### 你在 Nacos YAML 可这样配（可多 topic、可改并发）



```yaml
spring:
  kafka:
    bootstrap-servers: 10.1.40.171:9092
    consumer:
      enable-auto-commit: false

machine:
  kafka:
    consumer:
      enabled: true
      group-id: machine-data-group
      concurrency: 4
      topics:
        - machine.topic.a
        - machine.topic.b
      idempotency:
        enabled: true
        key-prefix: "kafka:idempotent:"
        processing-ttl-seconds: 600
        done-ttl-hours: 72
```