package com.cityaed.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 紧急取用记录（报警事件）。一旦创建，设备立即进入“待回补”，
 * 只有回补登记 + 复检通过（状态推进到 CLOSED）后才可能恢复可用。
 * 该记录不可通过任何“刷新状态”操作清除。
 */
@Entity
@Table(name = "usage_event")
public class UsageEvent extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "device_id")
    public AedDevice device;

    @Column(nullable = false)
    public Instant reportedAt = Instant.now();

    /** 报案人/取用人（模拟报警，可为热心市民） */
    public String reporterName;

    @Column(length = 1000)
    public String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public UsageStatus status = UsageStatus.OPEN;

    public Instant closedAt;
}
