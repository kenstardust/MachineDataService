package com.industry.Config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaConsumerPropertiesBindingTest {

    @Test
    void shouldBindTopicsAndConcurrency() {
        StandardEnvironment environment = new StandardEnvironment();
        MutablePropertySources propertySources = environment.getPropertySources();

        Map<String, Object> props = new HashMap<>();
        props.put("machine.kafka.consumer.enabled", "true");
        props.put("machine.kafka.consumer.group-id", "machine-group");
        props.put("machine.kafka.consumer.concurrency", "4");
        props.put("machine.kafka.consumer.topics[0]", "topic-a");
        props.put("machine.kafka.consumer.topics[1]", "topic-b");
        props.put("machine.kafka.consumer.idempotency.enabled", "true");
        props.put("machine.kafka.consumer.idempotency.key-prefix", "kafka:idempotent:");

        propertySources.addFirst(new MapPropertySource("test", props));

        Binder binder = new Binder(ConfigurationPropertySources.from(propertySources));
        KafkaConsumerProperties bound = binder.bind("machine.kafka.consumer", KafkaConsumerProperties.class).orElseGet(KafkaConsumerProperties::new);

        assertTrue(bound.getEnabled());
        assertEquals("machine-group", bound.getGroupId());
        assertEquals(4, bound.getConcurrency());
        assertEquals(List.of("topic-a", "topic-b"), bound.getTopics());
        assertTrue(bound.getIdempotency().getEnabled());
        assertEquals("kafka:idempotent:", bound.getIdempotency().getKeyPrefix());
    }
}
