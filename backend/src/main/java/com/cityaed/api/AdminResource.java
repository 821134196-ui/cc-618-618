package com.cityaed.api;

import com.cityaed.api.dto.AdminAedDto;
import com.cityaed.api.dto.AdminDeviceDetail;
import com.cityaed.api.dto.CreateDeviceRequest;
import com.cityaed.api.dto.InspectionDto;
import com.cityaed.api.dto.InspectionRequest;
import com.cityaed.api.dto.RecheckRequest;
import com.cityaed.api.dto.ReplenishRequest;
import com.cityaed.api.dto.UsageEventDto;
import com.cityaed.model.AedDevice;
import com.cityaed.model.Inspection;
import com.cityaed.model.Replenishment;
import com.cityaed.model.UsageEvent;
import com.cityaed.model.UsageStatus;
import com.cityaed.service.DeviceStatusService;
import com.cityaed.service.StatusView;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * 维护端 API：巡检登记、取用事件处理（回补 + 复检）、设备管理。
 * 包含内部维修电话等内部字段，仅供维护人员使用。
 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    DeviceStatusService statusService;

    // ---------- 设备 ----------

    @GET
    @Path("/aeds")
    @Transactional
    public List<AdminAedDto> listDevices() {
        return AedDevice.<AedDevice>listAll().stream()
                .sorted(Comparator.comparing(d -> d.code))
                .map(this::toAdminDto)
                .toList();
    }

    @POST
    @Path("/aeds")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createDevice(CreateDeviceRequest req) {
        if (req == null || isBlank(req.code()) || isBlank(req.name())) {
            return ApiErrors.badRequest("设备编号与名称不能为空");
        }
        if (req.latitude() == null || req.longitude() == null
                || Math.abs(req.latitude()) > 90 || Math.abs(req.longitude()) > 180) {
            return ApiErrors.badRequest("经纬度缺失或超出范围");
        }
        String code = req.code().trim();
        if (AedDevice.count("code", code) > 0) {
            return ApiErrors.conflict("设备编号已存在：" + code);
        }
        AedDevice device = new AedDevice();
        device.code = code;
        device.name = req.name().trim();
        device.address = req.address();
        device.latitude = req.latitude();
        device.longitude = req.longitude();
        device.maintenancePhone = req.maintenancePhone();
        device.installedAt = LocalDate.now();
        device.persist();
        return Response.status(Response.Status.CREATED).entity(toAdminDto(device)).build();
    }

    @GET
    @Path("/aeds/{id}")
    @Transactional
    public AdminDeviceDetail deviceDetail(@PathParam("id") long id) {
        AedDevice device = findDevice(id);
        List<InspectionDto> inspections =
                Inspection.<Inspection>list("device = ?1 order by inspectedAt desc, id desc", device)
                        .stream().map(InspectionDto::from).toList();
        List<UsageEventDto> events =
                UsageEvent.<UsageEvent>list("device = ?1 order by reportedAt desc, id desc", device)
                        .stream().map(e -> UsageEventDto.from(e, replenishmentOf(e))).toList();
        return new AdminDeviceDetail(toAdminDto(device), inspections, events);
    }

    // ---------- 巡检登记 ----------

    /**
     * 例行巡检登记。注意：例行巡检不会清除未闭环的取用事件——
     * 取用后必须在事件流程内完成回补与复检，设备才可能恢复可用。
     */
    @POST
    @Path("/aeds/{id}/inspections")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response createInspection(@PathParam("id") long id, InspectionRequest req) {
        AedDevice device = findDevice(id);
        Response invalid = validateInspection(req.inspectorName(), req.batteryBatch(),
                req.batteryExpiry(), req.padsBatch(), req.padsExpiry());
        if (invalid != null) {
            return invalid;
        }
        Inspection inspection = new Inspection();
        inspection.device = device;
        inspection.inspectorName = req.inspectorName().trim();
        inspection.batteryOk = req.batteryOk();
        inspection.padsOk = req.padsOk();
        inspection.cabinetOk = req.cabinetOk();
        inspection.batteryBatch = req.batteryBatch().trim();
        inspection.batteryExpiry = req.batteryExpiry();
        inspection.padsBatch = req.padsBatch().trim();
        inspection.padsExpiry = req.padsExpiry();
        inspection.notes = req.notes();
        if (req.inspectedAt() != null) {
            inspection.inspectedAt = req.inspectedAt();
        }
        inspection.passed = computePassed(req.batteryOk(), req.padsOk(), req.cabinetOk(),
                req.batteryExpiry(), req.padsExpiry());
        inspection.persist();
        return Response.status(Response.Status.CREATED).entity(InspectionDto.from(inspection)).build();
    }

    // ---------- 取用事件：回补 + 复检 ----------

    @GET
    @Path("/usage-events")
    @Transactional
    public List<UsageEventDto> listUsageEvents(@QueryParam("status") UsageStatus status) {
        String query = status == null
                ? "order by reportedAt desc, id desc"
                : "status = ?1 order by reportedAt desc, id desc";
        List<UsageEvent> events = status == null
                ? UsageEvent.list(query)
                : UsageEvent.list(query, status);
        return events.stream().map(e -> UsageEventDto.from(e, replenishmentOf(e))).toList();
    }

    /**
     * 耗材回补登记：录入实际补回耗材的批次与有效期。
     * 仅 OPEN（待回补）状态可登记；登记后事件进入 REPLENISHED（待复检）。
     */
    @POST
    @Path("/usage-events/{id}/replenish")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response replenish(@PathParam("id") long id, ReplenishRequest req) {
        UsageEvent event = UsageEvent.findById(id);
        if (event == null) {
            throw new NotFoundException("取用事件不存在");
        }
        if (event.status != UsageStatus.OPEN) {
            return ApiErrors.conflict("仅“待回补”状态的取用事件可登记回补（当前：" + event.status + "）");
        }
        Response invalid = validateReplenishment(req);
        if (invalid != null) {
            return invalid;
        }
        Replenishment r = new Replenishment();
        r.usageEvent = event;
        r.device = event.device;
        r.staffName = req.staffName().trim();
        r.batteryBatch = req.batteryBatch().trim();
        r.batteryExpiry = req.batteryExpiry();
        r.padsBatch = req.padsBatch().trim();
        r.padsExpiry = req.padsExpiry();
        r.persist();

        event.status = UsageStatus.REPLENISHED;
        return Response.ok(UsageEventDto.from(event, r)).build();
    }

    /**
     * 复检：逐项复查并核对实际耗材批次（须与回补登记一致）。
     * 复检通过后事件闭环（CLOSED），设备状态重新派生，满足条件即恢复可用；
     * 复检未通过则事件保持 REPLENISHED，整改后可再次复检。
     */
    @POST
    @Path("/usage-events/{id}/recheck")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response recheck(@PathParam("id") long id, RecheckRequest req) {
        UsageEvent event = UsageEvent.findById(id);
        if (event == null) {
            throw new NotFoundException("取用事件不存在");
        }
        if (event.status != UsageStatus.REPLENISHED) {
            return ApiErrors.conflict("仅“已回补待复检”状态的取用事件可复检（当前：" + event.status + "）");
        }
        Replenishment replenishment = replenishmentOf(event);
        if (replenishment == null) {
            return ApiErrors.conflict("缺少回补记录，无法复检");
        }
        Response invalid = validateInspection(req.inspectorName(), req.batteryBatch(),
                req.batteryExpiry(), req.padsBatch(), req.padsExpiry());
        if (invalid != null) {
            return invalid;
        }
        // 核对补回耗材的实际批次：复检登记批次必须与回补登记批次一致
        if (!replenishment.batteryBatch.equalsIgnoreCase(req.batteryBatch().trim())
                || !replenishment.padsBatch.equalsIgnoreCase(req.padsBatch().trim())) {
            return ApiErrors.badRequest("复检批次与回补登记批次不一致，请核对实际耗材批次（回补登记：电池 "
                    + replenishment.batteryBatch + " / 电极片 " + replenishment.padsBatch + "）");
        }

        Inspection inspection = new Inspection();
        inspection.device = event.device;
        inspection.usageEvent = event;
        inspection.inspectorName = req.inspectorName().trim();
        inspection.batteryOk = req.batteryOk();
        inspection.padsOk = req.padsOk();
        inspection.cabinetOk = req.cabinetOk();
        inspection.batteryBatch = req.batteryBatch().trim();
        inspection.batteryExpiry = req.batteryExpiry();
        inspection.padsBatch = req.padsBatch().trim();
        inspection.padsExpiry = req.padsExpiry();
        inspection.notes = req.notes();
        inspection.passed = computePassed(req.batteryOk(), req.padsOk(), req.cabinetOk(),
                req.batteryExpiry(), req.padsExpiry());
        inspection.persist();

        if (inspection.passed) {
            event.status = UsageStatus.CLOSED;
            event.closedAt = Instant.now();
        }
        return Response.ok(UsageEventDto.from(event, replenishment)).build();
    }

    // ---------- 内部辅助 ----------

    private AedDevice findDevice(long id) {
        AedDevice device = AedDevice.findById(id);
        if (device == null) {
            throw new NotFoundException("设备不存在");
        }
        return device;
    }

    private AdminAedDto toAdminDto(AedDevice device) {
        StatusView view = statusService.compute(device);
        Inspection latest = DeviceStatusService.latestInspection(device);
        UsageEvent openEvent = UsageEvent
                .find("device = ?1 and status <> ?2 order by reportedAt desc", device, UsageStatus.CLOSED)
                .firstResult();
        return AdminAedDto.from(device, view, latest, openEvent);
    }

    private static Replenishment replenishmentOf(UsageEvent event) {
        return Replenishment.find("usageEvent", event).firstResult();
    }

    /** 巡检/复检综合结论：三项检查全部通过且耗材均未过期 */
    static boolean computePassed(boolean batteryOk, boolean padsOk, boolean cabinetOk,
                                 LocalDate batteryExpiry, LocalDate padsExpiry) {
        LocalDate today = LocalDate.now();
        return batteryOk && padsOk && cabinetOk
                && batteryExpiry != null && !batteryExpiry.isBefore(today)
                && padsExpiry != null && !padsExpiry.isBefore(today);
    }

    private static Response validateInspection(String inspectorName, String batteryBatch,
                                               LocalDate batteryExpiry, String padsBatch,
                                               LocalDate padsExpiry) {
        if (isBlank(inspectorName)) {
            return ApiErrors.badRequest("巡检人姓名不能为空");
        }
        if (isBlank(batteryBatch) || isBlank(padsBatch)) {
            return ApiErrors.badRequest("电池与电极片批次不能为空");
        }
        if (batteryExpiry == null || padsExpiry == null) {
            return ApiErrors.badRequest("电池与电极片有效期不能为空");
        }
        return null;
    }

    private static Response validateReplenishment(ReplenishRequest req) {
        if (req == null || isBlank(req.staffName())) {
            return ApiErrors.badRequest("回补经办人不能为空");
        }
        if (isBlank(req.batteryBatch()) || isBlank(req.padsBatch())) {
            return ApiErrors.badRequest("补回耗材的实际批次不能为空");
        }
        if (req.batteryExpiry() == null || req.padsExpiry() == null) {
            return ApiErrors.badRequest("补回耗材的有效期不能为空");
        }
        LocalDate today = LocalDate.now();
        if (req.batteryExpiry().isBefore(today) || req.padsExpiry().isBefore(today)) {
            return ApiErrors.badRequest("补回耗材已过期，不能用于回补");
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
