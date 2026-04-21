# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Test Commands

```bash
# Run all tests
mvn test

# Run specific test
mvn test -Dtest=ClassName

# Run integration test
mvn test -Dtest=KafkaToIoTDBRealFlowIntegrationTest
mvn test -Dtest=IoTDBRealCrudIntegrationTest

# Build
mvn clean package
```

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     MachineData-Service                         │
├─────────────────────────────────────────────────────────────────┤
│  Kafka (test-topic)                                             │
│      │                                                         │
│      ▼                                                         │
│  MachineDataKafkaConsumer ─────► IoTDB (time-series storage)   │
│      │                              root.test.channel_XX        │
│      ▼                                                         │
│  RedisMessageIdempotencyService                                │
│  (prevents duplicate message processing)                        │
│                                                                 │
│  REST API (port 8054)                                          │
│      └── /iotdb/write/single, /iotdb/query/single, etc.        │
└─────────────────────────────────────────────────────────────────┘
```

## Core Components

### Kafka Consumer Pipeline
- **Entry**: `MachineDataKafkaConsumer` listens to `test-topic`
- **Template**: `AbstractKafkaConsumerTemplate` handles idempotency & acknowledgment
- **Business Logic**: Parses `ChannelEntity`, writes channels to IoTDB as separate devices
- **Idempotency**: Redis-backed `RedisMessageIdempotencyService` (or `NoOpMessageIdempotencyService` when Redis disabled)

### IoTDB Data Model
- **Database**: `root.test`
- **Device Pattern**: `root.test.channel_XX` (where XX is 01-64)
- **Measurement**: `value` (DOUBLE)
- **Timestamp**: Parsed from Kafka message time field

### Key Files
| File | Purpose |
|------|---------|
| `MachineDataKafkaConsumer.java` | Kafka listener → IoTDB writer |
| `AbstractKafkaConsumerTemplate.java` | Idempotency + ack logic |
| `IoTDBRepository.java` | Low-level IoTDB session operations |
| `IoTDBController.java` | REST API for IoTDB CRUD |

## Configuration

### Kafka Consumer (application.yml)
```yaml
machine:
  kafka:
    consumer:
      enabled: true              # Enable/disable consumer
      concurrency: 1             # Listener threads
      topics: [test-topic]       # Topics to subscribe
      idempotency:
        enabled: true
        key-prefix: "kafka:idempotent:"
        processing-ttl-seconds: 600
        done-ttl-hours: 72
```

### Redis (optional)
```yaml
machine:
  redis:
    enabled: true                # Required for idempotency
    address: redis://host:6379
```

### IoTDB
```yaml
iotdb:
  host: 10.1.40.171
  port: 6667
  username: root
  password: root
  database: root.test
```

## Conditional Beans

Redis-dependent beans use `@ConditionalOnProperty(matchIfMissing = false)`:
- `RedisMessageIdempotencyService` - only created when `machine.redis.enabled=true`
- `RedissonRedisCacheTemplate` - only created when `machine.redis.enabled=true`
- `RedissonClient` - only created when `machine.redis.enabled=true`

When testing without Redis, set `machine.redis.enabled=false` and `machine.kafka.consumer.idempotency.enabled=false`.

## Integration Tests

| Test | Purpose |
|------|---------|
| `KafkaToIoTDBRealFlowIntegrationTest` | End-to-end: Kafka → IoTDB verification |
| `KafkaLive200SpeedIntegrationTest` | Throughput test (500 messages) |
| `IoTDBRealCrudIntegrationTest` | IoTDB CRUD operations |
| `IoTDBKafkaDataQueryTest` | Query specific Kafka timestamp in IoTDB |

## Claude Code Skill

- Repo-local skill: `/alibaba-review`
- Use it to review pending changes, a specific file, or a repository area against Alibaba-inspired development practices adapted for this project.
- The skill is review-oriented, not code-generation-oriented.
- Its guidance is based on Alibaba-inspired coding themes plus repository context; it is not a verbatim restatement of any official document.
- It should prioritize correctness, safety, observability, maintainability, and test completeness over style-only suggestions.
