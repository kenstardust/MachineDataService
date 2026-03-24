package com.industry.iotdb.exception;

import org.springframework.http.HttpStatus;

public class IoTDBBadRequestException extends IoTDBOperationException {

    public IoTDBBadRequestException(String message) {
        super(message, message, HttpStatus.BAD_REQUEST);
    }

    public IoTDBBadRequestException(String message, Throwable cause) {
        super(message, message, HttpStatus.BAD_REQUEST, cause);
    }
}
