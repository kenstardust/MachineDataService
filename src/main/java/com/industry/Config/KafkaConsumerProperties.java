package com.industry.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "machine.kafka.consumer")
public class KafkaConsumerProperties {

    private Boolean enabled = false;
    private List<String> topics = new ArrayList<>();
    private Integer concurrency = 1;
    private String groupId;
    private Idempotency idempotency = new Idempotency();

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getTopics() {
        return topics;
    }

    public void setTopics(List<String> topics) {
        this.topics = topics;
    }

    public Integer getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(Integer concurrency) {
        this.concurrency = concurrency;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Idempotency getIdempotency() {
        return idempotency;
    }

    public void setIdempotency(Idempotency idempotency) {
        this.idempotency = idempotency;
    }

    public static class Idempotency {

        private Boolean enabled = true;
        private String keyPrefix = "kafka:idempotent:";
        private Long processingTtlSeconds = 600L;
        private Long doneTtlHours = 72L;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public Long getProcessingTtlSeconds() {
            return processingTtlSeconds;
        }

        public void setProcessingTtlSeconds(Long processingTtlSeconds) {
            this.processingTtlSeconds = processingTtlSeconds;
        }

        public Long getDoneTtlHours() {
            return doneTtlHours;
        }

        public void setDoneTtlHours(Long doneTtlHours) {
            this.doneTtlHours = doneTtlHours;
        }
    }
}
