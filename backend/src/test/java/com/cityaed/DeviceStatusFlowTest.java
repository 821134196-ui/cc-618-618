package com.cityaed;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * 状态机验证：过期设备不可显示可用；取用后必须完成回补和复检；
 * 刷新状态不能清除取用记录。
 */
@QuarkusTest
class DeviceStatusFlowTest {

    private long deviceIdByCode(String code) {
        return given().when().get("/api/public/devices").then().extract()
                .jsonPath().getLong("find { it.code == '" + code + "' }.id");
    }

    private Map<String, Object> publicDevice(String code) {
        return given().when().get("/api/public/devices").then().extract()
                .jsonPath().getObject("find { it.code == '" + code + "' }", Map.class);
    }

    // ---------- 停用原因 ----------

    @Test
    void 巡检过期的设备不能显示可用() {
        Map<String, Object> d = publicDevice("AED-003");
        assertThat(d.get("status"), is("OUT_OF_SERVICE"));
        assertThat((String) d.get("reason"), containsString("巡检过期"));
    }

    @Test
    void 耗材过期的设备不能显示可用() {
        Map<String, Object> d = publicDevice("AED-004");
        assertThat(d.get("status"), is("OUT_OF_SERVICE"));
        assertThat((String) d.get("reason"), containsString("耗材过期"));
    }

    @Test
    void 箱体故障的设备不能显示可用() {
        Map<String, Object> d = publicDevice("AED-005");
        assertThat(d.get("status"), is("OUT_OF_SERVICE"));
        assertThat((String) d.get("reason"), containsString("箱体故障"));
    }

    @Test
    void 种子中的待回补设备状态正确() {
        Map<String, Object> d = publicDevice("AED-007");
        assertThat(d.get("status"), is("PENDING_RESTOCK"));
    }

    // ---------- 取用 → 回补 → 复检 全流程 ----------

    @Test
    void 取用后必须完成回补和复检才恢复可用() {
        long id = deviceIdByCode("AED-002");
        assertThat(publicDevice("AED-002").get("status"), is("AVAILABLE"));

        // 1. 紧急取用 → 立即转为待回补
        given().contentType(ContentType.JSON).body("{\"reporterName\":\"路人\",\"note\":\"急救取用\"}")
                .when().post("/api/public/devices/" + id + "/retrieve")
                .then().statusCode(201)
                .body("status", is("PENDING_RESTOCK"));
        assertThat(publicDevice("AED-002").get("status"), is("PENDING_RESTOCK"));

        // 找到该设备的未闭环取用事件
        List<Map> open = given().when().get("/api/admin/retrievals?open=true")
                .then().extract().jsonPath().getList("findAll { it.deviceCode == 'AED-002' }", Map.class);
        assertThat(open, hasSize(1));
        long retrievalId = ((Number) open.get(0).get("id")).longValue();
        assertThat(open.get(0).get("state"), is("OPEN"));

        // 2. 未回补先复检 → 拒绝
        given().contentType(ContentType.JSON)
                .body("{\"inspectorName\":\"张巡检\",\"batteryOk\":true,\"padsOk\":true,\"cabinetOk\":true}")
                .when().post("/api/admin/retrievals/" + retrievalId + "/recheck")
                .then().statusCode(400);

        // 3. 回补登记（含实际批次）→ 事件变为已回补，但设备仍待复检，不能显示可用
        String batteryExpiry = LocalDate.now().plusMonths(12).toString();
        String padsExpiry = LocalDate.now().plusMonths(18).toString();
        given().contentType(ContentType.JSON)
                .body("{\"operator\":\"李维护\",\"batteryBatch\":\"BAT-NEW-01\",\"batteryExpiry\":\""
                        + batteryExpiry + "\",\"padsBatch\":\"PAD-NEW-01\",\"padsExpiry\":\"" + padsExpiry + "\"}")
                .when().post("/api/admin/retrievals/" + retrievalId + "/restock")
                .then().statusCode(200)
                .body("state", is("RESTOCKED"))
                .body("restockBatteryBatch", is("BAT-NEW-01"));
        assertThat("仅回补未复检，不能恢复可用",
                publicDevice("AED-002").get("status"), is("PENDING_RESTOCK"));

        // 4. 复检通过 → 事件闭环，设备恢复可用
        given().contentType(ContentType.JSON)
                .body("{\"inspectorName\":\"张巡检\",\"batteryOk\":true,\"padsOk\":true,\"cabinetOk\":true,\"notes\":\"复检正常\"}")
                .when().post("/api/admin/retrievals/" + retrievalId + "/recheck")
                .then().statusCode(200)
                .body("state", is("CLOSED"));
        assertThat(publicDevice("AED-002").get("status"), is("AVAILABLE"));

        // 5. 维护端可核对补回耗材的实际批次
        given().when().get("/api/admin/devices/" + id).then()
                .body("batteryBatch", is("BAT-NEW-01"))
                .body("padsBatch", is("PAD-NEW-01"));
    }

