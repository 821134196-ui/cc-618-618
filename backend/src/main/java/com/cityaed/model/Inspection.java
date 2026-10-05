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
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 巡检记录：逐项检查电池、电极片、箱体，并登记实物耗材批次与有效期。
 * 作为取用事件的“复检”时，通过 usageEvent 关联对应事件。
 */
@Entity
@Table(name = "inspection")
public class Inspection extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "device_id")
    public AedDevice device;

    @Column(nullable = false)
    public Instant inspectedAt = Instant.now();

    public String inspectorName;

    /** 电池项检查是否通过 */
    public boolean batteryOk;

    /** 电极片项检查是否通过 */
    public boolean padsOk;

    /** 箱体项检查是否通过 */
    public boolean cabinetOk;

    /** 实物电池批次 */
    public String batteryBatch;

    public LocalDate batteryExpiry;

    /** 实物电极片批次 */
    public String padsBatch;

    public LocalDate padsExpiry;

    /** 本次巡检综合结论（提交时由服务端计算） */
    public boolean passed;

    @Column(length = 1000)
    public String notes;

    /** 若本次巡检是某取用事件的复检，关联该事件；例行巡检为 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usage_event_id")
    public UsageEvent usageEvent;
}
