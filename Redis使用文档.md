# Redis 使用文档

## 1. 目标
本项目 Redis 访问已统一为模板接口 `RedisCacheTemplate`，默认实现为 Redisson：`RedissonRedisCacheTemplate`（Bean 名：`rediscache`）。

- 配置入口：`machine.redis.*`（建议在 Nacos YAML）
- 业务访问入口：`RedisCacheTemplate`
- Kafka 幂等已接入该模板：`RedisMessageIdempotencyService`

---

## 2. 配置说明（Nacos YAML）
配置前缀：`machine.redis`

```yaml
machine:
  redis:
    enabled: true
    address: redis://10.1.40.17:6379
    password: your_password   # 可选
    database: 0
    connection-pool-size: 64
    connection-minimum-idle-size: 10
    idle-connection-timeout: 10000
    connect-timeout: 10000
```

### 字段含义
- `enabled`: 是否启用 Redis（默认 `true`）
- `address`: Redis 地址，必须是 `redis://host:port` 格式
- `password`: 密码（可选）
- `database`: 数据库索引
- `connection-pool-size`: 连接池大小
- `connection-minimum-idle-size`: 最小空闲连接数
- `idle-connection-timeout`: 空闲连接超时（毫秒）
- `connect-timeout`: 建连超时（毫秒）

---

## 3. 代码结构

### 3.1 配置类
- `com.industry.Config.RedisProperties`
  - 绑定 `machine.redis.*`
- `com.industry.Config.RedisConfig`
  - 创建 `RedissonClient`（`destroyMethod = "shutdown"`）

### 3.2 模板接口
- `com.industry.Redis.template.RedisCacheTemplate`
  - `set(key, value, ttl)`
  - `get(key, type)`
  - `delete(key)`
  - `exists(key)`
  - `update(key, value, ttl)`（仅 key 存在时更新）
  - `setIfAbsent(key, value, ttl)`（原子写入，常用于幂等/锁）

### 3.3 Redisson 实现
- `com.industry.Redis.template.RedissonRedisCacheTemplate`
  - 基于 `RBucket`
  - 使用 `ObjectMapper` 做 JSON 序列化/反序列化
  - Bean 名称：`rediscache`

---

## 4. 业务使用方式

### 4.1 注入模板（推荐）
```java
import com.industry.Redis.template.RedisCacheTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.time.Duration;

@Service
public class DemoService {

    @Resource(name = "rediscache")
    private RedisCacheTemplate redisCacheTemplate;

    public void demo() {
        redisCacheTemplate.set("demo:key", "hello", Duration.ofMinutes(10));

        String value = redisCacheTemplate.get("demo:key", String.class);

        boolean exists = redisCacheTemplate.exists("demo:key");

        boolean updated = redisCacheTemplate.update("demo:key", "hello2", Duration.ofMinutes(10));

        boolean firstSet = redisCacheTemplate.setIfAbsent("demo:lock", "1", Duration.ofSeconds(30));

        boolean deleted = redisCacheTemplate.delete("demo:key");
    }
}
```

### 4.2 方法语义约定
- `set`: 覆盖写入并重置 TTL
- `get`: key 不存在返回 `null`
- `update`: key 不存在返回 `false`，不会新建
- `setIfAbsent`: key 已存在返回 `false`
- `delete`: 删除成功返回 `true`，不存在通常为 `false`

---

## 5. Kafka 幂等中的 Redis 用法
Kafka 消费幂等由 `RedisMessageIdempotencyService` 实现，核心 key 规则：

- `...:processing`：处理中的短 TTL key（防并发重复处理）
- `...:done`：已完成的长 TTL key（防重复消费）

处理流程：
1. `tryAcquire(messageId)`
   - `done` 存在 => 直接判定重复
   - `processing` `setIfAbsent` 成功 => 允许消费
   - 否则 => 处理中
2. 业务成功：`markDone(messageId)`（写 `done`，删 `processing`）
3. 业务失败：`releaseProcessing(messageId)`（删 `processing`）

---

## 6. 使用规范（统一入口）
1. 业务代码不要直接操作 `RedissonClient`。
2. 统一通过 `RedisCacheTemplate` 访问 Redis。
3. Key 命名建议：`业务域:模块:实体:标识`。
4. 所有缓存写入必须带 TTL，避免脏数据长期驻留。
5. 需要“仅首次写入”场景，统一使用 `setIfAbsent`。

---

## 7. 排查建议
- 启动失败先看 `machine.redis.address` 是否带 `redis://` 前缀。
- 如配置了密码但连接失败，检查密码是否与目标实例一致。
- 若 `enabled=false`，`RedissonClient` 不会创建。
- 反序列化报错通常是 `get(key, type)` 的 `type` 与实际存储结构不一致。

---

## 8. 已有验证
已覆盖并通过：
- `RedisPropertiesBindingTest`
- `RedissonRedisCacheTemplateTest`
- `RedisMessageIdempotencyServiceTest`
- `KafkaConsumerPropertiesBindingTest`
- `AbstractKafkaConsumerTemplateTest`

可执行：
```bash
mvn -Dtest=RedisPropertiesBindingTest,RedissonRedisCacheTemplateTest,RedisMessageIdempotencyServiceTest test
```