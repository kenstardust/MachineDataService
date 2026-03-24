package com.industry.iotdb.exception;

public class IoTDBOperationException extends RuntimeException {

    public IoTDBOperationException(String message) {
        super(message);
    }

    public IoTDBOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
