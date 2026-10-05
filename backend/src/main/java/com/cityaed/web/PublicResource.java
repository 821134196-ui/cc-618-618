package com.cityaed.web;

import com.cityaed.dto.PublicDeviceDto;
import com.cityaed.model.AlarmEvent;
import com.cityaed.model.Device;
import com.cityaed.model.RetrievalEvent;
import com.cityaed.service.DeviceStatusService;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公众接口：查询设备（地图与列表共用同一份数据）、紧急取用上报。
 * 返回的 DTO 不含内部维修电话等敏感信息。
 */
@Path("/api/public")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublicResource {

    @Inject
    DeviceStatusService statusService;

    /** 地图视图与列表视图都从这里取数，保证两处状态一致 */
    @GET
    @Path("/devices")
    public List<PublicDeviceDto> devices() {
        return Device.<Device>listAll().stream()
                .map(d -> PublicDeviceDto.of(d, statusService))
                .toList();
    }

    @GET
    @Path("/devices/{id}")
    public PublicDeviceDto device(@PathParam("id") long id) {
        Device d = Device.findById(id);
        if (d == null) {
            throw new NotFoundException("设备不存在");
        }
        return PublicDeviceDto.of(d, statusService);
    }

    public record RetrieveRequest(String reporterName, String note) {
    }

    /**
     * 紧急取用：立即生成取用事件（不可清除）并触发本地模拟报警，
     * 设备随即转为"待回补"，直到回补登记且复检通过。
     */
    @POST
    @Path("/devices/{id}/retrieve")
    @Transactional
    public Response retrieve(@PathParam("id") long id, RetrieveRequest req) {
        Device d = Device.findById(id);
        if (d == null) {
            throw new NotFoundException("设备不存在");
        }
        long open = RetrievalEvent.count("device = ?1 and closedAt is null", d);
        if (open > 0) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ErrorBody("该设备已有未闭环的取用事件")).build();
        }
        RetrievalEvent r = new RetrievalEvent();
        r.device = d;
        r.retrievedAt = LocalDateTime.now();
        r.reporterName = req == null ? null : req.reporterName();
        r.note = req == null ? null : req.note();
        r.persist();

        // 本地模拟报警：记录报警事件（真实场景会推送短信/平台告警）
        AlarmEvent alarm = new AlarmEvent();
        alarm.device = d;
        alarm.triggeredAt = LocalDateTime.now();
        alarm.type = "RETRIEVAL";
        alarm.message = "设备 " + d.code + "（" + d.name + "）发生紧急取用，请安排回补与复检";
        alarm.persist();

        return Response.status(Response.Status.CREATED)
                .entity(PublicDeviceDto.of(d, statusService)).build();
    }

    public record ErrorBody(String message) {
    }
}