    @Test
    void 复检不通过则事件不闭环() {
        long id = deviceIdByCode("AED-008");
        given().contentType(ContentType.JSON).body("{\"reporterName\":\"市民\"}")
                .when().post("/api/public/devices/" + id + "/retrieve")
                .then().statusCode(201);

        long retrievalId = ((Number) given().when().get("/api/admin/retrievals?open=true")
                .then().extract().jsonPath()
                .getObject("find { it.deviceCode == 'AED-008' }", Map.class).get("id")).longValue();

        String expiry = LocalDate.now().plusMonths(12).toString();
        given().contentType(ContentType.JSON)
                .body("{\"operator\":\"李维护\",\"batteryBatch\":\"BAT-R8\",\"batteryExpiry\":\"" + expiry
                        + "\",\"padsBatch\":\"PAD-R8\",\"padsExpiry\":\"" + expiry + "\"}")
                .when().post("/api/admin/retrievals/" + retrievalId + "/restock")
                .then().statusCode(200);

        // 复检发现箱体仍有问题 → 不闭环，设备依旧不可用
        given().contentType(ContentType.JSON)
                .body("{\"inspectorName\":\"张巡检\",\"batteryOk\":true,\"padsOk\":true,\"cabinetOk\":false}")
                .when().post("/api/admin/retrievals/" + retrievalId + "/recheck")
                .then().statusCode(200)
                .body("state", is("RESTOCKED"));
        assertThat(publicDevice("AED-008").get("status"), is("PENDING_RESTOCK"));

        // 再次复检通过 → 闭环并恢复可用
        given().contentType(ContentType.JSON)
                .body("{\"inspectorName\":\"张巡检\",\"batteryOk\":true,\"padsOk\":true,\"cabinetOk\":true}")
                .when().post("/api/admin/retrievals/" + retrievalId + "/recheck")
                .then().statusCode(200)
                .body("state", is("CLOSED"));
        assertThat(publicDevice("AED-008").get("status"), is("AVAILABLE"));
    }

    @Test
    void 过期耗材不能用于回补() {
        long id = deviceIdByCode("AED-006");
        given().contentType(ContentType.JSON).body("{\"reporterName\":\"市民\"}")
                .when().post("/api/public/devices/" + id + "/retrieve")
                .then().statusCode(201);

        long retrievalId = ((Number) given().when().get("/api/admin/retrievals?open=true")
                .then().extract().jsonPath()
                .getObject("find { it.deviceCode == 'AED-006' }", Map.class).get("id")).longValue();

        given().contentType(ContentType.JSON)
                .body("{\"operator\":\"李维护\",\"batteryBatch\":\"BAT-OLD\",\"batteryExpiry\":\"2020-01-01\","
                        + "\"padsBatch\":\"PAD-OLD\",\"padsExpiry\":\"2020-01-01\"}")
                .when().post("/api/admin/retrievals/" + retrievalId + "/restock")
                .then().statusCode(400);
        assertThat(publicDevice("AED-006").get("status"), is("PENDING_RESTOCK"));
    }

    // ---------- 刷新不能清除取用记录 ----------

    @Test
    void 刷新状态不能清除取用记录() {
        // AED-007 在种子数据里就是待回补状态
        long openBefore = given().when().get("/api/admin/retrievals?open=true")
                .then().extract().jsonPath()
                .getList("findAll { it.deviceCode == 'AED-007' }", Map.class).size();
        assertThat(openBefore, is(1L));

        // 反复查询（模拟公众反复刷新页面），状态与取用记录不得改变
        for (int i = 0; i < 5; i++) {
            assertThat(publicDevice("AED-007").get("status"), is("PENDING_RESTOCK"));
        }
        List<Map> openAfter = given().when().get("/api/admin/retrievals?open=true")
                .then().extract().jsonPath()
                .getList("findAll { it.deviceCode == 'AED-007' }", Map.class);
        assertThat(openAfter, hasSize(1));
        assertThat(openAfter.get(0).get("state"), is("OPEN"));

        // 同一设备不允许重复取用（防止用重复请求冲刷状态）
        long id = deviceIdByCode("AED-007");
        given().contentType(ContentType.JSON).body("{\"reporterName\":\"路人\"}")
                .when().post("/api/public/devices/" + id + "/retrieve")
                .then().statusCode(409);
    }
}
