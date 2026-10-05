package com.cityaed.api.dto;

import com.cityaed.model.Replenishment;
import com.cityaed.model.UsageEvent;
import com.cityaed.model.UsageStatus;

import java.time.Instant;

/** 取用事件视图（含回补记录，供维护端核对批次） */
public record UsageEventDto(
        long id,
        long deviceId,
        String deviceCode,
        String deviceName,
        UsageStatus status,
        Instant reportedAt,
        String reporterName,
        String note,
        Instant closedAt,
        ReplenishmentDto replenishment) {

    public static UsageEventDto from(UsageEvent e, Replenishment r) {
        return new UsageEventDto(
                e.id, e.device.id, e.device.code, e.device.name,
                e.status, e.reportedAt, e.reporterName, e.note, e.closedAt,
                r != null ? ReplenishmentDto.from(r) : null);
    }
}
