package com.industry.Kafka.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.industry.Kafka.idempotency.MessageIdempotencyService;
import com.industry.Kafka.model.MachineKafkaMessage;
import com.industry.Kafka.template.AbstractKafkaConsumerTemplate;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "machine.kafka.consumer", name = "enabled", havingValue = "true")
public class MachineDataKafkaConsumer extends AbstractKafkaConsumerTemplate<MachineKafkaMessage> {

    private final ObjectMapper objectMapper;

    public MachineDataKafkaConsumer(MessageIdempotencyService idempotencyService, ObjectMapper objectMapper) {
        super(idempotencyService);
        this.objectMapper = objectMapper;
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
    protected MachineKafkaMessage deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, MachineKafkaMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Invalid kafka message payload", ex);
        }
    }

    @Override
    protected String extractMessageId(MachineKafkaMessage message, ConsumerRecord<String, String> record) {
        return message.getMessageId();
    }

    @Override
    protected void processBusiness(MachineKafkaMessage message, ConsumerRecord<String, String> record) {
        // 业务逻辑入口，当前版本仅保留模板流程
    }
}
