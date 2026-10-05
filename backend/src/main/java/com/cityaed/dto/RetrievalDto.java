package com.cityaed.dto;

import com.cityaed.model.RetrievalEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record RetrievalDto(
        long id,
        long deviceId,
        String deviceCode,
        String deviceName,
        LocalDateTime retrievedAt,
        String reporterName,
        String note,
        /** OPEN / RESTOCKED / CLOSED */
        String state,
        LocalDateTime restockedAt,
        String restockOperator,
        String restockBatteryBatch,
        LocalDate restockBatteryExpiry,
        String restockPadsBatch,
        LocalDate restockPadsExpiry,
        Long recheckInspectionId,
        LocalDateTime closedAt) {

    public static RetrievalDto of(RetrievalEvent r) {
        String state = r.closedAt != null ? "CLOSED" : (r.restockedAt != null ? "RESTOCKED" : "OPEN");
        return new RetrievalDto(
                r.id, r.device.id, r.device.code, r.device.name,
                r.retrievedAt, r.reporterName, r.note, state,
                r.restockedAt, r.restockOperator,
                r.restockBatteryBatch, r.restockBatteryExpiry,
                r.restockPadsBatch, r.restockPadsExpiry,
                r.recheckInspection == null ? null : r.recheckInspection.id,
                r.closedAt);
    }
}
