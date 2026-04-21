package com.industry.Kafka.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industry.entity.ChannelEntity;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.model.response.QueryResponse;
import com.industry.iotdb.service.IoTDBDataService;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "spring.kafka.bootstrap-servers=10.1.40.171:9092",
        "spring.kafka.consumer.group-id=testMachineDataService",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "machine.kafka.consumer.enabled=false",
        "machine.kafka.consumer.group-id=testMachineDataService",
        "machine.kafka.consumer.concurrency=1",
        "machine.kafka.consumer.topics[0]=test-topic",
        "machine.kafka.consumer.idempotency.enabled=true",
        "machine.kafka.consumer.idempotency.key-prefix=kafka:idempotent:realtest:",
        "machine.kafka.consumer.idempotency.processing-ttl-seconds=600",
        "machine.kafka.consumer.idempotency.done-ttl-hours=72",
        "machine.redis.enabled=true",
        "machine.redis.address=redis://10.1.40.171:6379",
        "iotdb.host=10.1.40.171",
        "iotdb.port=6667",
        "iotdb.username=root",
        "iotdb.password=root",
        "iotdb.database=root.test"
})
class KafkaTimestampTwoChannelIntegrationTest {

    private static final String TOPIC = "test-topic";
    private static final DateTimeFormatter KAFKA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private static final long KAFKA_SCAN_TIMEOUT_MS = 90000;
    private static final long IOTDB_QUERY_WINDOW_MS = 2000;
    private static final int IOTDB_RETRY = 4;
    private static final double VALUE_EPSILON = 1e-6;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IoTDBDataService ioTDBDataService;

    @Test
    void shouldReadExistingKafkaTimeAndVerifyTwoChannelsInIoTDB() throws InterruptedException {
        VerifiedCandidate verified = readAndVerifyTwoChannels();
        assertNotNull(verified, "No persisted Kafka message found for chan1/chan24 within timeout");

        System.out.println("[KAFKA TIME TEST] topic=" + TOPIC
                + ", partition=" + verified.record().partition()
                + ", offset=" + verified.record().offset());
        System.out.println("[KAFKA TIME TEST] kafkaWriteTime=" + verified.message().getTime()
                + ", timestampMs=" + verified.timestampMs());
        System.out.println("[KAFKA TIME TEST] sampledChannels chan1=" + verified.chan1()
                + ", chan24=" + verified.chan24());
        System.out.println("[KAFKA TIME TEST] iotdbDevices=root.test.channel_01,root.test.channel_24");
    }

    private VerifiedCandidate readAndVerifyTwoChannels() throws InterruptedException {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.1.40.171:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "testMachineDataService");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + KAFKA_SCAN_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    ChannelEntity message = parseIfValid(record.value());
                    if (message == null) {
                        continue;
                    }

                    long timestamp = parseKafkaTimestamp(message.getTime());
                    double chan1 = message.getChannels().get("chan1");
                    double chan24 = message.getChannels().get("chan24");

                    boolean channel01Ok = waitUntilPersisted("root.test.channel_01", timestamp, chan1);
                    boolean channel24Ok = waitUntilPersisted("root.test.channel_24", timestamp, chan24);
                    if (channel01Ok && channel24Ok) {
                        return new VerifiedCandidate(record, message, timestamp, chan1, chan24);
                    }
                }
            }
        }
        return null;
    }

    private ChannelEntity parseIfValid(String payload) {
        try {
            ChannelEntity message = objectMapper.readValue(payload, ChannelEntity.class);
            if (message.getTime() == null || message.getChannels() == null) {
                return null;
            }
            LocalDateTime.parse(message.getTime(), KAFKA_TIME_FORMAT);
            if (!message.getChannels().containsKey("chan1") || !message.getChannels().containsKey("chan24")) {
                return null;
            }
            return message;
        } catch (Exception ex) {
            return null;
        }
    }

    private long parseKafkaTimestamp(String time) {
        return LocalDateTime.parse(time, KAFKA_TIME_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private boolean waitUntilPersisted(String device, long timestamp, double expected) throws InterruptedException {
        int retry = IOTDB_RETRY;
        while (retry-- > 0) {
            QueryResponse response = ioTDBDataService.querySingle(buildQuery(device, timestamp));
            if (response.isSuccess() && response.getCount() > 0 && containsValue(response.getRows(), expected)) {
                return true;
            }
            Thread.sleep(1000);
        }
        return false;
    }

    private QueryRequest buildQuery(String device, long timestamp) {
        QueryRequest request = new QueryRequest();
        request.setDevice(device);
        request.setMeasurements(List.of("value"));
        request.setStartTime(timestamp - IOTDB_QUERY_WINDOW_MS);
        request.setEndTime(timestamp + IOTDB_QUERY_WINDOW_MS);
        request.setLimit(200);
        request.setOffset(0);
        return request;
    }

    private boolean containsValue(List<Map<String, Object>> rows, double expected) {
        return rows.stream().anyMatch(row -> row.entrySet().stream()
                .filter(entry -> !"Time".equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .anyMatch(number -> Math.abs(number.doubleValue() - expected) < VALUE_EPSILON));
    }

    private record VerifiedCandidate(
            ConsumerRecord<String, String> record,
            ChannelEntity message,
            long timestampMs,
            double chan1,
            double chan24
    ) {
    }
}
