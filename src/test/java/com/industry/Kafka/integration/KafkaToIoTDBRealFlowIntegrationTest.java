package com.industry.Kafka.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industry.entity.ChannelEntity;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.model.response.QueryResponse;
import com.industry.iotdb.service.IoTDBDataService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "spring.kafka.bootstrap-servers=10.1.40.171:9092",
        "spring.kafka.consumer.group-id=testMachineDataService",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "machine.kafka.consumer.enabled=true",
        "machine.kafka.consumer.group-id=testMachineDataService",
        "machine.kafka.consumer.concurrency=1",
        "machine.kafka.consumer.topics[0]=test-topic",
        "machine.kafka.consumer.idempotency.enabled=false",
        "machine.kafka.consumer.idempotency.key-prefix=kafka:idempotent:realtest:",
        "machine.kafka.consumer.idempotency.processing-ttl-seconds=600",
        "machine.kafka.consumer.idempotency.done-ttl-hours=72",
        "machine.redis.enabled=false",
        "iotdb.host=10.1.40.171",
        "iotdb.port=6667",
        "iotdb.username=root",
        "iotdb.password=root",
        "iotdb.database=root.test"
})
class KafkaToIoTDBRealFlowIntegrationTest {

    private static final String TOPIC = "test-topic";
    private static final String CONTINUOUS_LISTENER_GROUP = "testMachineDataService-continuous";
    private static final String DEVICE_CHANNEL_01 = "root.test.channel_01";
    private static final String DEVICE_CHANNEL_24 = "root.test.channel_24";
    private static final String DEVICE_CHANNEL_48 = "root.test.channel_48";
    private static final DateTimeFormatter KAFKA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private static final long IOTDB_QUERY_WINDOW_MS = 2000;
    private static final int IOTDB_RETRY_PER_CANDIDATE = 6;
    private static final long IOTDB_RETRY_INTERVAL_MS = 1000;
    private static final double VALUE_EPSILON = 1e-6;

    private final CountDownLatch keepAlive = new CountDownLatch(1);
    private final AtomicReference<String> lastFailure = new AtomicReference<>();

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IoTDBDataService ioTDBDataService;

    @KafkaListener(
            topics = TOPIC,
            groupId = CONTINUOUS_LISTENER_GROUP,
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        try {
            ChannelEntity message = parseIfValidChannelMessage(record.value());
            if (message == null) {
                logInvalidPayload(record);
                return;
            }

            long timestamp = parseKafkaTimestamp(message.getTime());
            if (!waitUntilPersisted(DEVICE_CHANNEL_01, timestamp, message.getChannels().get("chan1"))) {
                logPersistFailure(record, timestamp, DEVICE_CHANNEL_01);
                return;
            }
            if (!waitUntilPersisted(DEVICE_CHANNEL_24, timestamp, message.getChannels().get("chan24"))) {
                logPersistFailure(record, timestamp, DEVICE_CHANNEL_24);
                return;
            }
            if (!waitUntilPersisted(DEVICE_CHANNEL_48, timestamp, message.getChannels().get("chan48"))) {
                logPersistFailure(record, timestamp, DEVICE_CHANNEL_48);
                return;
            }

            acknowledgment.acknowledge();
            lastFailure.set(null);
            logVerificationSuccess(record, message);
        } catch (Exception ex) {
            logVerificationException(record, ex);
        }
    }

    @Test
    void shouldKeepListeningUntilManuallyStopped() throws InterruptedException {
        System.out.println("[TEST] KafkaToIoTDBRealFlowIntegrationTest is running in continuous mode.");
        System.out.println("[TEST] Success means a Kafka message has already been persisted to IoTDB.");
        System.out.println("[TEST] Stop the program manually when you want to end monitoring.");
        keepAlive.await();
    }

    private void logInvalidPayload(ConsumerRecord<String, String> record) {
        String failure = "Kafka message is not a valid channel payload, offset=" + record.offset();
        lastFailure.set(failure);
        System.out.println("[KAFKA->IOTDB FAILED] " + failure);
    }

    private void logPersistFailure(ConsumerRecord<String, String> record, long timestamp, String device) {
        String failure = "Kafka consumed but IoTDB missing " + device
                + ", offset=" + record.offset()
                + ", timestampMs=" + timestamp;
        lastFailure.set(failure);
        System.out.println("[KAFKA->IOTDB FAILED] " + failure);
    }

    private void logVerificationSuccess(ConsumerRecord<String, String> record, ChannelEntity message) {
        System.out.println("[KAFKA->IOTDB VERIFIED] topic=" + record.topic()
                + ", partition=" + record.partition()
                + ", offset=" + record.offset()
                + ", time=" + message.getTime()
                + ", devices=" + DEVICE_CHANNEL_01 + "," + DEVICE_CHANNEL_24 + "," + DEVICE_CHANNEL_48);
    }

    private void logVerificationException(ConsumerRecord<String, String> record, Exception ex) {
        String failure = "Kafka consumed but IoTDB verification failed at offset=" + record.offset() + ": " + ex.getMessage();
        lastFailure.set(failure);
        System.out.println("[KAFKA->IOTDB FAILED] " + failure);
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
}
