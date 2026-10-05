package com.cityaed.model;

/**
 * 取用事件状态机：OPEN(待回补) -> REPLENISHED(已回补待复检) -> CLOSED(复检通过，闭环)。
 * 只有 CLOSED 之后设备才可能恢复“可用”。
 */
public enum UsageStatus {
    OPEN,
    REPLENISHED,
    CLOSED
}
