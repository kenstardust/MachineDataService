package com.industry.Kafka.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industry.Config.KafkaConsumerProperties;
import com.industry.entity.ChannelEntity;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.model.response.QueryResponse;
import com.industry.iotdb.service.IoTDBDataService;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "spring.kafka.bootstrap-servers=10.1.40.171:9092",
        "spring.kafka.consumer.group-id=MachineData-Service-drain-${random.uuid}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "machine.kafka.consumer.enabled=true",
        "machine.kafka.consumer.group-id=MachineData-Service-drain-${random.uuid}",
        "machine.kafka.consumer.concurrency=1",
        "machine.kafka.consumer.topics[0]=test-topic",
        "machine.kafka.consumer.idempotency.enabled=true",
        "machine.kafka.consumer.idempotency.key-prefix=kafka:idempotent:drain:${random.uuid}:",
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
class KafkaBacklogDrainBenchmarkIntegrationTest {

    private static final String TOPIC = "test-topic";
    private static final DateTimeFormatter KAFKA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
    private static final long MAX_BENCHMARK_MS = 600000;
    private static final long CHECK_INTERVAL_MS = 1000;
    private static final int REQUIRED_CONSECUTIVE_DRAIN_CHECKS = 3;
    private static final int SAMPLE_EVERY_N_MESSAGES = 200;
    private static final long IOTDB_QUERY_WINDOW_MS = 2000;
    private static final int IOTDB_RETRY = 3;
    private static final double VALUE_EPSILON = 1e-6;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IoTDBDataService ioTDBDataService;

    @Autowired
    private KafkaConsumerProperties kafkaConsumerProperties;

    @Autowired
    private RedissonClient redissonClient;

    @Test
    void shouldDrainBacklogAndReportWriteSpeed() throws Exception {
        String groupId = kafkaConsumerProperties.getGroupId();
        String idempotencyPrefix = kafkaConsumerProperties.getIdempotency().getKeyPrefix();

        Map<TopicPartition, Long> endOffsets = readEndOffsets(TOPIC);
        Map<TopicPartition, Long> committedOffsetsAtStart = readGroupCommittedOffsets(groupId);
        long backlogCount = calculateBacklogCount(endOffsets, committedOffsetsAtStart);

        assertTrue(backlogCount > 0, "No backlog found in topic window at benchmark start");

        long startMillis = System.currentTimeMillis();

        SamplingStats samplingStats = runSamplingChecks(TOPIC, committedOffsetsAtStart, endOffsets);
        DrainStats drainStats = waitUntilDrained(groupId, endOffsets, startMillis);

        long elapsedMs = System.currentTimeMillis() - startMillis;
        long writtenCount = countDoneKeys(idempotencyPrefix);
        double throughput = elapsedMs > 0 ? writtenCount * 1000.0 / elapsedMs : 0;

        System.out.println("[KAFKA BENCH] topic=" + TOPIC);
        System.out.println("[KAFKA BENCH] groupId=" + groupId);
        System.out.println("[KAFKA BENCH] idempotencyPrefix=" + idempotencyPrefix);
        System.out.println("[KAFKA BENCH] backlogCountAtStart=" + backlogCount);
        System.out.println("[KAFKA BENCH] drained=" + drainStats.drained());
        System.out.println("[KAFKA BENCH] finalLag=" + drainStats.finalLag());
        System.out.println("[KAFKA BENCH] writtenCount=" + writtenCount);
        System.out.println("[KAFKA BENCH] elapsedMs=" + elapsedMs);
        System.out.println("[KAFKA BENCH] throughputMsgPerSec=" + String.format("%.2f", throughput));
        System.out.println("[KAFKA BENCH] sampleAttempts=" + samplingStats.sampleAttempts()
                + ", samplePass=" + samplingStats.samplePass()
                + ", sampleFail=" + samplingStats.sampleFail());

        assertTrue(drainStats.drained(), "Backlog was not drained before timeout");
        assertTrue(drainStats.finalLag() == 0, "Backlog lag should be zero after drain");
        assertTrue(writtenCount > 0, "No messages were marked done in Redis idempotency keys");
        assertTrue(samplingStats.sampleFail() == 0, "Some sampled channel values were not queryable in IoTDB");
    }

