package com.cityaed.service;

import com.cityaed.model.Device;
import com.cityaed.model.Inspection;
import com.cityaed.model.RetrievalEvent;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 巡检 / 回补 / 复检的业务规则。所有状态变更都通过事实记录完成，
 * 不存在"直接改状态"的入口。
 */
@ApplicationScoped
public class MaintenanceService {

    /** 巡检合格后的有效期：90 天 */
    public static final int INSPECTION_VALID_DAYS = 90;

    /**
     * 录入一次巡检（ROUTINE 或 RECHECK），并把结果应用到设备台账。
     */
    public Inspection applyInspection(Device device, Inspection.Type type, String inspectorName,
                                      boolean batteryOk, boolean padsOk, boolean cabinetOk,
                                      String batteryBatch, LocalDate batteryExpiry,
                                      String padsBatch, LocalDate padsExpiry,
                                      String notes) {
        Inspection insp = new Inspection();
        insp.device = device;
        insp.type = type;
        insp.inspectorName = inspectorName;
        insp.inspectedAt = LocalDateTime.now();
        insp.batteryOk = batteryOk;
        insp.padsOk = padsOk;
        insp.cabinetOk = cabinetOk;
        insp.batteryBatch = batteryBatch;
        insp.batteryExpiry = batteryExpiry;
        insp.padsBatch = padsBatch;
        insp.padsExpiry = padsExpiry;
        insp.passed = batteryOk && padsOk && cabinetOk;
        insp.notes = notes;
        insp.persist();

        // 巡检结果回写台账：实物批次、故障标志、下次应检日期
        device.lastInspectionDate = insp.inspectedAt.toLocalDate();
        device.nextInspectionDue = insp.inspectedAt.toLocalDate().plusDays(INSPECTION_VALID_DAYS);
        device.batteryBatch = batteryBatch;
        device.batteryExpiry = batteryExpiry;
        device.padsBatch = padsBatch;
        device.padsExpiry = padsExpiry;
        device.cabinetFault = !cabinetOk;
        device.consumableFault = !(batteryOk && padsOk);
        device.persist();
        return insp;
    }

    /**
     * 回补登记：维护人员核对并记录实际补回的耗材批次。
     * 只允许对"已取用且尚未回补"的事件登记；补回耗材必须在有效期内。
     */
    public String restock(RetrievalEvent r, String operator,
                          String batteryBatch, LocalDate batteryExpiry,
                          String padsBatch, LocalDate padsExpiry) {
        if (r.closedAt != null) {
            return "事件已闭环，不能重复回补";
        }
        if (r.restockedAt != null) {
            return "该事件已登记回补";
        }
        if (isBlank(operator) || isBlank(batteryBatch) || isBlank(padsBatch)
                || batteryExpiry == null || padsExpiry == null) {
            return "回补人、电池批次、电极片批次及有效期均为必填";
        }
        LocalDate today = LocalDate.now();
        if (batteryExpiry.isBefore(today) || padsExpiry.isBefore(today)) {
            return "补回耗材已过期，不能登记";
        }
        r.restockedAt = LocalDateTime.now();
        r.restockOperator = operator;
        r.restockBatteryBatch = batteryBatch;
        r.restockBatteryExpiry = batteryExpiry;
        r.restockPadsBatch = padsBatch;
        r.restockPadsExpiry = padsExpiry;
        r.persist();

        // 实物已补入箱体，台账同步更新（但设备仍处于待复检状态）
        Device d = r.device;
        d.batteryBatch = batteryBatch;
        d.batteryExpiry = batteryExpiry;
        d.padsBatch = padsBatch;
        d.padsExpiry = padsExpiry;
        d.persist();
        return null;
    }

    /**
     * 复检：必须先回补。复检记录现场实际批次（与回补登记核对），
     * 全部通过才闭环取用事件，设备才可能恢复可用。
     */
    public String recheck(RetrievalEvent r, String inspectorName,
                          boolean batteryOk, boolean padsOk, boolean cabinetOk, String notes) {
        if (r.closedAt != null) {
            return "事件已闭环";
        }
        if (r.restockedAt == null) {
            return "请先完成回补登记，再复检";
        }
        Inspection insp = applyInspection(r.device, Inspection.Type.RECHECK, inspectorName,
                batteryOk, padsOk, cabinetOk,
                r.restockBatteryBatch, r.restockBatteryExpiry,
                r.restockPadsBatch, r.restockPadsExpiry,
                notes);
        if (insp.passed) {
            r.recheckInspection = insp;
            r.closedAt = LocalDateTime.now();
            r.persist();
        }
        return null;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
