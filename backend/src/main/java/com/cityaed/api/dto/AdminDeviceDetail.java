package com.cityaed.api.dto;

import java.util.List;

/** 维护端设备详情：设备信息 + 巡检历史 + 取用事件历史 */
public record AdminDeviceDetail(
        AdminAedDto device,
        List<InspectionDto> inspections,
        List<UsageEventDto> usageEvents) {
}
