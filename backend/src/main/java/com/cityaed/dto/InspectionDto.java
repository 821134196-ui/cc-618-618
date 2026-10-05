package com.cityaed.dto;

import com.cityaed.model.Inspection;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record InspectionDto(
        long id,
        String type,
        String inspectorName,
        LocalDateTime inspectedAt,
        boolean batteryOk,
        boolean padsOk,
        boolean cabinetOk,
        boolean passed,
        String batteryBatch,
        LocalDate batteryExpiry,
        String padsBatch,
        LocalDate padsExpiry,
        String notes) {

    public static InspectionDto of(Inspection i) {
        return new InspectionDto(
                i.id, i.type.name(), i.inspectorName, i.inspectedAt,
                i.batteryOk, i.padsOk, i.cabinetOk, i.passed,
                i.batteryBatch, i.batteryExpiry, i.padsBatch, i.padsExpiry, i.notes);
    }
}
