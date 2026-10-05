package com.cityaed.dto;

import com.cityaed.model.Device;
import com.cityaed.model.RetrievalEvent;
import com.cityaed.service.DeviceStatusService;

import java.time.LocalDate;

/**
 * 维护端视图 DTO —— 包含内部维修电话与耗材批次，供维护人员核对。
 */
public record AdminDeviceDto(
        long id,
        String code,
        String name,
        String address,
        double latitude,
        double longitude,
        String status,
        String reason,
        String maintainerPhone,
        String batteryBatch,
        LocalDate batteryExpiry,
        String padsBatch,
        LocalDate padsExpiry,
        boolean cabinetFault,
        boolean consumableFault,
        LocalDate lastInspectionDate,
        LocalDate nextInspectionDue,
        Long openRetrievalId) {

    public static AdminDeviceDto of(Device d, DeviceStatusService statusService) {
        DeviceStatusService.EffectiveStatus s = statusService.of(d);
        RetrievalEvent open = RetrievalEvent.find("device = ?1 and closedAt is null", d).firstResult();
        return new AdminDeviceDto(
                d.id, d.code, d.name, d.address, d.latitude, d.longitude,
                s.status().name(), s.reason(),
                d.maintainerPhone,
                d.batteryBatch, d.batteryExpiry, d.padsBatch, d.padsExpiry,
                d.cabinetFault, d.consumableFault,
                d.lastInspectionDate, d.nextInspectionDue,
                open == null ? null : open.id);
    }
}
