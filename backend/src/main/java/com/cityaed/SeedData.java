package com.cityaed;

import com.cityaed.model.AedDevice;
import com.cityaed.model.Inspection;
import com.cityaed.model.Replenishment;
import com.cityaed.model.UsageEvent;
import com.cityaed.model.UsageStatus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 演示数据：数据库为空时写入 8 台设备，覆盖
 * 可用 / 待回补 / 待复检 / 巡检过期 / 耗材过期 / 箱体故障 / 未巡检 等状态。
 */
@ApplicationScoped
public class SeedData {

    @ConfigProperty(name = "app.seed.enabled", defaultValue = "true")
    boolean seedEnabled;

    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (!seedEnabled || AedDevice.count() > 0) {
            return;
        }
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();

        AedDevice d1 = device("AED-0001", "市民中心地铁站A口", "市民大道 100 号地铁站 A 口大厅",
                31.2304, 121.4737, "021-6800-8001", today.minusYears(1));
        AedDevice d2 = device("AED-0002", "中央公园北门", "中央公园北门游客服务中心",
                31.2360, 121.4690, "021-6800-8002", today.minusYears(1));
        AedDevice d3 = device("AED-0003", "第一人民医院门诊大厅", "建设路 45 号门诊一楼",
                31.2280, 121.4820, "021-6800-8003", today.minusYears(2));
        AedDevice d4 = device("AED-0004", "体育中心游泳馆", "体育路 8 号游泳馆入口",
                31.2420, 121.4650, "021-6800-8004", today.minusYears(2));
        AedDevice d5 = device("AED-0005", "火车南站候车厅", "火车南站二楼候车厅东侧",
                31.2250, 121.4550, "021-6800-8005", today.minusYears(3));
        AedDevice d6 = device("AED-0006", "大学城图书馆", "学府路 200 号图书馆一层",
                31.2330, 121.4900, "021-6800-8006", today.minusYears(1));
        AedDevice d7 = device("AED-0007", "滨江步道3号岗亭", "滨江步道 3 号治安岗亭",
                31.2390, 121.4980, "021-6800-8007", today.minusMonths(2));
        AedDevice d8 = device("AED-0008", "政务服务中心", "为民路 1 号政务服务中心大厅",
                31.2270, 121.4700, "021-6800-8008", today.minusYears(1));

        // 1) 正常可用：10 天前巡检通过，耗材有效期充足
        inspection(d1, "王巡检", now.minus(10, ChronoUnit.DAYS),
                true, true, true, "BAT-2026-A01", today.plusYears(2), "PAD-2026-P01", today.plusYears(2));

        // 2) 巡检合格，但 2 小时前发生紧急取用 -> 待回补
        inspection(d2, "王巡检", now.minus(10, ChronoUnit.DAYS),
                true, true, true, "BAT-2026-A02", today.plusYears(2), "PAD-2026-P02", today.plusYears(2));
        usage(d2, "李先生", "路人突发心脏骤停，已取用电极片", now.minus(2, ChronoUnit.HOURS), UsageStatus.OPEN);

        // 3) 100 天前巡检（超过 90 天有效期）-> 巡检过期
        inspection(d3, "陈巡检", now.minus(100, ChronoUnit.DAYS),
                true, true, true, "BAT-2025-B03", today.plusYears(1), "PAD-2025-B03", today.plusYears(1));

        // 4) 5 天前巡检通过，但电池昨天到期 -> 耗材过期：电池
        inspection(d4, "陈巡检", now.minus(5, ChronoUnit.DAYS),
                true, true, true, "BAT-2021-C04", today.minusDays(1), "PAD-2026-C04", today.plusYears(1));

        // 5) 5 天前巡检发现箱体损坏 -> 箱体故障
        inspection(d5, "赵巡检", now.minus(5, ChronoUnit.DAYS),
                true, true, false, "BAT-2026-D05", today.plusYears(2), "PAD-2026-D05", today.plusYears(2));

        // 6) 取用后已完成回补 -> 待复检（演示复检批次核对）
        inspection(d6, "王巡检", now.minus(10, ChronoUnit.DAYS),
                true, true, true, "BAT-2026-E06", today.plusYears(2), "PAD-2026-E06", today.plusYears(2));
        UsageEvent e6 = usage(d6, "张同学", "操场急救取用", now.minus(1, ChronoUnit.DAYS), UsageStatus.REPLENISHED);
        replenishment(e6, d6, "刘维护", "BAT-2026-09A", today.plusYears(2), "PAD-2026-09A", today.plusYears(2));

        // 7) 新装设备，尚无巡检记录 -> 未巡检
        //    （d7 不创建任何巡检）

        // 8) 正常可用：20 天前巡检通过
        inspection(d8, "赵巡检", now.minus(20, ChronoUnit.DAYS),
                true, true, true, "BAT-2026-F08", today.plusYears(2), "PAD-2026-F08", today.plusYears(2));
    }

    private static AedDevice device(String code, String name, String address,
                                    double lat, double lng, String phone, LocalDate installedAt) {
        AedDevice d = new AedDevice();
        d.code = code;
        d.name = name;
        d.address = address;
        d.latitude = lat;
        d.longitude = lng;
        d.maintenancePhone = phone;
        d.installedAt = installedAt;
        d.persist();
        return d;
    }

    private static Inspection inspection(AedDevice device, String inspector, Instant at,
                                         boolean batteryOk, boolean padsOk, boolean cabinetOk,
                                         String batteryBatch, LocalDate batteryExpiry,
                                         String padsBatch, LocalDate padsExpiry) {
        Inspection i = new Inspection();
        i.device = device;
        i.inspectorName = inspector;
        i.inspectedAt = at;
        i.batteryOk = batteryOk;
        i.padsOk = padsOk;
        i.cabinetOk = cabinetOk;
        i.batteryBatch = batteryBatch;
        i.batteryExpiry = batteryExpiry;
        i.padsBatch = padsBatch;
        i.padsExpiry = padsExpiry;
        i.passed = batteryOk && padsOk && cabinetOk;
        i.persist();
        return i;
    }

    private static UsageEvent usage(AedDevice device, String reporter, String note,
                                    Instant at, UsageStatus status) {
        UsageEvent e = new UsageEvent();
        e.device = device;
        e.reporterName = reporter;
        e.note = note;
        e.reportedAt = at;
        e.status = status;
        e.persist();
        return e;
    }

    private static Replenishment replenishment(UsageEvent event, AedDevice device, String staff,
                                               String batteryBatch, LocalDate batteryExpiry,
                                               String padsBatch, LocalDate padsExpiry) {
        Replenishment r = new Replenishment();
        r.usageEvent = event;
        r.device = device;
        r.staffName = staff;
        r.batteryBatch = batteryBatch;
        r.batteryExpiry = batteryExpiry;
        r.padsBatch = padsBatch;
        r.padsExpiry = padsExpiry;
        r.persist();
        return r;
    }
}
