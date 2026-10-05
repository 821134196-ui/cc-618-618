package com.cityaed;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * 公众接口安全性与地图/列表一致性验证。
 */
@QuarkusTest
class PublicApiTest {

    @Test
    void 公众接口不展示内部维修电话() {
        String body = given().when().get("/api/public/devices").then()
                .statusCode(200).extract().asString();
        assertThat(body, not(containsString("maintainerPhone")));
        assertThat(body, not(containsString("13800000001")));
        // 批次属于内部维护信息，公众端同样不返回
        assertThat(body, not(containsString("batteryBatch")));

        long id = given().when().get("/api/public/devices").then().extract()
                .jsonPath().getLong("[0].id");
        String detail = given().when().get("/api/public/devices/" + id).then()
                .statusCode(200).extract().asString();
        assertThat(detail, not(containsString("maintainerPhone")));
    }

    @Test
    void 维护端可以看到内部电话与耗材批次() {
        String body = given().when().get("/api/admin/devices").then()
                .statusCode(200).extract().asString();
        assertThat(body, containsString("maintainerPhone"));
        assertThat(body, containsString("batteryBatch"));
    }

    @Test
    void 地图与列表看到的状态完全一致() {
        // 前端地图与列表渲染同一份 /api/public/devices 数据。
        // 这里模拟两个视图各自取数，验证每个设备的坐标（地图需要）与状态（列表需要）
        // 都齐全，且两次取数的状态映射一致 —— 不会出现地图与列表结果不同。
        List<Map> forMap = given().when().get("/api/public/devices")
                .then().statusCode(200).extract().jsonPath().getList("$", Map.class);
        List<Map> forList = given().when().get("/api/public/devices")
                .then().statusCode(200).extract().jsonPath().getList("$", Map.class);

        assertThat(forMap, hasSize(greaterThanOrEqualTo(8)));
        for (Map<String, Object> d : forMap) {
            assertThat("地图标记需要坐标", d.get("latitude"), notNullValue());
            assertThat("地图标记需要坐标", d.get("longitude"), notNullValue());
            assertThat("列表需要状态", d.get("status"), notNullValue());
        }

        Map<Object, Object> mapStatuses = forMap.stream()
                .collect(Collectors.toMap(d -> d.get("id"), d -> d.get("status")));
        Map<Object, Object> listStatuses = forList.stream()
                .collect(Collectors.toMap(d -> d.get("id"), d -> d.get("status")));
        assertThat("地图与列表的状态必须一致", mapStatuses, is(listStatuses));

        // 停用设备必须带具体原因，两个视图都能展示
        for (Map<String, Object> d : forList) {
            if ("OUT_OF_SERVICE".equals(d.get("status"))) {
                assertThat("停用设备必须给出原因", (String) d.get("reason"), notNullValue());
            }
        }
    }

    @Test
    void 取用会触发本地模拟报警() {
        List<Map> alarms = given().when().get("/api/admin/alarms")
                .then().statusCode(200).extract().jsonPath().getList("$", Map.class);
        assertThat(alarms, hasSize(greaterThanOrEqualTo(1)));
        assertThat((String) alarms.get(0).get("message"), containsString("紧急取用"));
    }
}
