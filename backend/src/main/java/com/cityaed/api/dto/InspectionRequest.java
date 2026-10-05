package com.cityaed.api.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 巡检登记请求：逐项检查电池、电极片、箱体，并登记实物耗材批次与有效期。
 * inspectedAt 可选（用于补录历史巡检），缺省为当前时间。
 */
public record InspectionRequest(
        String inspectorName,
        boolean batteryOk,
        boolean padsOk,
        boolean cabinetOk,
        String batteryBatch,
        LocalDate batteryExpiry,
        String padsBatch,
        LocalDate padsExpiry,
        String notes,
        Instant inspectedAt) {
}
