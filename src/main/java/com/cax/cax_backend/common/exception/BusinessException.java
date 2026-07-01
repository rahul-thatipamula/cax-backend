package com.cax.cax_backend.common.exception;

import com.cax.cax_backend.common.constants.ErrorCodes;

/**
 * Business logic exceptions — all extend BaseException (inheritance).
 */
public class BusinessException {

    public static class ResourceNotFoundException extends BaseException {
        public ResourceNotFoundException(String resource) {
            super(resource + " not found", 404, ErrorCodes.RESOURCE_NOT_FOUND);
        }
        public ResourceNotFoundException(String resource, String identifier) {
            super(resource + " with identifier '" + identifier + "' not found", 404, ErrorCodes.RESOURCE_NOT_FOUND);
        }
    }

    public static class ResourceAlreadyExistsException extends BaseException {
        public ResourceAlreadyExistsException(String resource) {
            super(resource + " already exists", 409, ErrorCodes.RESOURCE_ALREADY_EXISTS);
        }
        public ResourceAlreadyExistsException(String resource, String identifier) {
            super(resource + " with identifier '" + identifier + "' already exists", 409, ErrorCodes.RESOURCE_ALREADY_EXISTS);
        }
    }

    public static class ResourceConflictException extends BaseException {
        public ResourceConflictException(String message) {
            super(message, 409, ErrorCodes.RESOURCE_CONFLICT);
        }
    }

    public static class CollegeDetailsAlreadyExistsException extends BaseException {
        public CollegeDetailsAlreadyExistsException() {
            super("College details already added", 400, ErrorCodes.COLLEGE_DETAILS_ALREADY_EXISTS);
        }
    }

    public static class InsufficientBalanceException extends BaseException {
        public InsufficientBalanceException() {
            super("Insufficient balance", 400, ErrorCodes.INSUFFICIENT_BALANCE);
        }
        public InsufficientBalanceException(double required, double available) {
            super("Insufficient balance. Required: " + required + ", Available: " + available, 400, ErrorCodes.INSUFFICIENT_BALANCE);
        }
    }

    public static class BadRequestException extends BaseException {
        public BadRequestException(String message) {
            super(message, 400, ErrorCodes.VALIDATION_ERROR);
        }
    }

    public static class ForbiddenException extends BaseException {
        public ForbiddenException(String message) {
            super(message, 403, ErrorCodes.FORBIDDEN);
        }
    }

    public static class PurchaseVerificationFailedException extends BaseException {
        public PurchaseVerificationFailedException() {
            super("Purchase verification failed", 400, ErrorCodes.PURCHASE_VERIFICATION_FAILED);
        }
        public PurchaseVerificationFailedException(String message) {
            super(message, 400, ErrorCodes.PURCHASE_VERIFICATION_FAILED);
        }
    }
}
