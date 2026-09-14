package com.suretyseven.docflow.constants;

public final class ResponseMessages {

    private ResponseMessages() {
    }

    public static final String DOCUMENT_UPLOADED = "Document uploaded successfully";
    public static final String DOCUMENT_NOT_FOUND = "Document not found";
    public static final String DUPLICATE_DOCUMENT = "Duplicate document detected";
    public static final String LOGIN_SUCCESS = "Login successful";
    public static final String LOGIN_FAILED = "Invalid credentials";
    public static final String LOGOUT_SUCCESS = "Logged out successfully";
    public static final String DOWNLOAD_URL_GENERATED = "Download URL generated";
    public static final String DOCUMENT_LIST_FETCHED = "Documents fetched successfully";
    public static final String HISTORY_FETCHED = "History fetched successfully";
    public static final String DOCUMENT_FETCHED = "Document fetched successfully";

    public static final String VALIDATION_ERROR = "Validation failed";
    public static final String UNEXPECTED_ERROR = "An unexpected error occurred";
    public static final String UPLOAD_FAILED = "Failed to upload document to storage";

    public static final String FIELD_REQUIRED_SUFFIX = " is required";
    public static final String ANNUAL_REVENUE_INVALID = "annualRevenue must be >= 0";
    public static final String DOCUMENT_DATE_INVALID = "documentDate must not be null or a future date";
}
