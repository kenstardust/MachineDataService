package com.industry.Redis.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;

@Service("rediscache")
@ConditionalOnProperty(prefix = "machine.redis", name = "enabled", havingValue = "true", matchIfMissing = false)
public class RedissonRedisCacheTemplate implements RedisCacheTemplate {

    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public RedissonRedisCacheTemplate(RedissonClient redissonClient, ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> void set(String key, T value, Duration ttl) {
        RBucket<String> bucket = redissonClient.getBucket(key);
        bucket.set(serialize(value), ttl);
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        RBucket<String> bucket = redissonClient.getBucket(key);
        String value = bucket.get();
        if (value == null) {
            return null;
        }
        return deserialize(value, type);
    }

    @Override
    public boolean delete(String key) {
        return redissonClient.getBucket(key).delete();
    }

    @Override
    public boolean exists(String key) {
        return redissonClient.getBucket(key).isExists();
    }

    @Override
    public <T> boolean update(String key, T value, Duration ttl) {
        RBucket<String> bucket = redissonClient.getBucket(key);
        if (!bucket.isExists()) {
            return false;
        }
        bucket.set(serialize(value), ttl);
        return true;
    }

    @Override
    public <T> boolean setIfAbsent(String key, T value, Duration ttl) {
        return redissonClient.getBucket(key).setIfAbsent(serialize(value), ttl);
    }

    private <T> String serialize(T value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Failed to serialize redis value", ex);
        }
    }

    private <T> T deserialize(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Failed to deserialize redis value", ex);
        }
    }
}
