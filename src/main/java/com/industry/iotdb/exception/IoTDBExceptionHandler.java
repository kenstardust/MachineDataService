package com.industry.iotdb.exception;

import com.industry.iotdb.model.response.OperationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IoTDBExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(IoTDBExceptionHandler.class);

    @ExceptionHandler(IoTDBOperationException.class)
    public ResponseEntity<OperationResponse> handleIoTDBOperationException(IoTDBOperationException ex) {
        log.warn("IoTDB operation failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(ex.getHttpStatus()).body(OperationResponse.failure(ex.getSafeMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OperationResponse> handleValidationException(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse("Request validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(OperationResponse.failure(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OperationResponse> handleGenericException(Exception ex) {
        log.error("Unexpected IoTDB request error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(OperationResponse.failure("Internal server error"));
    }
}
