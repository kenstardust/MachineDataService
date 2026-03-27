package com.industry.Kafka.integration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.junit.jupiter.api.Test;

import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class KafkaConnectivitySmokeTest {

    private static final String BOOTSTRAP_SERVERS = "10.1.40.171:9092";
    private static final String TARGET_TOPIC = "test-topic";

    @Test
    void shouldConnectToKafkaBrokerAndSeeTopic() {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", BOOTSTRAP_SERVERS);

        // 建议补上超时配置，避免默认值不清晰
        properties.put("request.timeout.ms", "5000");
        properties.put("default.api.timeout.ms", "5000");
        properties.put("connections.max.idle.ms", "10000");

        try (AdminClient adminClient = AdminClient.create(properties)) {
            System.out.println("[KAFKA SMOKE] trying to connect, bootstrapServers=" + BOOTSTRAP_SERVERS);

            DescribeClusterResult describeCluster = adminClient.describeCluster();
            String clusterId = describeCluster.clusterId().get(5, TimeUnit.SECONDS);
            assertNotNull(clusterId, "Connected to Kafka but clusterId is null");

            Set<String> topics = adminClient.listTopics().names().get(5, TimeUnit.SECONDS);
            System.out.println("[KAFKA SMOKE] topics=" + topics);

            assertTrue(topics.contains(TARGET_TOPIC),
                    "Kafka connected, but topic not found: " + TARGET_TOPIC);

            System.out.println("[KAFKA SMOKE] connected=true");
            System.out.println("[KAFKA SMOKE] clusterId=" + clusterId);
            System.out.println("[KAFKA SMOKE] topicExists(" + TARGET_TOPIC + ")=true");

        } catch (Exception e) {
            e.printStackTrace();
            fail("Kafka smoke test failed: " + e.getClass().getName() + " - " + e.getMessage());
        }
    }
}