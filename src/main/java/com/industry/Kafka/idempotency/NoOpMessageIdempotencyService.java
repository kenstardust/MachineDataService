package com.industry.Kafka.idempotency;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "machine.redis", name = "enabled", havingValue = "false", matchIfMissing = false)
public class NoOpMessageIdempotencyService implements MessageIdempotencyService {

    @Override
    public AcquireResult tryAcquire(String messageId) {
        return AcquireResult.NEW;
    }

    @Override
    public void markDone(String messageId) {
        // no-op
    }

    @Override
    public void releaseProcessing(String messageId) {
        // no-op
    }
}
