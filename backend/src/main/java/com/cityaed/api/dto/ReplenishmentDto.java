package com.cityaed.api.dto;

import com.cityaed.model.Replenishment;

import java.time.Instant;
import java.time.LocalDate;

/** 回补登记视图：维护人员可核对实际补回耗材的批次与有效期 */
public record ReplenishmentDto(
        String staffName,
        String batteryBatch,
        LocalDate batteryExpiry,
        String padsBatch,
        LocalDate padsExpiry,
        Instant replenishedAt) {

    public static ReplenishmentDto from(Replenishment r) {
        return new ReplenishmentDto(
                r.staffName, r.batteryBatch, r.batteryExpiry,
                r.padsBatch, r.padsExpiry, r.replenishedAt);
    }
}
