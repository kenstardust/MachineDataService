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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "spring.kafka.bootstrap-servers=10.1.40.171:9092",
        "spring.kafka.consumer.group-id=MachineData-Service-real-read",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "machine.kafka.consumer.enabled=true",
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
class KafkaToIoTDBRealFlowIntegrationTest {

    private static final String TOPIC = "test-topic";
    private static final DateTimeFormatter KAFKA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IoTDBDataService ioTDBDataService;

    private static final long KAFKA_SCAN_TIMEOUT_MS = 120000;
    private static final long IOTDB_QUERY_WINDOW_MS = 2000;
    private static final int IOTDB_RETRY_PER_CANDIDATE = 6;
    private static final long IOTDB_RETRY_INTERVAL_MS = 1000;
    private static final double VALUE_EPSILON = 1e-6;

    @Test
    void shouldReadExistingKafkaMessageAndVerifyPersistedToIoTDB() throws Exception {
        ConsumedMessage consumed = readPersistedChannelMessageFromExistingQueue();
        assertNotNull(consumed, "No persisted channel json message found in topic test-topic within timeout");

        ConsumerRecord<String, String> existingRecord = consumed.record();
        ChannelEntity message = consumed.message();
        long timestamp = parseKafkaTimestamp(message.getTime());

        double chan1 = message.getChannels().get("chan1");
        double chan24 = message.getChannels().get("chan24");
        double chan48 = message.getChannels().get("chan48");

        System.out.println("[KAFKA REAL FLOW] consumedFromExistingQueue=true");
        System.out.println("[KAFKA REAL FLOW] topic=" + TOPIC + ", partition=" + existingRecord.partition() + ", offset=" + existingRecord.offset());
        System.out.println("[KAFKA REAL FLOW] kafkaKey=" + existingRecord.key());
        System.out.println("[KAFKA REAL FLOW] time=" + message.getTime() + ", timestampMs=" + timestamp);
        System.out.println("[KAFKA REAL FLOW] devices=root.test.channel_01,root.test.channel_24,root.test.channel_48");
        System.out.println("[KAFKA REAL FLOW] values chan1=" + chan1 + ", chan24=" + chan24 + ", chan48=" + chan48);
    }

    private ConsumedMessage readPersistedChannelMessageFromExistingQueue() throws InterruptedException {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.1.40.171:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "inspector-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        int inspectedValidMessages = 0;
        ConsumedMessage lastCandidate = null;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + KAFKA_SCAN_TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    System.out.println("[KAFKA DEBUG] polled topic=" + record.topic()
                            + ", partition=" + record.partition()
                            + ", offset=" + record.offset()
                            + ", key=" + record.key());
                    System.out.println("[KAFKA DEBUG] payload=" + truncate(record.value(), 500));

                    ChannelEntity message = parseIfValidChannelMessage(record.value());
                    if (message == null) {
                        System.out.println("[KAFKA DEBUG] skipped non-channel message at offset=" + record.offset());
                        continue;
                    }

                    inspectedValidMessages++;
                    lastCandidate = new ConsumedMessage(record, message);
                    long timestamp = parseKafkaTimestamp(message.getTime());
                    double chan1 = message.getChannels().get("chan1");
                    double chan24 = message.getChannels().get("chan24");
                    double chan48 = message.getChannels().get("chan48");

                    boolean channel01Ok = waitUntilPersisted("root.test.channel_01", timestamp, chan1);
                    boolean channel24Ok = waitUntilPersisted("root.test.channel_24", timestamp, chan24);
                    boolean channel48Ok = waitUntilPersisted("root.test.channel_48", timestamp, chan48);

                    if (channel01Ok && channel24Ok && channel48Ok) {
                        System.out.println("[KAFKA DEBUG] matched persisted channel message at offset=" + record.offset());
                        return lastCandidate;
                    }

                    System.out.println("[KAFKA DEBUG] candidate not persisted yet. offset=" + record.offset()
                            + ", timestampMs=" + timestamp
                            + ", channel01Ok=" + channel01Ok
                            + ", channel24Ok=" + channel24Ok
                            + ", channel48Ok=" + channel48Ok);
                }
            }
        }

        System.out.println("[KAFKA DEBUG] no persisted candidate found, inspectedValidMessages=" + inspectedValidMessages);
        if (lastCandidate != null) {
            long ts = parseKafkaTimestamp(lastCandidate.message().getTime());
            System.out.println("[KAFKA DEBUG] lastCandidate topic=" + TOPIC
                    + ", partition=" + lastCandidate.record().partition()
                    + ", offset=" + lastCandidate.record().offset()
                    + ", timestampMs=" + ts
                    + ", values chan1=" + lastCandidate.message().getChannels().get("chan1")
                    + ", chan24=" + lastCandidate.message().getChannels().get("chan24")
                    + ", chan48=" + lastCandidate.message().getChannels().get("chan48"));
        }

        return null;
    }

    private ChannelEntity parseIfValidChannelMessage(String payload) {
        try {
            ChannelEntity message = objectMapper.readValue(payload, ChannelEntity.class);
            if (message.getTime() == null || message.getChannels() == null) {
                return null;
            }
            LocalDateTime.parse(message.getTime(), KAFKA_TIME_FORMAT);
            if (!message.getChannels().containsKey("chan1")
                    || !message.getChannels().containsKey("chan24")
                    || !message.getChannels().containsKey("chan48")) {
                return null;
            }
            return message;
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean waitUntilPersisted(String device, long timestamp, double expected) throws InterruptedException {
        int retry = IOTDB_RETRY_PER_CANDIDATE;
        while (retry-- > 0) {
            QueryResponse response = ioTDBDataService.querySingle(buildQuery(device, timestamp));
            if (response.isSuccess() && response.getCount() > 0 && containsValue(response.getRows(), expected)) {
                return true;
            }
            Thread.sleep(IOTDB_RETRY_INTERVAL_MS);
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

    private long parseKafkaTimestamp(String time) {
        return LocalDateTime.parse(time, KAFKA_TIME_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen) + "...";
    }

    private record ConsumedMessage(ConsumerRecord<String, String> record, ChannelEntity message) {
    }
}
