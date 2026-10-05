package com.cityaed.api.dto;

import com.cityaed.model.Inspection;

import java.time.Instant;
import java.time.LocalDate;

/** 巡检记录视图 */
public record InspectionDto(
        long id,
        Instant inspectedAt,
        String inspectorName,
        boolean batteryOk,
        boolean padsOk,
        boolean cabinetOk,
        String batteryBatch,
        LocalDate batteryExpiry,
        String padsBatch,
        LocalDate padsExpiry,
        boolean passed,
        String notes,
        Long usageEventId) {

    public static InspectionDto from(Inspection i) {
        return new InspectionDto(
                i.id, i.inspectedAt, i.inspectorName,
                i.batteryOk, i.padsOk, i.cabinetOk,
                i.batteryBatch, i.batteryExpiry, i.padsBatch, i.padsExpiry,
                i.passed, i.notes,
                i.usageEvent != null ? i.usageEvent.id : null);
    }
}
