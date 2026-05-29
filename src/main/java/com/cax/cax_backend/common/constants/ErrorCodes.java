package com.cax.cax_backend.common.constants;

/**
 * Centralized error codes matching the Node.js backend.
 */
public final class ErrorCodes {

    private ErrorCodes() {}

    // Authentication errors (1000-1099)
    public static final int UNAUTHORIZED = 1000;
    public static final int INVALID_TOKEN = 1001;
    public static final int TOKEN_EXPIRED = 1002;
    public static final int INVALID_CREDENTIALS = 1003;
    public static final int USER_NOT_FOUND = 1004;

    // Authorization errors (1100-1199)
    public static final int FORBIDDEN = 1100;
    public static final int INSUFFICIENT_PERMISSIONS = 1101;
    public static final int ADMIN_ONLY = 1102;

    // Validation errors (1200-1299)
    public static final int VALIDATION_ERROR = 1200;
    public static final int INVALID_INPUT = 1201;
    public static final int MISSING_REQUIRED_FIELD = 1202;
    public static final int INVALID_FORMAT = 1203;

    // Resource errors (1300-1399)
    public static final int RESOURCE_NOT_FOUND = 1300;
    public static final int RESOURCE_ALREADY_EXISTS = 1301;
    public static final int RESOURCE_CONFLICT = 1302;

    // Business logic errors (1400-1499)
    public static final int COLLEGE_DETAILS_ALREADY_EXISTS = 1400;
    public static final int ID_VERIFICATION_PENDING = 1401;
    public static final int INSUFFICIENT_BALANCE = 1402;
    public static final int PURCHASE_VERIFICATION_FAILED = 1404;

    // Server errors (1500-1599)
    public static final int INTERNAL_SERVER_ERROR = 1500;
    public static final int DATABASE_ERROR = 1501;
    public static final int EXTERNAL_SERVICE_ERROR = 1502;
}
