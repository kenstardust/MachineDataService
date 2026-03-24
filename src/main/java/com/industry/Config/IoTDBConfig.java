package com.industry.Config;

import org.apache.iotdb.session.pool.SessionPool;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(IoTDBProperties.class)
public class IoTDBConfig {

    @Bean(destroyMethod = "close")
    public SessionPool sessionPool(IoTDBProperties properties) {
        return new SessionPool.Builder()
                .host(properties.getHost())
                .port(properties.getPort())
                .user(properties.getUsername())
                .password(properties.getPassword())
                .maxSize(properties.getMaxSize())
                .fetchSize(properties.getFetchSize())
                .enableRedirection(properties.getEnableRedirection())
                .enableCompression(false)
                .waitToGetSessionTimeoutInMs(properties.getConnectionTimeoutInMs())
                .build();
    }
}
