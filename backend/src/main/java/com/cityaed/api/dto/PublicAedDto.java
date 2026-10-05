package com.cityaed.api.dto;

import com.cityaed.model.AedDevice;
import com.cityaed.model.DeviceStatus;
import com.cityaed.model.Inspection;
import com.cityaed.service.StatusView;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 公众视图：绝不包含内部维修电话等内部字段。
 */
public record PublicAedDto(
        long id,
        String code,
        String name,
        String address,
        double latitude,
        double longitude,
        DeviceStatus status,
        String statusReason,
        LocalDate batteryExpiry,
        LocalDate padsExpiry,
        Instant lastInspectedAt) {

    public static PublicAedDto from(AedDevice d, StatusView view, Inspection latest) {
        return new PublicAedDto(
                d.id, d.code, d.name, d.address, d.latitude, d.longitude,
                view.status(), view.reason(),
                latest != null ? latest.batteryExpiry : null,
                latest != null ? latest.padsExpiry : null,
                latest != null ? latest.inspectedAt : null);
    }
}
