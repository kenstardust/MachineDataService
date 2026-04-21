package com.industry.Config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(RedisProperties.class)
public class RedisConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "machine.redis", name = "enabled", havingValue = "true", matchIfMissing = false)
    public RedissonClient redissonClient(RedisProperties properties) {
        Config config = new Config();
        var singleServer = config.useSingleServer()
                .setAddress(properties.getAddress())
                .setDatabase(properties.getDatabase())
                .setConnectionPoolSize(properties.getConnectionPoolSize())
                .setConnectionMinimumIdleSize(properties.getConnectionMinimumIdleSize())
                .setIdleConnectionTimeout(properties.getIdleConnectionTimeout())
                .setConnectTimeout(properties.getConnectTimeout());

        if (StringUtils.hasText(properties.getPassword())) {
            singleServer.setPassword(properties.getPassword());
        }

        return Redisson.create(config);
    }
}
