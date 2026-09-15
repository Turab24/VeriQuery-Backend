package com.enterpriseai.hub.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a correlation id to every request, publishes it to the logging MDC and to the
 * {@code X-Request-Id} response header, and records the wall-clock duration of the call.
 *
 * <p>Combined with the timings persisted on each assistant message this is what makes it
 * possible to answer "why was this particular answer slow?" from the logs alone:</p>
 *
 * <pre>
 * 10:15:30.412 INFO [3f9c1a7e] POST /api/chat -&gt; 200 in 2014ms
 * </pre>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestLoggingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(RequestContext.HEADER);
        if (!StringUtils.hasText(requestId)) {
            requestId = UUID.randomUUID().toString().substring(0, 8);
        }
        RequestContext.setRequestId(requestId);
        response.setHeader(RequestContext.HEADER, requestId);

        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;
            if (!isNoise(request.getRequestURI())) {
                log.info("{} {} -> {} in {}ms",
                        request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            }
            RequestContext.clear();
        }
    }

    private boolean isNoise(String uri) {
        return uri.startsWith("/actuator/health")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/v3/api-docs");
    }
}
