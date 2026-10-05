package com.cityaed.service;

import com.cityaed.model.AlarmEvent;
import com.cityaed.model.Device;
import com.cityaed.model.Inspection;
import com.cityaed.model.RetrievalEvent;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 首次启动时写入演示数据：覆盖"可用 / 待回补 / 巡检过期 / 耗材过期 / 箱体故障"
 * 全部状态，以及一条已闭环的取用历史。日期均相对当前日期生成，保证演示状态恒成立。
 */
@ApplicationScoped
public class DataSeeder {

    @Startup
    void seed() {
        // 先在事务外做一次读取：触发 Hibernate 初始化和 schema 生成，
        // 避免建表 DDL 与种子数据事务并发争用 SQLite 文件锁。
        if (Device.count() > 0) {
            return;
        }
        QuarkusTransaction.requiringNew().run(this::doSeed);
    }

    void doSeed() {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

        // 1. 可用：巡检与耗材都在有效期内，且有一次已闭环的取用历史
        Device d1 = device("AED-001", "市民中心一楼大厅", "人民大道 100 号市民中心",
                31.2304, 121.4737, "13800000001",
                "BAT-2026-A01", today.plusMonths(14), "PAD-2026-P01", today.plusMonths(20),
                today.minusDays(20), false, false);
        seedClosedRetrieval(d1, now);

        // 2. 可用
        device("AED-002", "中央图书馆北门", "文化路 55 号中央图书馆",
                31.2345, 121.4801, "13800000002",
                "BAT-2026-A02", today.plusMonths(10), "PAD-2026-P02", today.plusMonths(16),
                today.minusDays(45), false, false);

        // 3. 巡检过期：上次巡检距今 120 天（有效期 90 天）
        device("AED-003", "地铁人民广场站 3 号口", "人民广场地铁站换乘大厅",
                31.2270, 121.4690, "13800000003",
                "BAT-2025-B11", today.plusMonths(8), "PAD-2025-Q11", today.plusMonths(12),
                today.minusDays(120), false, false);

        // 4. 耗材过期：电极片已过期 5 天
        device("AED-004", "体育中心游泳馆", "体育路 8 号市体育中心",
                31.2410, 121.4620, "13800000004",
                "BAT-2026-A04", today.plusMonths(9), "PAD-2024-P09", today.minusDays(5),
                today.minusDays(30), false, false);

        // 5. 箱体故障：上次巡检箱体未通过
        device("AED-005", "滨江公园东门岗亭", "滨江大道 300 号公园东门",
                31.2190, 121.4850, "13800000005",
                "BAT-2026-A05", today.plusMonths(11), "PAD-2026-P05", today.plusMonths(18),
                today.minusDays(10), true, false);

        // 6. 可用
        device("AED-006", "第一人民医院急诊楼", "健康路 1 号急诊楼一层",
                31.2255, 121.4900, "13800000006",
                "BAT-2026-A06", today.plusMonths(15), "PAD-2026-P06", today.plusMonths(22),
                today.minusDays(7), false, false);

        // 7. 待回补：2 小时前发生紧急取用，尚未回补
        Device d7 = device("AED-007", "大学城学生活动中心", "学府路 88 号学生活动中心",
                31.2080, 121.4450, "13800000007",
                "BAT-2026-A07", today.plusMonths(6), "PAD-2026-P07", today.plusMonths(9),
                today.minusDays(15), false, false);
        RetrievalEvent open = new RetrievalEvent();
        open.device = d7;
        open.retrievedAt = now.minusHours(2);
        open.reporterName = "现场市民";
        open.note = "球场有人心脏骤停，开箱取用";
        open.persist();
        AlarmEvent alarm = new AlarmEvent();
        alarm.device = d7;
        alarm.triggeredAt = open.retrievedAt;
        alarm.type = "RETRIEVAL";
        alarm.message = "设备 AED-007（大学城学生活动中心）发生紧急取用，请安排回补与复检";
        alarm.persist();

        // 8. 可用
        device("AED-008", "长途汽车客运站候车厅", "站前路 12 号客运总站",
                31.2450, 121.4530, "13800000008",
                "BAT-2026-A08", today.plusMonths(12), "PAD-2026-P08", today.plusMonths(14),
                today.minusDays(60), false, false);
    }

    private Device device(String code, String name, String address, double lat, double lng,
                          String phone, String batteryBatch, LocalDate batteryExpiry,
                          String padsBatch, LocalDate padsExpiry,
                          LocalDate lastInspection, boolean cabinetFault, boolean consumableFault) {
        Device d = new Device();
        d.code = code;
        d.name = name;
        d.address = address;
        d.latitude = lat;
        d.longitude = lng;
        d.maintainerPhone = phone;
        d.batteryBatch = batteryBatch;
        d.batteryExpiry = batteryExpiry;
        d.padsBatch = padsBatch;
        d.padsExpiry = padsExpiry;
        d.lastInspectionDate = lastInspection;
        d.nextInspectionDue = lastInspection.plusDays(MaintenanceService.INSPECTION_VALID_DAYS);
        d.cabinetFault = cabinetFault;
        d.consumableFault = consumableFault;
        d.createdAt = LocalDateTime.now();
        d.persist();

        Inspection insp = new Inspection();
        insp.device = d;
        insp.type = Inspection.Type.ROUTINE;
        insp.inspectorName = "张巡检";
        insp.inspectedAt = lastInspection.atTime(9, 30);
        insp.batteryOk = !consumableFault;
        insp.padsOk = !consumableFault;
        insp.cabinetOk = !cabinetFault;
        insp.batteryBatch = batteryBatch;
        insp.batteryExpiry = batteryExpiry;
        insp.padsBatch = padsBatch;
        insp.padsExpiry = padsExpiry;
        insp.passed = insp.batteryOk && insp.padsOk && insp.cabinetOk;
        insp.notes = cabinetFault ? "箱门卡扣损坏，待维修" : "例行巡检";
        insp.persist();
        return d;
    }

    /** AED-001 的历史：一个月前取用 → 回补 → 复检通过 → 闭环 */
    private void seedClosedRetrieval(Device d, LocalDateTime now) {
        RetrievalEvent r = new RetrievalEvent();
        r.device = d;
        r.retrievedAt = now.minusDays(30);
        r.reporterName = "物业保安";
        r.note = "大厅演练取用";
        r.restockedAt = now.minusDays(29);
        r.restockOperator = "李维护";
        r.restockBatteryBatch = d.batteryBatch;
        r.restockBatteryExpiry = d.batteryExpiry;
        r.restockPadsBatch = d.padsBatch;
        r.restockPadsExpiry = d.padsExpiry;
        r.persist();

        Inspection recheck = new Inspection();
        recheck.device = d;
        recheck.type = Inspection.Type.RECHECK;
        recheck.inspectorName = "张巡检";
        recheck.inspectedAt = now.minusDays(29).plusHours(3);
        recheck.batteryOk = true;
        recheck.padsOk = true;
        recheck.cabinetOk = true;
        recheck.batteryBatch = d.batteryBatch;
        recheck.batteryExpiry = d.batteryExpiry;
        recheck.padsBatch = d.padsBatch;
        recheck.padsExpiry = d.padsExpiry;
        recheck.passed = true;
        recheck.notes = "取用后复检，全部正常";
        recheck.persist();

        r.recheckInspection = recheck;
        r.closedAt = recheck.inspectedAt;
        r.persist();
    }
}
