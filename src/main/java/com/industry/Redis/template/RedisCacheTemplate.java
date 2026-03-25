package com.industry.Redis.template;

import java.time.Duration;

public interface RedisCacheTemplate {

    <T> void set(String key, T value, Duration ttl);

    <T> T get(String key, Class<T> type);

    boolean delete(String key);

    boolean exists(String key);

    <T> boolean update(String key, T value, Duration ttl);

    <T> boolean setIfAbsent(String key, T value, Duration ttl);
}
