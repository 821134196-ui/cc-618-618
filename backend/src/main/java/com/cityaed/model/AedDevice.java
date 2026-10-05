package com.cityaed.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * AED 设备台账。设备当前状态不存字段，统一由 DeviceStatusService 依据
 * 巡检记录 / 取用事件 / 耗材有效期实时派生，避免“刷新状态”绕过业务流程。
 *
 * 主键使用 IDENTITY（SQLite 自增）：表式 ID 生成器需要挂起当前事务另开
 * 新事务分配 ID，会与 SQLite 单写锁冲突（SQLITE_BUSY）。
 */
@Entity
@Table(name = "aed_device")
public class AedDevice extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    /** 设备编号，如 AED-0001 */
    @Column(nullable = false, unique = true)
    public String code;

    @Column(nullable = false)
    public String name;

    public String address;

    public double latitude;

    public double longitude;

    /** 内部维修电话 —— 仅维护端 API 输出，公众 API 绝不返回 */
    public String maintenancePhone;

    public LocalDate installedAt;

    @Column(nullable = false)
    public Instant createdAt = Instant.now();
}
