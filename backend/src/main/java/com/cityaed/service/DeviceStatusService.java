package com.cityaed.service;

import com.cityaed.model.Device;
import com.cityaed.model.RetrievalEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备对外状态的唯一推导入口。状态不存储、不缓存，每次由事实记录实时计算：
 * <ol>
 *   <li>存在未闭环的取用事件 → 待回补（PENDING_RESTOCK）</li>
 *   <li>箱体故障 → 停用</li>
 *   <li>电池/电极片未通过巡检 → 停用</li>
 *   <li>耗材过期 → 停用</li>
 *   <li>巡检过期 → 停用</li>
 *   <li>否则 → 可用</li>
 * </ol>
 * 地图与列表共用此推导，保证两处状态一致；因为没有"重置状态"的入口，
 * 刷新页面不可能清除取用记录造成的状态。
 */
@ApplicationScoped
public class DeviceStatusService {

    public enum Status { AVAILABLE, PENDING_RESTOCK, OUT_OF_SERVICE }

    public record EffectiveStatus(Status status, String reason) {
        public boolean available() {
            return status == Status.AVAILABLE;
        }
    }

    public EffectiveStatus of(Device d) {
        return of(d, LocalDate.now());
    }

    public EffectiveStatus of(Device d, LocalDate today) {
        long openRetrievals = RetrievalEvent.count("device = ?1 and closedAt is null", d);
        if (openRetrievals > 0) {
            return new EffectiveStatus(Status.PENDING_RESTOCK, "紧急取用待回补复检");
        }
        if (d.cabinetFault) {
            return new EffectiveStatus(Status.OUT_OF_SERVICE, "箱体故障");
        }
        if (d.consumableFault) {
            return new EffectiveStatus(Status.OUT_OF_SERVICE, "电池/电极片未通过巡检");
        }
        List<String> expired = new ArrayList<>();
        if (d.batteryExpiry == null || d.batteryExpiry.isBefore(today)) {
            expired.add("电池");
        }
        if (d.padsExpiry == null || d.padsExpiry.isBefore(today)) {
            expired.add("电极片");
        }
        if (!expired.isEmpty()) {
            return new EffectiveStatus(Status.OUT_OF_SERVICE, "耗材过期（" + String.join("、", expired) + "）");
        }
        if (d.nextInspectionDue == null || d.nextInspectionDue.isBefore(today)) {
            return new EffectiveStatus(Status.OUT_OF_SERVICE, "巡检过期");
        }
        return new EffectiveStatus(Status.AVAILABLE, null);
    }
}
