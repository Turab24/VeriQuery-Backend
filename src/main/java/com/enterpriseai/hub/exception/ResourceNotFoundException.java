package com.enterpriseai.hub.exception;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public static ResourceNotFoundException document(Long id) {
        return new ResourceNotFoundException(ErrorCode.DOCUMENT_NOT_FOUND, "Document " + id + " was not found");
    }

    public static ResourceNotFoundException conversation(Long id) {
        return new ResourceNotFoundException(ErrorCode.CONVERSATION_NOT_FOUND, "Conversation " + id + " was not found");
    }

    public static ResourceNotFoundException user(Object identifier) {
        return new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, "User " + identifier + " was not found");
    }
}
