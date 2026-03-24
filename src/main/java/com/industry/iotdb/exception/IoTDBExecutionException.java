package com.industry.iotdb.exception;

import org.springframework.http.HttpStatus;

public class IoTDBExecutionException extends IoTDBOperationException {

    public IoTDBExecutionException(String message, Throwable cause) {
        super(message, "IoTDB operation execution failed", HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}
