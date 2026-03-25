package com.industry.Kafka.exception;

public class KafkaConsumeRetryableException extends RuntimeException {

    public KafkaConsumeRetryableException(String message) {
        super(message);
    }

    public KafkaConsumeRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
