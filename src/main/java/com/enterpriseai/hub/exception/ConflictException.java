package com.enterpriseai.hub.exception;

public class ConflictException extends ApiException {

    public ConflictException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ConflictException emailTaken(String email) {
        return new ConflictException(ErrorCode.EMAIL_ALREADY_EXISTS,
                "An account already exists for " + email);
    }
}
