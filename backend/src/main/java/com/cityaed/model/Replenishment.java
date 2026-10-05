package com.cityaed.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 耗材回补登记：维护人员补齐耗材时录入实际批次与有效期，
 * 复检时系统会比对批次是否一致，供维护人员核对。
 */
@Entity
@Table(name = "replenishment")
public class Replenishment extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usage_event_id", unique = true)
    public UsageEvent usageEvent;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "device_id")
    public AedDevice device;

    @Column(nullable = false)
    public Instant replenishedAt = Instant.now();

    /** 回补经办人 */
    public String staffName;

    /** 实际补回的电池批次 */
    public String batteryBatch;

    public LocalDate batteryExpiry;

    /** 实际补回的电极片批次 */
    public String padsBatch;

    public LocalDate padsExpiry;
}
