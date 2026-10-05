package com.cityaed.api;

import jakarta.ws.rs.core.Response;

import java.util.Map;

/** 统一 JSON 错误响应：{"error": "..."} */
public final class ApiErrors {

    private ApiErrors() {
    }

    public static Response error(int status, String message) {
        return Response.status(status).entity(Map.of("error", message)).build();
    }

    public static Response badRequest(String message) {
        return error(400, message);
    }

    public static Response conflict(String message) {
        return error(409, message);
    }
}
