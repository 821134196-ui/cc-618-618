package com.cityaed.api;

import com.cityaed.api.dto.PublicAedDto;
import com.cityaed.api.dto.UsageEventDto;
import com.cityaed.api.dto.UseRequest;
import com.cityaed.model.AedDevice;
import com.cityaed.model.DeviceStatus;
import com.cityaed.model.Inspection;
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

import java.util.Comparator;
import java.util.List;

/**
 * 公众查询 API。地图与文字列表均使用本接口的同一份数据，保证两处状态一致。
 * 输出 DTO 不含内部维修电话等内部字段。
 */
@Path("/api/public/aeds")
@Produces(MediaType.APPLICATION_JSON)
public class PublicAedResource {

    @Inject
    DeviceStatusService statusService;

    /**
     * 设备列表（地图与文字列表共用的唯一数据源）。
     * query 仅做过滤，不影响任何设备的状态计算。
     */
    @GET
    @Transactional
    public List<PublicAedDto> list(@QueryParam("query") String query,
                                   @QueryParam("status") DeviceStatus status) {
        String q = query == null ? "" : query.trim().toLowerCase();
        return AedDevice.<AedDevice>listAll().stream()
                .filter(d -> q.isEmpty()
                        || d.code.toLowerCase().contains(q)
                        || d.name.toLowerCase().contains(q)
                        || (d.address != null && d.address.toLowerCase().contains(q)))
                .map(d -> toDto(d))
                .filter(dto -> status == null || dto.status() == status)
                .sorted(Comparator.comparing(PublicAedDto::code))
                .toList();
    }

    @GET
    @Path("/{id}")
    @Transactional
    public PublicAedDto get(@PathParam("id") long id) {
        AedDevice device = AedDevice.findById(id);
        if (device == null) {
            throw new NotFoundException("设备不存在");
        }
        return toDto(device);
    }

    /**
     * 紧急取用（本地模拟报警）。设备立即转为“待回补”，
     * 必须完成回补登记且复检通过后才恢复可用。
     */
    @POST
    @Path("/{id}/use")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response reportUse(@PathParam("id") long id, UseRequest request) {
        AedDevice device = AedDevice.findById(id);
        if (device == null) {
            throw new NotFoundException("设备不存在");
        }
        long openCount = UsageEvent.count("device = ?1 and status <> ?2", device, UsageStatus.CLOSED);
        if (openCount > 0) {
            return ApiErrors.conflict("该设备已有未完成的取用事件，正在等待回补/复检");
        }
        UsageEvent event = new UsageEvent();
        event.device = device;
        event.reporterName = request != null && request.reporterName() != null
                && !request.reporterName().isBlank()
                ? request.reporterName().trim() : "热心市民";
        event.note = request != null ? request.note() : null;
        event.persist();
        return Response.status(Response.Status.CREATED)
                .entity(UsageEventDto.from(event, null))
                .build();
    }

    private PublicAedDto toDto(AedDevice device) {
        StatusView view = statusService.compute(device);
        Inspection latest = DeviceStatusService.latestInspection(device);
        return PublicAedDto.from(device, view, latest);
    }
}
