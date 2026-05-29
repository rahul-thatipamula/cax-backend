package com.cax.cax_backend.common.exception;

import com.cax.cax_backend.common.constants.ErrorCodes;

/**
 * Authentication & authorization exceptions — all extend BaseException (inheritance).
 */
public class AuthException {

    public static class UnauthorizedException extends BaseException {
        public UnauthorizedException() {
            super("Unauthorized access", 401, ErrorCodes.UNAUTHORIZED);
        }
        public UnauthorizedException(String message) {
            super(message, 401, ErrorCodes.UNAUTHORIZED);
        }
    }

    public static class InvalidTokenException extends BaseException {
        public InvalidTokenException() {
            super("Invalid token", 401, ErrorCodes.INVALID_TOKEN);
        }
        public InvalidTokenException(String message) {
            super(message, 401, ErrorCodes.INVALID_TOKEN);
        }
    }

    public static class TokenExpiredException extends BaseException {
        public TokenExpiredException() {
            super("Token has expired", 401, ErrorCodes.TOKEN_EXPIRED);
        }
    }

    public static class InvalidCredentialsException extends BaseException {
        public InvalidCredentialsException() {
            super("Invalid credentials", 401, ErrorCodes.INVALID_CREDENTIALS);
        }
    }

    public static class UserNotFoundException extends BaseException {
        public UserNotFoundException() {
            super("User not found", 404, ErrorCodes.USER_NOT_FOUND);
        }
        public UserNotFoundException(String userId) {
            super("User not found: " + userId, 404, ErrorCodes.USER_NOT_FOUND);
        }
    }

    public static class ForbiddenException extends BaseException {
        public ForbiddenException() {
            super("Access forbidden", 403, ErrorCodes.FORBIDDEN);
        }
        public ForbiddenException(String message) {
            super(message, 403, ErrorCodes.FORBIDDEN);
        }
    }

    public static class InsufficientPermissionsException extends BaseException {
        public InsufficientPermissionsException() {
            super("Insufficient permissions", 403, ErrorCodes.INSUFFICIENT_PERMISSIONS);
        }
    }

    public static class AdminOnlyException extends BaseException {
        public AdminOnlyException() {
            super("Admin access required", 403, ErrorCodes.ADMIN_ONLY);
        }
    }
}
