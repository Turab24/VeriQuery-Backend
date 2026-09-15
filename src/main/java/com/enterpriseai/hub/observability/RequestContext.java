package com.enterpriseai.hub.observability;

import org.slf4j.MDC;

/**
 * Accessors for the per-request diagnostic context. Values are placed in the SLF4J MDC
 * by {@link RequestLoggingFilter} so that every log line emitted while handling a request
 * carries the same correlation id.
 */
public final class RequestContext {

    public static final String REQUEST_ID = "requestId";
    public static final String USER_ID = "userId";
    public static final String HEADER = "X-Request-Id";

    private RequestContext() {
    }

    public static String currentRequestId() {
        return MDC.get(REQUEST_ID);
    }

    public static void setRequestId(String requestId) {
        MDC.put(REQUEST_ID, requestId);
    }

    public static void setUserId(String userId) {
        MDC.put(USER_ID, userId);
    }

    public static void clear() {
        MDC.remove(REQUEST_ID);
        MDC.remove(USER_ID);
    }
}
