package com.industry.Config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.StringUtils;
import org.springframework.util.backoff.FixedBackOff;

import java.util.Map;

@Configuration
@EnableKafka
@EnableConfigurationProperties(KafkaConsumerProperties.class)
public class KafkaConsumerConfig {

    @Bean
    public ConsumerFactory<String, String> consumerFactory(
            KafkaProperties kafkaProperties,
            ObjectProvider<SslBundles> sslBundlesProvider,
            KafkaConsumerProperties consumerProperties) {
        Map<String, Object> properties = kafkaProperties.buildConsumerProperties(sslBundlesProvider.getIfAvailable());
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        properties.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        if (StringUtils.hasText(consumerProperties.getGroupId())) {
            properties.put(ConsumerConfig.GROUP_ID_CONFIG, consumerProperties.getGroupId());
        }
        return new DefaultKafkaConsumerFactory<>(properties);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaDefaultErrorHandler,
            KafkaConsumerProperties consumerProperties) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(Math.max(1, consumerProperties.getConcurrency()));
        factory.setCommonErrorHandler(kafkaDefaultErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }

    @Bean
    public DefaultErrorHandler kafkaDefaultErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 3L));
    }

    @Bean("machineKafkaTopics")
    public String[] machineKafkaTopics(KafkaConsumerProperties consumerProperties) {
        return consumerProperties.getTopics().toArray(new String[0]);
    }
}
