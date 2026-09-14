package com.suretyseven.docflow.exception;

import lombok.Getter;

@Getter
public class DuplicateDocumentException extends RuntimeException {

    private final String existingDocumentId;
    private final String existingS3Key;
    private final String existingStatus;

    public DuplicateDocumentException(String message, String existingDocumentId, String existingS3Key, String existingStatus) {
        super(message);
        this.existingDocumentId = existingDocumentId;
        this.existingS3Key = existingS3Key;
        this.existingStatus = existingStatus;
    }
}
