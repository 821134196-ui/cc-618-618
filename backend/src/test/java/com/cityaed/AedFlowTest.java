package com.cityaed;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 关键业务约束的端到端验证：
 *  - 巡检过期 / 耗材过期 / 箱体故障的设备不能显示可用，且给出具体原因
 *  - 紧急取用后必须完成回补 + 复检才恢复可用
 *  - 复检必须核对补回耗材的实际批次
 *  - 取用记录不能通过刷新状态清除
 *  - 公众接口不输出内部维修电话
 *  - 地图与文字列表（同一数据源）状态一致
 */
@QuarkusTest
class AedFlowTest {

    // ---------- 过期与故障：不能显示可用 ----------

    @Test
    void expiredInspectionIsNotAvailable() {
        long id = createDevice();
        // 补录一条 100 天前的合格巡检（超过 90 天有效期）
        createInspection(id, inspBody(true, true, true,
                LocalDate.now().plusYears(1), LocalDate.now().plusYears(1),
                Instant.now().minus(100, ChronoUnit.DAYS).toString()))
                .then().statusCode(201);

        given().queryParam("query", codeOf(id))
                .when().get("/api/public/aeds")
                .then().statusCode(200)
                .body("[0].status", equalTo("OUT_OF_SERVICE"))
                .body("[0].statusReason", containsString("巡检过期"));
    }

    @Test
    void expiredConsumableIsNotAvailable() {
        long id = createDevice();
        // 电池昨天到期
        createInspectionWithExpiry(id, LocalDate.now().minusDays(1), LocalDate.now().plusYears(1));

        given().queryParam("query", codeOf(id))
                .when().get("/api/public/aeds")
                .then().statusCode(200)
                .body("[0].status", equalTo("OUT_OF_SERVICE"))
                .body("[0].statusReason", containsString("耗材过期"));
    }

    @Test
    void cabinetFaultIsNotAvailable() {
        long id = createDevice();
        createInspection(id, inspBody(true, true, false,
                LocalDate.now().plusYears(1), LocalDate.now().plusYears(1), null))
                .then().statusCode(201);

        given().queryParam("query", codeOf(id))
                .when().get("/api/public/aeds")
                .then().statusCode(200)
                .body("[0].status", equalTo("OUT_OF_SERVICE"))
                .body("[0].statusReason", containsString("箱体故障"));
    }

    @Test
    void neverInspectedIsNotAvailable() {
        long id = createDevice();
        given().queryParam("query", codeOf(id))
                .when().get("/api/public/aeds")
                .then().statusCode(200)
                .body("[0].status", equalTo("OUT_OF_SERVICE"))
                .body("[0].statusReason", containsString("未巡检"));
    }

    // ---------- 取用 -> 回补 -> 复检 全流程 ----------

    @Test
    void useReplenishRecheckFlow() {
        long deviceId = createDevice();
        passInspection(deviceId);
        // 初始可用
        given().queryParam("query", codeOf(deviceId)).when().get("/api/public/aeds")
                .then().body("[0].status", equalTo("AVAILABLE"));

        // 紧急取用 -> 立即待回补
        long eventId = given().contentType(ContentType.JSON)
                .body(Map.of("reporterName", "测试市民", "note", "模拟急救取用"))
                .when().post("/api/public/aeds/{id}/use", deviceId)
                .then().statusCode(201)
                .body("status", equalTo("OPEN"))
                .extract().jsonPath().getLong("id");

        given().queryParam("query", codeOf(deviceId)).when().get("/api/public/aeds")
                .then().body("[0].status", equalTo("PENDING_REPLENISHMENT"))
                .body("[0].statusReason", containsString("待回补"));

        // 重复取用被拒绝
        given().contentType(ContentType.JSON).body(Map.of("reporterName", "另一位市民"))
                .when().post("/api/public/aeds/{id}/use", deviceId)
                .then().statusCode(409);

        // 回补登记 -> 仍不可用，待复检
        given().contentType(ContentType.JSON).body(replenishBody("BAT-NEW-1", "PAD-NEW-1"))
                .when().post("/api/admin/usage-events/{id}/replenish", eventId)
                .then().statusCode(200).body("status", equalTo("REPLENISHED"));

        given().queryParam("query", codeOf(deviceId)).when().get("/api/public/aeds")
                .then().body("[0].status", equalTo("PENDING_REPLENISHMENT"))
                .body("[0].statusReason", containsString("待复检"));

        // 复检通过（批次与回补一致）-> 事件闭环，设备恢复可用
        given().contentType(ContentType.JSON).body(recheckBody("BAT-NEW-1", "PAD-NEW-1", true, true, true))
                .when().post("/api/admin/usage-events/{id}/recheck", eventId)
                .then().statusCode(200).body("status", equalTo("CLOSED"));

        given().queryParam("query", codeOf(deviceId)).when().get("/api/public/aeds")
                .then().body("[0].status", equalTo("AVAILABLE"));
    }

