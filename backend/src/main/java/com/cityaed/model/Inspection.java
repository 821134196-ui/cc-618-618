package com.cityaed.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 巡检记录：逐项检查电池、电极片、箱体，并记录实物耗材批次。
 * 复检（RECHECK）也是一条巡检记录，关联到取用事件的闭环。
 */
@Entity
public class Inspection extends PanacheEntity {

    public enum Type { ROUTINE, RECHECK }

    @ManyToOne(optional = false)
    public Device device;

    @Enumerated(EnumType.STRING)
    public Type type;

    public String inspectorName;
    public LocalDateTime inspectedAt;

    // ---- 逐项检查结果 ----
    public boolean batteryOk;
    public boolean padsOk;
    public boolean cabinetOk;

    // ---- 现场实物耗材批次 ----
    public String batteryBatch;
    public LocalDate batteryExpiry;
    public String padsBatch;
    public LocalDate padsExpiry;

    public boolean passed;
    public String notes;
}
