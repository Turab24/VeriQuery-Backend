package com.enterpriseai.hub.security;

import com.enterpriseai.hub.common.ApiErrorResponse;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.observability.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        log.warn("Access denied for {} {}", request.getMethod(), request.getRequestURI());
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.ACCESS_DENIED,
                "You do not have permission to perform this action",
                request.getRequestURI(),
                RequestContext.currentRequestId());
        response.setStatus(ErrorCode.ACCESS_DENIED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