    @Test
    void recheckRequiresMatchingBatches() {
        long deviceId = createDevice();
        passInspection(deviceId);
        long eventId = reportUse(deviceId);

        given().contentType(ContentType.JSON).body(replenishBody("BAT-REAL-1", "PAD-REAL-1"))
                .when().post("/api/admin/usage-events/{id}/replenish", eventId)
                .then().statusCode(200);

        // 批次不符 -> 复检被拒绝，事件保持待复检，设备仍不可用
        given().contentType(ContentType.JSON).body(recheckBody("BAT-WRONG-9", "PAD-REAL-1", true, true, true))
                .when().post("/api/admin/usage-events/{id}/recheck", eventId)
                .then().statusCode(400)
                .body("error", containsString("批次"));

        given().queryParam("status", "REPLENISHED").when().get("/api/admin/usage-events")
                .then().statusCode(200)
                .body("find { it.id == " + eventId + " }.status", equalTo("REPLENISHED"));
        given().queryParam("query", codeOf(deviceId)).when().get("/api/public/aeds")
                .then().body("[0].status", equalTo("PENDING_REPLENISHMENT"));
    }

    @Test
    void failedRecheckKeepsDeviceUnavailable() {
        long deviceId = createDevice();
        passInspection(deviceId);
        long eventId = reportUse(deviceId);
        given().contentType(ContentType.JSON).body(replenishBody("BAT-R2", "PAD-R2"))
                .when().post("/api/admin/usage-events/{id}/replenish", eventId)
                .then().statusCode(200);

        // 复检发现箱体故障 -> 未通过，事件不闭环
        given().contentType(ContentType.JSON).body(recheckBody("BAT-R2", "PAD-R2", true, true, false))
                .when().post("/api/admin/usage-events/{id}/recheck", eventId)
                .then().statusCode(200).body("status", equalTo("REPLENISHED"));

        given().queryParam("query", codeOf(deviceId)).when().get("/api/public/aeds")
                .then().body("[0].status", equalTo("PENDING_REPLENISHMENT"));
    }

    @Test
    void replenishmentWithExpiredConsumablesRejected() {
        long deviceId = createDevice();
        passInspection(deviceId);
        long eventId = reportUse(deviceId);

        Map<String, Object> body = new HashMap<>(replenishBody("BAT-OLD", "PAD-OLD"));
        body.put("batteryExpiry", LocalDate.now().minusDays(3).toString());
        given().contentType(ContentType.JSON).body(body)
                .when().post("/api/admin/usage-events/{id}/replenish", eventId)
                .then().statusCode(400)
                .body("error", containsString("过期"));
    }

    // ---------- 取用记录不能通过刷新状态清除 ----------

    @Test
    void usageRecordCannotBeClearedByRefreshOrRoutineInspection() {
        long deviceId = createDevice();
        passInspection(deviceId);
        long eventId = reportUse(deviceId);

        // 反复查询（“刷新”）不改变状态
        for (int i = 0; i < 3; i++) {
            given().queryParam("query", codeOf(deviceId)).when().get("/api/public/aeds")
                    .then().body("[0].status", equalTo("PENDING_REPLENISHMENT"));
        }
        // 例行巡检即使全部合格，也不能清除取用事件
        passInspection(deviceId);
        given().queryParam("query", codeOf(deviceId)).when().get("/api/public/aeds")
                .then().body("[0].status", equalTo("PENDING_REPLENISHMENT"));

        // 取用事件仍然 OPEN，未被清除
        given().queryParam("status", "OPEN").when().get("/api/admin/usage-events")
                .then().statusCode(200)
                .body("find { it.id == " + eventId + " }.status", equalTo("OPEN"));
    }

    // ---------- 公众视图不泄露内部信息 ----------

    @Test
    void publicViewHidesMaintenancePhone() {
        long id = createDevice();
        passInspection(id);

        String publicBody = given().queryParam("query", codeOf(id))
                .when().get("/api/public/aeds").asString();
        assertThat(publicBody, not(containsString("maintenancePhone")));
        assertThat(publicBody, not(containsString("010-666")));

        String adminBody = given().when().get("/api/admin/aeds").asString();
        assertThat(adminBody, containsString("maintenancePhone"));
    }

