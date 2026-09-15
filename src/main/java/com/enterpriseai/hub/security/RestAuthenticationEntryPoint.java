package com.enterpriseai.hub.security;

import com.enterpriseai.hub.common.ApiErrorResponse;
import com.enterpriseai.hub.exception.ErrorCode;
import com.enterpriseai.hub.observability.RequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Returns the standard error envelope (instead of an HTML login redirect) when an
 * unauthenticated caller hits a protected endpoint.
 */
@Component
@RequiredArgsConstructor
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(
                ErrorCode.UNAUTHENTICATED,
                "Authentication is required to access this resource",
                request.getRequestURI(),
                RequestContext.currentRequestId());
        response.setStatus(ErrorCode.UNAUTHENTICATED.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
