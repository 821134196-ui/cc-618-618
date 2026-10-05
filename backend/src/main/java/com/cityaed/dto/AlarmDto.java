package com.cityaed.dto;

import com.cityaed.model.AlarmEvent;

import java.time.LocalDateTime;

public record AlarmDto(
        long id,
        long deviceId,
        String deviceCode,
        String deviceName,
        LocalDateTime triggeredAt,
        String type,
        String message) {

    public static AlarmDto of(AlarmEvent a) {
        return new AlarmDto(a.id, a.device.id, a.device.code, a.device.name,
                a.triggeredAt, a.type, a.message);
    }
}
