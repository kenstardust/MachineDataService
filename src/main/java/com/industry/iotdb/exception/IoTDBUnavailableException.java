package com.industry.iotdb.exception;

import org.springframework.http.HttpStatus;

public class IoTDBUnavailableException extends IoTDBOperationException {

    public IoTDBUnavailableException(String message, Throwable cause) {
        super(message, "IoTDB service is temporarily unavailable", HttpStatus.SERVICE_UNAVAILABLE, cause);
    }
}
