package com.cityaed.web;

import com.cityaed.dto.AdminDeviceDto;
import com.cityaed.dto.AlarmDto;
import com.cityaed.dto.InspectionDto;
import com.cityaed.dto.RetrievalDto;
import com.cityaed.model.AlarmEvent;
import com.cityaed.model.Device;
import com.cityaed.model.Inspection;
import com.cityaed.model.RetrievalEvent;
import com.cityaed.service.DeviceStatusService;
import com.cityaed.service.MaintenanceService;
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

import java.time.LocalDate;
import java.util.List;

/**
 * 维护端接口：设备台账（含内部电话与耗材批次）、巡检录入、
 * 取用事件的回补登记与复检闭环、报警记录。
 */
@Path("/api/admin")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AdminResource {

    @Inject
    DeviceStatusService statusService;

    @Inject
    MaintenanceService maintenanceService;

    @GET
    @Path("/devices")
    public List<AdminDeviceDto> devices() {
        return Device.<Device>listAll().stream()
                .map(d -> AdminDeviceDto.of(d, statusService))
                .toList();
    }

    @GET
    @Path("/devices/{id}")
    public AdminDeviceDto device(@PathParam("id") long id) {
        Device d = Device.findById(id);
        if (d == null) {
            throw new NotFoundException("设备不存在");
        }
        return AdminDeviceDto.of(d, statusService);
    }

    @GET
    @Path("/devices/{id}/inspections")
    public List<InspectionDto> inspections(@PathParam("id") long id) {
        Device d = Device.findById(id);
        if (d == null) {
            throw new NotFoundException("设备不存在");
        }
        return Inspection.<Inspection>find("device = ?1 order by inspectedAt desc", d).list()
                .stream().map(InspectionDto::of).toList();
    }

    @GET
    @Path("/devices/{id}/retrievals")
    public List<RetrievalDto> deviceRetrievals(@PathParam("id") long id) {
        Device d = Device.findById(id);
        if (d == null) {
            throw new NotFoundException("设备不存在");
        }
        return RetrievalEvent.<RetrievalEvent>find("device = ?1 order by retrievedAt desc", d).list()
                .stream().map(RetrievalDto::of).toList();
    }

    // ---- 巡检录入 ----

    public record InspectionRequest(String inspectorName,
                                    boolean batteryOk, boolean padsOk, boolean cabinetOk,
                                    String batteryBatch, LocalDate batteryExpiry,
                                    String padsBatch, LocalDate padsExpiry,
                                    String notes) {
    }

    @POST
    @Path("/devices/{id}/inspections")
    @Transactional
    public Response inspect(@PathParam("id") long id, InspectionRequest req) {
        Device d = Device.findById(id);
        if (d == null) {
            throw new NotFoundException("设备不存在");
        }
        if (req == null || req.inspectorName() == null || req.inspectorName().isBlank()) {
            return badRequest("巡检员姓名必填");
        }
        maintenanceService.applyInspection(d, Inspection.Type.ROUTINE, req.inspectorName(),
                req.batteryOk(), req.padsOk(), req.cabinetOk(),
                req.batteryBatch(), req.batteryExpiry(),
                req.padsBatch(), req.padsExpiry(), req.notes());
        return Response.status(Response.Status.CREATED)
                .entity(AdminDeviceDto.of(d, statusService)).build();
    }

    // ---- 取用事件：回补与复检 ----

    @GET
    @Path("/retrievals")
    public List<RetrievalDto> retrievals(@QueryParam("open") Boolean open) {
        List<RetrievalEvent> events = Boolean.TRUE.equals(open)
                ? RetrievalEvent.find("closedAt is null order by retrievedAt desc").list()
                : RetrievalEvent.find("order by retrievedAt desc").list();
        return events.stream().map(RetrievalDto::of).toList();
    }

    public record RestockRequest(String operator,
                                 String batteryBatch, LocalDate batteryExpiry,
                                 String padsBatch, LocalDate padsExpiry) {
    }

    /** 回补登记：维护人员核对并记录实际补回耗材的批次与有效期 */
    @POST
    @Path("/retrievals/{id}/restock")
    @Transactional
    public Response restock(@PathParam("id") long id, RestockRequest req) {
        RetrievalEvent r = RetrievalEvent.findById(id);
        if (r == null) {
            throw new NotFoundException("取用事件不存在");
        }
        String error = maintenanceService.restock(r,
                req == null ? null : req.operator(),
                req == null ? null : req.batteryBatch(),
                req == null ? null : req.batteryExpiry(),
                req == null ? null : req.padsBatch(),
                req == null ? null : req.padsExpiry());
        if (error != null) {
            return badRequest(error);
        }
        return Response.ok(RetrievalDto.of(r)).build();
    }

    public record RecheckRequest(String inspectorName,
                                 boolean batteryOk, boolean padsOk, boolean cabinetOk,
                                 String notes) {
    }

    /** 复检：必须在回补之后；全部通过才闭环事件、恢复可用 */
    @POST
    @Path("/retrievals/{id}/recheck")
    @Transactional
    public Response recheck(@PathParam("id") long id, RecheckRequest req) {
        RetrievalEvent r = RetrievalEvent.findById(id);
        if (r == null) {
            throw new NotFoundException("取用事件不存在");
        }
        if (req == null || req.inspectorName() == null || req.inspectorName().isBlank()) {
            return badRequest("复检人姓名必填");
        }
        String error = maintenanceService.recheck(r, req.inspectorName(),
                req.batteryOk(), req.padsOk(), req.cabinetOk(), req.notes());
        if (error != null) {
            return badRequest(error);
        }
        return Response.ok(RetrievalDto.of(r)).build();
    }

    // ---- 报警记录（本地模拟） ----

    @GET
    @Path("/alarms")
    public List<AlarmDto> alarms() {
        return AlarmEvent.<AlarmEvent>find("order by triggeredAt desc").list()
                .stream().map(AlarmDto::of).toList();
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(new PublicResource.ErrorBody(message)).build();
    }
}
