package com.cityaed.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * AED 设备台账。设备的"对外状态"不落库，而是由
 * {@code DeviceStatusService} 根据取用记录、巡检记录、耗材有效期实时推导，
 * 因此任何"刷新状态"的操作都无法抹掉取用或故障事实。
 */
@Entity
public class Device extends PanacheEntity {

    /** 设备编号，如 AED-001 */
    @Column(unique = true, nullable = false)
    public String code;

    public String name;
    public String address;

    /** 本地模拟定位坐标 */
    public double latitude;
    public double longitude;

    /** 内部维修电话 —— 仅维护端可见，公众 DTO 绝不包含此字段 */
    public String maintainerPhone;

    // ---- 当前耗材（由巡检 / 回补登记更新） ----
    public String batteryBatch;
    public LocalDate batteryExpiry;
    public String padsBatch;
    public LocalDate padsExpiry;

    // ---- 巡检状态 ----
    public LocalDate lastInspectionDate;
    /** 下次应巡检日期，超过即"巡检过期" */
    public LocalDate nextInspectionDue;

    /** 箱体故障（巡检 cabinetOk=false 时置位，复检通过后清除） */
    public boolean cabinetFault;

    /** 电池/电极片未通过巡检 */
    public boolean consumableFault;

    public LocalDateTime createdAt;
}
