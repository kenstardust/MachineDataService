package com.industry.Kafka.template;

import com.industry.Kafka.exception.KafkaConsumeRetryableException;
import com.industry.Kafka.idempotency.MessageIdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AbstractKafkaConsumerTemplateTest {

    @Mock
    private MessageIdempotencyService idempotencyService;

    @Mock
    private Acknowledgment acknowledgment;

    @Test
    void shouldAckWhenDuplicateDone() {
        DemoTemplate template = new DemoTemplate(idempotencyService, false);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic-a", 0, 1L, "k", "msg-1");
        when(idempotencyService.tryAcquire("msg-1")).thenReturn(MessageIdempotencyService.AcquireResult.DUPLICATE_DONE);

        template.onMessage(record, acknowledgment);

        verify(acknowledgment).acknowledge();
        verify(idempotencyService, never()).markDone("msg-1");
    }

    @Test
    void shouldProcessAndAckWhenNewMessage() {
        DemoTemplate template = new DemoTemplate(idempotencyService, false);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic-a", 0, 2L, "k", "msg-2");
        when(idempotencyService.tryAcquire("msg-2")).thenReturn(MessageIdempotencyService.AcquireResult.NEW);

        template.onMessage(record, acknowledgment);

        verify(idempotencyService).markDone("msg-2");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void shouldThrowAndReleaseWhenBusinessFailed() {
        DemoTemplate template = new DemoTemplate(idempotencyService, true);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic-a", 0, 3L, "k", "msg-3");
        when(idempotencyService.tryAcquire("msg-3")).thenReturn(MessageIdempotencyService.AcquireResult.NEW);

        assertThrows(KafkaConsumeRetryableException.class, () -> template.onMessage(record, acknowledgment));

        verify(idempotencyService).releaseProcessing("msg-3");
        verify(acknowledgment, never()).acknowledge();
    }

    @Test
    void shouldThrowWhenInProgress() {
        DemoTemplate template = new DemoTemplate(idempotencyService, false);
        ConsumerRecord<String, String> record = new ConsumerRecord<>("topic-a", 0, 4L, "k", "msg-4");
        when(idempotencyService.tryAcquire("msg-4")).thenReturn(MessageIdempotencyService.AcquireResult.IN_PROGRESS);

        assertThrows(KafkaConsumeRetryableException.class, () -> template.onMessage(record, acknowledgment));

        verify(acknowledgment, never()).acknowledge();
        verify(idempotencyService, times(1)).tryAcquire("msg-4");
    }

    private static class DemoTemplate extends AbstractKafkaConsumerTemplate<String> {

        private final boolean throwOnProcess;

        private DemoTemplate(MessageIdempotencyService idempotencyService, boolean throwOnProcess) {
            super(idempotencyService);
            this.throwOnProcess = throwOnProcess;
        }

        @Override
        protected String deserialize(String payload) {
            return payload;
        }

        @Override
        protected String extractMessageId(String message, ConsumerRecord<String, String> record) {
            return message;
        }

        @Override
        protected void processBusiness(String message, ConsumerRecord<String, String> record) {
            if (throwOnProcess) {
                throw new RuntimeException("boom");
            }
        }
    }
}
