package com.cityaed.api.dto;

import com.cityaed.model.AedDevice;
import com.cityaed.model.DeviceStatus;
import com.cityaed.model.Inspection;
import com.cityaed.model.UsageEvent;
import com.cityaed.service.StatusView;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 维护端视图：包含内部维修电话、未闭环取用事件 id 等内部信息。
 */
public record AdminAedDto(
        long id,
        String code,
        String name,
        String address,
        double latitude,
        double longitude,
        String maintenancePhone,
        DeviceStatus status,
        String statusReason,
        LocalDate batteryExpiry,
        LocalDate padsExpiry,
        Instant lastInspectedAt,
        Long openUsageEventId) {

    public static AdminAedDto from(AedDevice d, StatusView view, Inspection latest, UsageEvent openEvent) {
        return new AdminAedDto(
                d.id, d.code, d.name, d.address, d.latitude, d.longitude,
                d.maintenancePhone,
                view.status(), view.reason(),
                latest != null ? latest.batteryExpiry : null,
                latest != null ? latest.padsExpiry : null,
                latest != null ? latest.inspectedAt : null,
                openEvent != null ? openEvent.id : null);
    }
}
