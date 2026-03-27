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

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "spring.kafka.bootstrap-servers=10.1.40.171:9092",
        "spring.kafka.consumer.group-id=MachineData-Service-live200-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=latest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "machine.kafka.consumer.enabled=true",
        "machine.kafka.consumer.concurrency=1",
        "machine.kafka.consumer.topics[0]=test-topic",
        "machine.kafka.consumer.idempotency.enabled=true",
        "machine.kafka.consumer.idempotency.key-prefix=kafka:idempotent:live200:${random.uuid}:",
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
class KafkaLive200SpeedIntegrationTest {

    private static final String TOPIC = "test-topic";
    private static final int TARGET_MESSAGES = 200;
    private static final long MAX_WAIT_MS = 600000;
    private static final int SAMPLE_INTERVAL = 50;
    private static final long IOTDB_QUERY_WINDOW_MS = 2000;
    private static final int IOTDB_RETRY = 3;
    private static final double VALUE_EPSILON = 1e-6;
    private static final DateTimeFormatter KAFKA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IoTDBDataService ioTDBDataService;

    @Test
    void shouldReadLive200MessagesAndReportSpeed() throws InterruptedException {
        LiveSpeedStats stats = readLiveMessagesAndMeasure();

        double elapsedSeconds = stats.elapsedMs() / 1000.0;
        double speed = elapsedSeconds > 0 ? stats.readCount() / elapsedSeconds : 0;

        System.out.println("[KAFKA LIVE200] topic=" + TOPIC);
        System.out.println("[KAFKA LIVE200] target=" + TARGET_MESSAGES + ", readCount=" + stats.readCount());
        System.out.println("[KAFKA LIVE200] elapsedMs=" + stats.elapsedMs());
        System.out.println("[KAFKA LIVE200] speedMsgPerSec=" + String.format("%.2f", speed));
        System.out.println("[KAFKA LIVE200] sampleChecks=" + stats.sampleChecks()
                + ", samplePass=" + stats.samplePass()
                + ", sampleFail=" + stats.sampleFail());

        assertTrue(stats.readCount() == TARGET_MESSAGES,
                "Did not read 200 live messages within timeout, read=" + stats.readCount());
        assertTrue(stats.sampleFail() == 0,
                "Some sampled messages are not queryable in IoTDB, sampleFail=" + stats.sampleFail());
    }

    private LiveSpeedStats readLiveMessagesAndMeasure() throws InterruptedException {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.1.40.171:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "inspector-live200-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        long readCount = 0;
        long sampleChecks = 0;
        long samplePass = 0;
        long sampleFail = 0;
        long firstReadMs = -1;
        long endMs = -1;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC));
            long deadline = System.currentTimeMillis() + MAX_WAIT_MS;

            while (System.currentTimeMillis() < deadline && readCount < TARGET_MESSAGES) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    ChannelEntity message = parseIfValid(record.value());
                    if (message == null) {
                        continue;
                    }

                    if (firstReadMs < 0) {
                        firstReadMs = System.currentTimeMillis();
                    }

                    readCount++;
                    if (readCount % SAMPLE_INTERVAL == 0) {
                        sampleChecks++;
                        long timestamp = parseKafkaTimestamp(message.getTime());
                        double chan1 = message.getChannels().get("chan1");
                        double chan24 = message.getChannels().get("chan24");

                        boolean channel01Ok = waitUntilPersisted("root.test.channel_01", timestamp, chan1);
                        boolean channel24Ok = waitUntilPersisted("root.test.channel_24", timestamp, chan24);
                        if (channel01Ok && channel24Ok) {
                            samplePass++;
                        } else {
                            sampleFail++;
                        }
                    }

                    if (readCount >= TARGET_MESSAGES) {
                        endMs = System.currentTimeMillis();
                        break;
                    }
                }
            }
        }

        long elapsedMs = (firstReadMs >= 0 && endMs >= 0) ? (endMs - firstReadMs) : 0;
        return new LiveSpeedStats(readCount, elapsedMs, sampleChecks, samplePass, sampleFail);
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

    private long parseKafkaTimestamp(String time) {
        return LocalDateTime.parse(time, KAFKA_TIME_FORMAT)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
    }

    private record LiveSpeedStats(long readCount, long elapsedMs, long sampleChecks, long samplePass, long sampleFail) {
    }
}
