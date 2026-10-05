package com.cityaed.dto;

import com.cityaed.model.Device;
import com.cityaed.service.DeviceStatusService;

import java.time.LocalDate;

/**
 * 公众视图 DTO —— 故意不包含 maintainerPhone（内部维修电话）与耗材批次等内部信息。
 * 地图与可搜索列表都渲染这份数据，因此两处状态必然一致。
 */
public record PublicDeviceDto(
        long id,
        String code,
        String name,
        String address,
        double latitude,
        double longitude,
        String status,
        String reason,
        LocalDate lastInspectionDate,
        LocalDate nextInspectionDue) {

    public static PublicDeviceDto of(Device d, DeviceStatusService statusService) {
        DeviceStatusService.EffectiveStatus s = statusService.of(d);
        return new PublicDeviceDto(
                d.id, d.code, d.name, d.address, d.latitude, d.longitude,
                s.status().name(), s.reason(),
                d.lastInspectionDate, d.nextInspectionDue);
    }
}
