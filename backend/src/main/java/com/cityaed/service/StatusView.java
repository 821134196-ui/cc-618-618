package com.cityaed.service;

import com.cityaed.model.DeviceStatus;

/** 设备状态视图：状态 + 面向公众的具体原因说明 */
public record StatusView(DeviceStatus status, String reason) {
}
