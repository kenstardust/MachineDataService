package com.industry.iotdb.exception;

import com.industry.iotdb.model.response.OperationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IoTDBExceptionHandler {

    @ExceptionHandler(IoTDBOperationException.class)
    public ResponseEntity<OperationResponse> handleIoTDBOperationException(IoTDBOperationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(OperationResponse.failure(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<OperationResponse> handleValidationException(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(OperationResponse.failure(ex.getBindingResult().getAllErrors().get(0).getDefaultMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<OperationResponse> handleGenericException(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(OperationResponse.failure(ex.getMessage()));
    }
}
