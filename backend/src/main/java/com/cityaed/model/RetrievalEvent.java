package com.cityaed.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 紧急取用事件。生命周期：OPEN（已取用，待回补）→ RESTOCKED（已回补，待复检）→ CLOSED（复检通过）。
 * 记录永不删除；只要 closedAt 为空，设备就处于"待回补"状态。
 */
@Entity
public class RetrievalEvent extends PanacheEntity {

    @ManyToOne(optional = false)
    public Device device;

    public LocalDateTime retrievedAt;
    public String reporterName;
    public String note;

    // ---- 回补登记（维护人员核对并记录实际补回耗材批次） ----
    public LocalDateTime restockedAt;
    public String restockOperator;
    public String restockBatteryBatch;
    public LocalDate restockBatteryExpiry;
    public String restockPadsBatch;
    public LocalDate restockPadsExpiry;

    // ---- 复检（通过后关闭事件） ----
    @OneToOne
    public Inspection recheckInspection;

    public LocalDateTime closedAt;

    public boolean isOpen() {
        return closedAt == null;
    }

    public boolean isRestocked() {
        return restockedAt != null;
    }
}
