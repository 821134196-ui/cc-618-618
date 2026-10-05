package com.cityaed.model;

/**
 * 设备对外状态（由底层记录派生，不可直接修改）：
 * AVAILABLE              可用
 * PENDING_REPLENISHMENT  紧急取用后待回补/待复检
 * OUT_OF_SERVICE         停用（巡检过期 / 耗材过期 / 箱体故障等，附具体原因）
 */
public enum DeviceStatus {
    AVAILABLE,
    PENDING_REPLENISHMENT,
    OUT_OF_SERVICE
}
