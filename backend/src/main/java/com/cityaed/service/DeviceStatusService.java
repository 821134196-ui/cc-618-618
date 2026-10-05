package com.cityaed.service;

import com.cityaed.model.AedDevice;
import com.cityaed.model.DeviceStatus;
import com.cityaed.model.Inspection;
import com.cityaed.model.UsageEvent;
import com.cityaed.model.UsageStatus;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备状态派生服务。状态永远由底层记录（取用事件、最近一次巡检、耗材有效期）
 * 实时计算，系统不提供任何“直接置为可用/刷新状态”的入口，
 * 因此取用记录无法通过刷新清除。
 *
 * 判定顺序：
 *  1. 存在未闭环取用事件 -> PENDING_REPLENISHMENT（待回补 / 待复检）
 *  2. 无巡检记录         -> OUT_OF_SERVICE（未巡检）
 *  3. 巡检超过有效期     -> OUT_OF_SERVICE（巡检过期）
 *  4. 箱体检查未通过     -> OUT_OF_SERVICE（箱体故障）
 *  5. 电池/电极片过期    -> OUT_OF_SERVICE（耗材过期：…）
 *  6. 电池/电极片未通过  -> OUT_OF_SERVICE（…巡检未通过）
 *  7. 否则               -> AVAILABLE
 */
@ApplicationScoped
public class DeviceStatusService {

    @ConfigProperty(name = "app.inspection-valid-days", defaultValue = "90")
    int inspectionValidDays;

    /** 查询设备当前状态（含具体原因） */
    public StatusView compute(AedDevice device) {
        List<UsageEvent> openEvents =
                UsageEvent.list("device = ?1 and status <> ?2", device, UsageStatus.CLOSED);
        Inspection latest = latestInspection(device);
        return compute(device, openEvents, latest, LocalDate.now());
    }

    public static Inspection latestInspection(AedDevice device) {
        return Inspection.find("device = ?1 order by inspectedAt desc, id desc", device).firstResult();
    }

    StatusView compute(AedDevice device, List<UsageEvent> openEvents, Inspection latest, LocalDate today) {
        // 1. 未闭环的取用事件优先：取用后必须完成回补+复检
        for (UsageEvent e : openEvents) {
            if (e.status == UsageStatus.OPEN) {
                return new StatusView(DeviceStatus.PENDING_REPLENISHMENT, "紧急取用待回补");
            }
            if (e.status == UsageStatus.REPLENISHED) {
                return new StatusView(DeviceStatus.PENDING_REPLENISHMENT, "耗材已回补，待复检");
            }
        }

        // 2-6. 停用原因（可能多项并存，全部列出）
        List<String> reasons = new ArrayList<>();
        if (latest == null) {
            reasons.add("未巡检");
        } else {
            LocalDate inspectedDate = latest.inspectedAt.atZone(ZoneId.systemDefault()).toLocalDate();
            if (inspectedDate.plusDays(inspectionValidDays).isBefore(today)) {
                reasons.add("巡检过期");
            }
            if (!latest.cabinetOk) {
                reasons.add("箱体故障");
            }
            if (isExpired(latest.batteryExpiry, today)) {
                reasons.add("耗材过期：电池");
            }
            if (isExpired(latest.padsExpiry, today)) {
                reasons.add("耗材过期：电极片");
            }
            if (!latest.batteryOk) {
                reasons.add("电池巡检未通过");
            }
            if (!latest.padsOk) {
                reasons.add("电极片巡检未通过");
            }
        }

        if (!reasons.isEmpty()) {
            return new StatusView(DeviceStatus.OUT_OF_SERVICE, String.join("；", reasons));
        }
        return new StatusView(DeviceStatus.AVAILABLE, "正常");
    }

    /** 有效期早于今天即视为过期（到期当日仍可用） */
    private static boolean isExpired(LocalDate expiry, LocalDate today) {
        return expiry != null && expiry.isBefore(today);
    }
}
