package com.industry.Kafka.template;

import com.industry.Kafka.exception.KafkaConsumeRetryableException;
import com.industry.Kafka.idempotency.MessageIdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.util.StringUtils;

public abstract class AbstractKafkaConsumerTemplate<T> {

    private final MessageIdempotencyService idempotencyService;

    protected AbstractKafkaConsumerTemplate(MessageIdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    public final void onMessage(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        T message = deserialize(record.value());
        String messageId = extractMessageId(message, record);
        if (!StringUtils.hasText(messageId)) {
            throw new KafkaConsumeRetryableException("messageId is required for idempotent consume");
        }

        MessageIdempotencyService.AcquireResult result = idempotencyService.tryAcquire(messageId);
        if (result == MessageIdempotencyService.AcquireResult.DUPLICATE_DONE) {
            acknowledgment.acknowledge();
            return;
        }
        if (result == MessageIdempotencyService.AcquireResult.IN_PROGRESS) {
            throw new KafkaConsumeRetryableException("Message is being processed: " + messageId);
        }

        try {
            processBusiness(message, record);
            idempotencyService.markDone(messageId);
            acknowledgment.acknowledge();
        } catch (Exception ex) {
            idempotencyService.releaseProcessing(messageId);
            throw new KafkaConsumeRetryableException("Kafka consume failed: " + messageId, ex);
        }
    }

    protected abstract T deserialize(String payload);

    protected abstract String extractMessageId(T message, ConsumerRecord<String, String> record);

    protected abstract void processBusiness(T message, ConsumerRecord<String, String> record);
}
