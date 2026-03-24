package com.industry.iotdb.exception;

import org.springframework.http.HttpStatus;

public class IoTDBOperationException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String safeMessage;

    public IoTDBOperationException(String message) {
        this(message, message, HttpStatus.BAD_REQUEST, null);
    }

    public IoTDBOperationException(String message, Throwable cause) {
        this(message, message, HttpStatus.BAD_REQUEST, cause);
    }

    public IoTDBOperationException(String message, String safeMessage, HttpStatus httpStatus) {
        this(message, safeMessage, httpStatus, null);
    }

    public IoTDBOperationException(String message, String safeMessage, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.safeMessage = safeMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getSafeMessage() {
        return safeMessage;
    }
}