    // ---------- 地图与列表一致性（同一数据源 + 搜索不改变状态） ----------

    @Test
    void mapAndListSeeIdenticalStatuses() {
        long id = createDevice();
        passInspection(id);
        String code = codeOf(id);

        // 列表（文字列表数据源）
        String listStatus = given().when().get("/api/public/aeds")
                .then().statusCode(200).extract().jsonPath()
                .getString("find { it.code == '%s' }.status".formatted(code));
        // 详情（地图标记点击后数据源）
        String detailStatus = given().when().get("/api/public/aeds/{id}", id)
                .then().statusCode(200).extract().jsonPath().getString("status");
        // 搜索过滤后（搜索只过滤，不改变状态）
        String searchedStatus = given().queryParam("query", code).when().get("/api/public/aeds")
                .then().statusCode(200).extract().jsonPath().getString("[0].status");

        assertEquals("AVAILABLE", listStatus);
        assertEquals(listStatus, detailStatus);
        assertEquals(listStatus, searchedStatus);
    }

    // ---------- 辅助方法 ----------

    private static long createDevice() {
        String code = "T-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return given().contentType(ContentType.JSON)
                .body(Map.of(
                        "code", code,
                        "name", "测试设备 " + code,
                        "address", "测试路 1 号",
                        "latitude", 31.23,
                        "longitude", 121.47,
                        "maintenancePhone", "010-666-" + code.substring(2)))
                .when().post("/api/admin/aeds")
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static String codeOf(long deviceId) {
        return given().when().get("/api/admin/aeds/{id}", deviceId)
                .then().statusCode(200).extract().jsonPath().getString("device.code");
    }

    private static Map<String, Object> inspBody(boolean batteryOk, boolean padsOk, boolean cabinetOk,
                                                LocalDate batteryExpiry, LocalDate padsExpiry,
                                                String inspectedAt) {
        Map<String, Object> body = new HashMap<>();
        body.put("inspectorName", "测试巡检员");
        body.put("batteryOk", batteryOk);
        body.put("padsOk", padsOk);
        body.put("cabinetOk", cabinetOk);
        body.put("batteryBatch", "BAT-TEST");
        body.put("batteryExpiry", batteryExpiry.toString());
        body.put("padsBatch", "PAD-TEST");
        body.put("padsExpiry", padsExpiry.toString());
        body.put("notes", "测试巡检");
        if (inspectedAt != null) {
            body.put("inspectedAt", inspectedAt);
        }
        return body;
    }

    private static Response createInspection(long deviceId, Map<String, Object> body) {
        return given().contentType(ContentType.JSON).body(body)
                .when().post("/api/admin/aeds/{id}/inspections", deviceId);
    }

    private static void passInspection(long deviceId) {
        createInspection(deviceId, inspBody(true, true, true,
                LocalDate.now().plusYears(1), LocalDate.now().plusYears(1), null))
                .then().statusCode(201).body("passed", equalTo(true));
    }

    private static void createInspectionWithExpiry(long deviceId, LocalDate batteryExpiry, LocalDate padsExpiry) {
        createInspection(deviceId, inspBody(true, true, true, batteryExpiry, padsExpiry, null))
                .then().statusCode(201);
    }

    private static long reportUse(long deviceId) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("reporterName", "测试市民", "note", "测试取用"))
                .when().post("/api/public/aeds/{id}/use", deviceId)
                .then().statusCode(201)
                .extract().jsonPath().getLong("id");
    }

    private static Map<String, Object> replenishBody(String batteryBatch, String padsBatch) {
        Map<String, Object> body = new HashMap<>();
        body.put("staffName", "测试维护员");
        body.put("batteryBatch", batteryBatch);
        body.put("batteryExpiry", LocalDate.now().plusYears(2).toString());
        body.put("padsBatch", padsBatch);
        body.put("padsExpiry", LocalDate.now().plusYears(2).toString());
        return body;
    }

    private static Map<String, Object> recheckBody(String batteryBatch, String padsBatch,
                                                   boolean batteryOk, boolean padsOk, boolean cabinetOk) {
        Map<String, Object> body = new HashMap<>();
        body.put("inspectorName", "测试复检员");
        body.put("batteryOk", batteryOk);
        body.put("padsOk", padsOk);
        body.put("cabinetOk", cabinetOk);
        body.put("batteryBatch", batteryBatch);
        body.put("batteryExpiry", LocalDate.now().plusYears(2).toString());
        body.put("padsBatch", padsBatch);
        body.put("padsExpiry", LocalDate.now().plusYears(2).toString());
        body.put("notes", "复检测试");
        return body;
    }
}
