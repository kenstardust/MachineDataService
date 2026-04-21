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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final int TARGET_MESSAGES = 500;
    private static final long MAX_WAIT_MS = 600000;
    private static final int SAMPLE_INTERVAL = 30;
    private static final long IOTDB_QUERY_WINDOW_MS = 50000;
    private static final int IOTDB_RETRY = 10;
    private static final double VALUE_EPSILON = 1e-6;
    private static final DateTimeFormatter KAFKA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IoTDBDataService ioTDBDataService;

    @Test
    void shouldReadLive200MessagesAndReportSpeed() throws InterruptedException {
        LiveReadResult readResult = readLiveMessagesAndCollectSamples();

        SamplingResult samplingResult = new SamplingResult(0, 0, 0);
        long verifyElapsedMs = 0;
        if (readResult.readCount() >= TARGET_MESSAGES) {
            long verifyStartMs = System.currentTimeMillis();
            samplingResult = verifySamplesAfterRead(readResult.sampledMessages());
            verifyElapsedMs = System.currentTimeMillis() - verifyStartMs;
        }

        double readElapsedSeconds = readResult.readElapsedMs() / 1000.0;
        double readSpeed = readElapsedSeconds > 0 ? readResult.readCount() / readElapsedSeconds : 0;

        System.out.println("[KAFKA LIVE200] topic=" + TOPIC);
        System.out.println("[KAFKA LIVE200] target=" + TARGET_MESSAGES + ", readCount=" + readResult.readCount());
        System.out.println("[KAFKA LIVE200] readElapsedMs=" + readResult.readElapsedMs());
        System.out.println("[KAFKA LIVE200] readSpeedMsgPerSec=" + String.format("%.2f", readSpeed));
        System.out.println("[KAFKA LIVE200] verifyElapsedMs=" + verifyElapsedMs);
        System.out.println("[KAFKA LIVE200] sampleChecks=" + samplingResult.sampleChecks()
                + ", samplePass=" + samplingResult.samplePass()
                + ", sampleFail=" + samplingResult.sampleFail());

        assertTrue(readResult.readCount() == TARGET_MESSAGES,
                "Did not read 200 live messages within timeout, read=" + readResult.readCount());
        assertTrue(samplingResult.sampleFail() == 0,
                "Some sampled messages are not queryable in IoTDB, sampleFail=" + samplingResult.sampleFail());
    }

    private LiveReadResult readLiveMessagesAndCollectSamples() throws InterruptedException {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "10.1.40.171:9092");
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "testMachineDataService");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        long readCount = 0;
        long firstReadMs = -1;
        long endMs = -1;
        List<SampledMessage> sampledMessages = new ArrayList<>();

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
                        SampledMessage sampledMessage = buildRandomSample(record, message);
                        if (sampledMessage != null) {
                            int maxChannelIndex = maxChannelIndex(message.getChannels());
                            System.out.println("[KAFKA LIVE200] sampleCaptured partition=" + record.partition()
                                    + ", offset=" + record.offset()
                                    + ", channelKeyCount=" + message.getChannels().size()
                                    + ", maxChannelIndex=" + maxChannelIndex
                                    + ", sampledChannel=" + channelKey(sampledMessage.sampledChannelIndex())
                                    + ", sampledValue=" + sampledMessage.sampledChannelValue());
                            sampledMessages.add(sampledMessage);
                        }
                    }

                    if (readCount >= TARGET_MESSAGES) {
                        endMs = System.currentTimeMillis();
                        break;
                    }
                }
            }

            if (readCount >= TARGET_MESSAGES) {
                consumer.commitSync();
            }
        }

        long readElapsedMs = (firstReadMs >= 0 && endMs >= 0) ? (endMs - firstReadMs) : 0;
        return new LiveReadResult(readCount, readElapsedMs, sampledMessages);
    }

    private SamplingResult verifySamplesAfterRead(List<SampledMessage> sampledMessages) throws InterruptedException {
        long sampleChecks = sampledMessages.size();
        long samplePass = 0;
        long sampleFail = 0;

        for (SampledMessage sample : sampledMessages) {
            boolean channelOk = waitUntilPersisted(
                    channelDevice(sample.sampledChannelIndex()),
                    sample.timestamp(),
                    sample.sampledChannelValue());

            if (channelOk) {
                samplePass++;
            } else {
                sampleFail++;
                System.out.println("[KAFKA LIVE200] sampleCheckFail partition=" + sample.partition()
                        + ", offset=" + sample.offset()
                        + ", timestamp=" + sample.timestamp()
                        + ", failedChannel=" + channelKey(sample.sampledChannelIndex())
                        + ", expected=" + sample.sampledChannelValue());
            }
        }

        return new SamplingResult(sampleChecks, samplePass, sampleFail);
    }

    private ChannelEntity parseIfValid(String payload) {
        try {
            ChannelEntity message = objectMapper.readValue(payload, ChannelEntity.class);
            if (message.getTime() == null || message.getChannels() == null || message.getChannels().isEmpty()) {
                return null;
            }
            LocalDateTime.parse(message.getTime(), KAFKA_TIME_FORMAT);
            return message;
        } catch (Exception ex) {
            return null;
        }
    }

    private int maxChannelIndex(Map<String, Double> channels) {
        int max = 0;
        for (String key : channels.keySet()) {
            if (key.startsWith("chan")) {
                try {
                    int idx = Integer.parseInt(key.substring(4));
                    if (idx > max) {
                        max = idx;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return max;
    }

    private String channelKey(int channelIndex) {
        return "chan" + channelIndex;
    }

    private String channelDevice(int channelIndex) {
        return "root.test.channel_" + String.format("%02d", channelIndex);
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

    private SampledMessage buildRandomSample(ConsumerRecord<String, String> record, ChannelEntity message) {
        List<Map.Entry<String, Double>> candidates = message.getChannels().entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .filter(entry -> parseChannelIndex(entry.getKey()) != null)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        int randomIndex = ThreadLocalRandom.current().nextInt(candidates.size());
        Map.Entry<String, Double> selected = candidates.get(randomIndex);
        Integer channelIndex = parseChannelIndex(selected.getKey());
        if (channelIndex == null) {
            return null;
        }

        return new SampledMessage(
                record.partition(),
                record.offset(),
                parseKafkaTimestamp(message.getTime()),
                channelIndex,
                selected.getValue());
    }

    private Integer parseChannelIndex(String channelKey) {
        if (channelKey == null || !channelKey.startsWith("chan")) {
            return null;
        }
        try {
            return Integer.parseInt(channelKey.substring(4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record LiveReadResult(long readCount, long readElapsedMs, List<SampledMessage> sampledMessages) {
    }

    private record SampledMessage(int partition, long offset, long timestamp, int sampledChannelIndex, double sampledChannelValue) {
    }

    private record SamplingResult(long sampleChecks, long samplePass, long sampleFail) {
    }
}
