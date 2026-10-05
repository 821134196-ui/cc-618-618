package com.cityaed.api.dto;

import java.time.LocalDate;

/**
 * 复检请求：逐项复查并登记实际耗材批次。
 * 服务端会校验批次与回补登记是否一致，不一致则拒绝复检。
 */
public record RecheckRequest(
        String inspectorName,
        boolean batteryOk,
        boolean padsOk,
        boolean cabinetOk,
        String batteryBatch,
        LocalDate batteryExpiry,
        String padsBatch,
        LocalDate padsExpiry,
        String notes) {
}
