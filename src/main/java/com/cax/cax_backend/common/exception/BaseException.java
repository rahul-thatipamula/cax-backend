package com.cax.cax_backend.common.exception;

import com.cax.cax_backend.common.constants.ErrorCodes;
import lombok.Getter;

/**
 * Abstract base exception — all custom exceptions inherit from this.
 * Demonstrates inheritance and encapsulation.
 */
@Getter
public abstract class BaseException extends RuntimeException {

    private final int statusCode;
    private final int errorCode;

    protected BaseException(String message, int statusCode, int errorCode) {
        super(message);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }

    protected BaseException(String message, int statusCode, int errorCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.errorCode = errorCode;
    }
}