    private Map<TopicPartition, Long> readEndOffsets(String topic) {
        Properties properties = kafkaProperties();
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "benchmark-endoffset-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            consumer.poll(Duration.ofMillis(1000));
            Set<TopicPartition> assignment = new HashSet<>(consumer.assignment());
            if (assignment.isEmpty()) {
                consumer.poll(Duration.ofMillis(1000));
                assignment = new HashSet<>(consumer.assignment());
            }
            return consumer.endOffsets(assignment);
        }
    }

    private SamplingStats runSamplingChecks(String topic,
                                            Map<TopicPartition, Long> committedOffsetsAtStart,
                                            Map<TopicPartition, Long> endOffsets) throws InterruptedException {
        Properties properties = kafkaProperties();
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "benchmark-sampler-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

        long consumedInWindow = 0;
        long sampleAttempts = 0;
        long samplePass = 0;
        long sampleFail = 0;

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.currentTimeMillis() + MAX_BENCHMARK_MS;

            while (System.currentTimeMillis() < deadline && consumedInWindow < calculateBacklogCount(endOffsets, committedOffsetsAtStart)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                for (ConsumerRecord<String, String> record : records) {
                    TopicPartition tp = new TopicPartition(record.topic(), record.partition());
                    long startOffset = committedOffsetsAtStart.getOrDefault(tp, 0L);
                    long endOffset = endOffsets.getOrDefault(tp, 0L);

                    if (record.offset() < startOffset || record.offset() >= endOffset) {
                        continue;
                    }

                    consumedInWindow++;
                    if (consumedInWindow % SAMPLE_EVERY_N_MESSAGES != 0) {
                        continue;
                    }

                    ChannelEntity message = parseIfValidForSample(record.value());
                    if (message == null) {
                        continue;
                    }

                    sampleAttempts++;
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
            }
        }

        return new SamplingStats(sampleAttempts, samplePass, sampleFail);
    }

    private DrainStats waitUntilDrained(String groupId,
                                        Map<TopicPartition, Long> endOffsets,
                                        long startMillis) throws Exception {
        int consecutiveDrainChecks = 0;
        while (System.currentTimeMillis() - startMillis < MAX_BENCHMARK_MS) {
            Map<TopicPartition, Long> committed = readGroupCommittedOffsets(groupId);
            long finalLag = calculateLag(endOffsets, committed);

            if (finalLag == 0) {
                consecutiveDrainChecks++;
                if (consecutiveDrainChecks >= REQUIRED_CONSECUTIVE_DRAIN_CHECKS) {
                    return new DrainStats(true, 0);
                }
            } else {
                consecutiveDrainChecks = 0;
            }

            Thread.sleep(CHECK_INTERVAL_MS);
        }

        Map<TopicPartition, Long> committed = readGroupCommittedOffsets(groupId);
        return new DrainStats(false, calculateLag(endOffsets, committed));
    }

    private Map<TopicPartition, Long> readGroupCommittedOffsets(String groupId) throws Exception {
        Properties adminProperties = new Properties();
        adminProperties.put("bootstrap.servers", "10.1.40.171:9092");

        try (AdminClient adminClient = AdminClient.create(adminProperties)) {
            ListConsumerGroupOffsetsResult result = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, Long> offsets = new HashMap<>();
            result.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS)
                    .forEach((tp, meta) -> offsets.put(tp, meta.offset()));
            return offsets;
        }
    }

    private long calculateBacklogCount(Map<TopicPartition, Long> endOffsets,
                                       Map<TopicPartition, Long> committedOffsetsAtStart) {
        long sum = 0;
        for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
            long committed = committedOffsetsAtStart.getOrDefault(entry.getKey(), 0L);
            long end = entry.getValue();
            if (end > committed) {
                sum += (end - committed);
            }
        }
        return sum;
    }

    private long calculateLag(Map<TopicPartition, Long> endOffsets,
                              Map<TopicPartition, Long> committedOffsets) {
        long lag = 0;
        for (Map.Entry<TopicPartition, Long> entry : endOffsets.entrySet()) {
            long committed = committedOffsets.getOrDefault(entry.getKey(), 0L);
            long end = entry.getValue();
            if (end > committed) {
                lag += (end - committed);
            }
        }
        return lag;
    }

    private long countDoneKeys(String idempotencyPrefix) {
        List<String> keys = new ArrayList<>();
        redissonClient.getKeys().getKeysByPattern(idempotencyPrefix + "*:done")
                .forEach(keys::add);
        return keys.size();
    }

    private ChannelEntity parseIfValidForSample(String payload) {
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

    private Properties kafkaProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.1.40.171:9092");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        return properties;
    }

    private record SamplingStats(long sampleAttempts, long samplePass, long sampleFail) {
    }

    private record DrainStats(boolean drained, long finalLag) {
    }
}
