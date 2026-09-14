package com.suretyseven.docflow.constants;

public final class AppConstants {

    private AppConstants() {
    }

    public static final int MAX_RETRIES = 3;

    public static final String STATUS_UPLOADED = "UPLOADED";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_PROCESSED = "PROCESSED";
    public static final String STATUS_FAILED = "FAILED";

    public static final String REASON_TIMEOUT = "PROCESSOR_TIMEOUT";
    public static final String REASON_ERROR = "PROCESSOR_ERROR";
    public static final String REASON_INVALID = "INVALID_RESULT";
    public static final String REASON_MAX_RETRIES = "MAX_RETRIES_EXCEEDED";
    public static final String REASON_VALIDATION_FAILED = "VALIDATION_FAILED";

    public static final String S3_FOLDER = "documents/";

    public static final String PROCESSOR_RESULT_SUCCESS = "SUCCESS";
    public static final String PROCESSOR_RESULT_TIMEOUT = "TIMEOUT";
    public static final String PROCESSOR_RESULT_ERROR = "ERROR";
    public static final String PROCESSOR_RESULT_INVALID = "INVALID_RESULT";

    public static final String MOCK_COMPANY_NAME = "ABC Construction Pvt Ltd";
    public static final String MOCK_REGISTRATION_NUMBER = "U12345DL2020PTC123456";
    public static final String MOCK_ADDRESS = "New Delhi";
    public static final String MOCK_ANNUAL_REVENUE = "12500000";

    public static final int DOC_ID_RANDOM_DIGITS = 5;
    public static final int MAX_DOC_ID_GENERATION_ATTEMPTS = 20;
    public static final String DOC_ID_PREFIX = "DOC-";

    public static final String JWT_HEADER = "Authorization";
    public static final String JWT_PREFIX = "Bearer ";
}
