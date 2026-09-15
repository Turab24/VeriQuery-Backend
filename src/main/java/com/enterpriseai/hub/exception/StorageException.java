package com.enterpriseai.hub.exception;

public class StorageException extends ApiException {

    public StorageException(String message, Throwable cause) {
        super(ErrorCode.STORAGE_ERROR, message, cause);
    }

    public StorageException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
