package com.industry.Kafka.idempotency;

import com.industry.Config.KafkaConsumerProperties;
import com.industry.Redis.template.RedisCacheTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisMessageIdempotencyServiceTest {

    @Mock
    private RedisCacheTemplate redisCacheTemplate;

    private RedisMessageIdempotencyService service;
    private KafkaConsumerProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KafkaConsumerProperties();
        properties.getIdempotency().setEnabled(true);
        properties.getIdempotency().setKeyPrefix("kafka:idempotent:");
        properties.getIdempotency().setProcessingTtlSeconds(600L);
        properties.getIdempotency().setDoneTtlHours(72L);
        service = new RedisMessageIdempotencyService(redisCacheTemplate, properties);
    }

    @Test
    void shouldReturnDuplicateDoneWhenDoneKeyExists() {
        when(redisCacheTemplate.exists("kafka:idempotent:msg-1:done")).thenReturn(true);

        MessageIdempotencyService.AcquireResult result = service.tryAcquire("msg-1");

        assertEquals(MessageIdempotencyService.AcquireResult.DUPLICATE_DONE, result);
        verify(redisCacheTemplate, never()).setIfAbsent(any(), any(), any());
    }

    @Test
    void shouldReturnNewWhenLockAcquired() {
        when(redisCacheTemplate.exists("kafka:idempotent:msg-2:done")).thenReturn(false);
        when(redisCacheTemplate.setIfAbsent(eq("kafka:idempotent:msg-2:processing"), eq("1"), any(Duration.class))).thenReturn(true);

        MessageIdempotencyService.AcquireResult result = service.tryAcquire("msg-2");

        assertEquals(MessageIdempotencyService.AcquireResult.NEW, result);
    }

    @Test
    void shouldReturnInProgressWhenNotDoneAndNotLocked() {
        when(redisCacheTemplate.exists("kafka:idempotent:msg-3:done")).thenReturn(false);
        when(redisCacheTemplate.setIfAbsent(eq("kafka:idempotent:msg-3:processing"), eq("1"), any(Duration.class))).thenReturn(false);

        MessageIdempotencyService.AcquireResult result = service.tryAcquire("msg-3");

        assertEquals(MessageIdempotencyService.AcquireResult.IN_PROGRESS, result);
    }

    @Test
    void shouldMarkDoneAndDeleteProcessing() {
        service.markDone("msg-4");

        verify(redisCacheTemplate).set(eq("kafka:idempotent:msg-4:done"), eq("1"), eq(Duration.ofHours(72L)));
        verify(redisCacheTemplate).delete("kafka:idempotent:msg-4:processing");
    }

    @Test
    void shouldDeleteProcessingOnRelease() {
        service.releaseProcessing("msg-5");

        verify(redisCacheTemplate).delete("kafka:idempotent:msg-5:processing");
    }

    @Test
    void shouldThrowWhenMessageIdBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.tryAcquire(" "));

        assertEquals("messageId must not be blank", ex.getMessage());
    }
}
