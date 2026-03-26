package com.industry.Kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.industry.Config.IoTDBProperties;
import com.industry.entity.ChannelEntity;
import com.industry.Kafka.idempotency.MessageIdempotencyService;
import com.industry.Kafka.template.AbstractKafkaConsumerTemplate;
import com.industry.iotdb.model.dto.IoTDBField;
import com.industry.iotdb.model.dto.IoTDBRecord;
import com.industry.iotdb.repository.IoTDBRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "machine.kafka.consumer", name = "enabled", havingValue = "true")
public class MachineDataKafkaConsumer extends AbstractKafkaConsumerTemplate<ChannelEntity> {

    private static final int MIN_CHANNEL_INDEX = 1;
    private static final int MAX_CHANNEL_INDEX = 64;
    private static final Pattern CHANNEL_KEY_PATTERN = Pattern.compile("^chan(\\d+)$");
    private static final DateTimeFormatter KAFKA_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");

    private final ObjectMapper objectMapper;
    private final IoTDBRepository ioTDBRepository;
    private final IoTDBProperties ioTDBProperties;

    public MachineDataKafkaConsumer(
            MessageIdempotencyService idempotencyService,
            ObjectMapper objectMapper,
            IoTDBRepository ioTDBRepository,
            IoTDBProperties ioTDBProperties) {
        super(idempotencyService);
        this.objectMapper = objectMapper;
        this.ioTDBRepository = ioTDBRepository;
        this.ioTDBProperties = ioTDBProperties;
    }

    @KafkaListener(
            id = "machineDataKafkaConsumer",
            topics = "#{@machineKafkaTopics}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listen(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        onMessage(record, acknowledgment);
    }

    @Override
    protected ChannelEntity deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, ChannelEntity.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid kafka message payload", ex);
        }
    }

    @Override
    protected String extractMessageId(ChannelEntity message, ConsumerRecord<String, String> record) {
        if (StringUtils.hasText(message.getMessageId())) {
            return message.getMessageId();
        }
        return message.fallbackMessageId();
    }

    @Override
    protected void processBusiness(ChannelEntity message, ConsumerRecord<String, String> record) {
        long timestamp = parseKafkaTime(message.getTime());
        List<IoTDBRecord> records = buildChannelRecords(message.getChannels(), timestamp);
        ioTDBRepository.insertRecords(records);
    }

    private String resolveDevicePrefix() {
        if (StringUtils.hasText(ioTDBProperties.getDatabase())) {
            return ioTDBProperties.getDatabase();
        }
        return "root.test";
    }

    private long parseKafkaTime(String time) {
        if (!StringUtils.hasText(time)) {
            throw new IllegalArgumentException("Kafka message time is required");
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(time, KAFKA_TIME_FORMAT);
            return localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Invalid kafka message time format: " + time, ex);
        }
    }

    private List<IoTDBRecord> buildChannelRecords(Map<String, Double> channels, long timestamp) {
        if (channels == null || channels.isEmpty()) {
            throw new IllegalArgumentException("Kafka channels payload is required");
        }
        String devicePrefix = resolveDevicePrefix();
        return channels.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> parseChannelIndex(entry.getKey())))
                .map(entry -> toRecord(devicePrefix, timestamp, entry.getKey(), entry.getValue()))
                .toList();
    }

    private IoTDBRecord toRecord(String devicePrefix, long timestamp, String channelKey, Double channelValue) {
        int channelIndex = parseChannelIndex(channelKey);
        IoTDBRecord record = new IoTDBRecord();
        record.setDevice(devicePrefix + "." + String.format("channel_%02d", channelIndex));
        record.setTimestamp(timestamp);
        record.setFields(List.of(valueField(channelValue)));
        return record;
    }

    private IoTDBField valueField(Double value) {
        IoTDBField field = new IoTDBField();
        field.setMeasurement("value");
        field.setDataType("DOUBLE");
        field.setValue(value);
        return field;
    }

    private int parseChannelIndex(String channelKey) {
        Matcher matcher = CHANNEL_KEY_PATTERN.matcher(channelKey);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid channel key: " + channelKey + ", expected format chanN");
        }
        int channelIndex = Integer.parseInt(matcher.group(1));
        if (channelIndex < MIN_CHANNEL_INDEX || channelIndex > MAX_CHANNEL_INDEX) {
            throw new IllegalArgumentException("Channel index out of range: " + channelKey + ", allowed chan1-chan64");
        }
        return channelIndex;
    }
}
