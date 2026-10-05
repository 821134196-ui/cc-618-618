package com.cityaed.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;

import java.time.LocalDateTime;

/**
 * 报警记录（本地模拟）：紧急取用触发，维护端可查看。
 */
@Entity
public class AlarmEvent extends PanacheEntity {

    @ManyToOne(optional = false)
    public Device device;

    public LocalDateTime triggeredAt;

    /** RETRIEVAL = 紧急取用报警 */
    public String type;

    public String message;
}
