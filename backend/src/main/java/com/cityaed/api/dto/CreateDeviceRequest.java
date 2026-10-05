package com.cityaed.api.dto;

/** 新建设备请求 */
public record CreateDeviceRequest(
        String code,
        String name,
        String address,
        Double latitude,
        Double longitude,
        String maintenancePhone) {
}
