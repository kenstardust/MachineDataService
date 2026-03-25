package com.industry.Kafka.idempotency;

public interface MessageIdempotencyService {

    AcquireResult tryAcquire(String messageId);

    void markDone(String messageId);

    void releaseProcessing(String messageId);

    enum AcquireResult {
        NEW,
        DUPLICATE_DONE,
        IN_PROGRESS
    }
}
