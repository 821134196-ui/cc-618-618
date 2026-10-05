package com.cityaed.api;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

/** 404 统一返回 JSON */
@Provider
public class NotFoundMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException exception) {
        String msg = exception.getMessage() != null ? exception.getMessage() : "资源不存在";
        return Response.status(404).entity(Map.of("error", msg)).build();
    }
}
