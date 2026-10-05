package com.cityaed.api.dto;

import java.time.LocalDate;

/** 耗材回补登记请求：录入实际补回耗材的批次与有效期 */
public record ReplenishRequest(
        String staffName,
        String batteryBatch,
        LocalDate batteryExpiry,
        String padsBatch,
        LocalDate padsExpiry) {
}
