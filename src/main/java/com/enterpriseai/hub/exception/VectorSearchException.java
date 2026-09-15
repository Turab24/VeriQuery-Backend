package com.enterpriseai.hub.exception;

public class VectorSearchException extends ApiException {

    public VectorSearchException(String message, Throwable cause) {
        super(ErrorCode.VECTOR_SEARCH_ERROR, message, cause);
    }
}
