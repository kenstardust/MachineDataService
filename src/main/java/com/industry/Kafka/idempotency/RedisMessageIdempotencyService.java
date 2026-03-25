package com.industry.Kafka.idempotency;

import com.industry.Config.KafkaConsumerProperties;
import com.industry.Redis.template.RedisCacheTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class RedisMessageIdempotencyService implements MessageIdempotencyService {

    private final RedisCacheTemplate redisCacheTemplate;
    private final KafkaConsumerProperties properties;

    public RedisMessageIdempotencyService(RedisCacheTemplate redisCacheTemplate, KafkaConsumerProperties properties) {
        this.redisCacheTemplate = redisCacheTemplate;
        this.properties = properties;
    }

    @Override
    public AcquireResult tryAcquire(String messageId) {
        if (!Boolean.TRUE.equals(properties.getIdempotency().getEnabled())) {
            return AcquireResult.NEW;
        }
        if (!StringUtils.hasText(messageId)) {
            throw new IllegalArgumentException("messageId must not be blank");
        }

        String doneKey = doneKey(messageId);
        if (redisCacheTemplate.exists(doneKey)) {
            return AcquireResult.DUPLICATE_DONE;
        }

        String processingKey = processingKey(messageId);
        boolean locked = redisCacheTemplate.setIfAbsent(
                processingKey,
                "1",
                Duration.ofSeconds(properties.getIdempotency().getProcessingTtlSeconds())
        );

        if (locked) {
            return AcquireResult.NEW;
        }

        if (redisCacheTemplate.exists(doneKey)) {
            return AcquireResult.DUPLICATE_DONE;
        }
        return AcquireResult.IN_PROGRESS;
    }

    @Override
    public void markDone(String messageId) {
        if (!Boolean.TRUE.equals(properties.getIdempotency().getEnabled())) {
            return;
        }
        redisCacheTemplate.set(
                doneKey(messageId),
                "1",
                Duration.ofHours(properties.getIdempotency().getDoneTtlHours())
        );
        redisCacheTemplate.delete(processingKey(messageId));
    }

    @Override
    public void releaseProcessing(String messageId) {
        if (!Boolean.TRUE.equals(properties.getIdempotency().getEnabled())) {
            return;
        }
        redisCacheTemplate.delete(processingKey(messageId));
    }

    private String processingKey(String messageId) {
        return properties.getIdempotency().getKeyPrefix() + messageId + ":processing";
    }

    private String doneKey(String messageId) {
        return properties.getIdempotency().getKeyPrefix() + messageId + ":done";
    }
}
