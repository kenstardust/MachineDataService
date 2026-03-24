# IoTDB 接口说明文档

## 一、模块说明

当前 IoTDB 模块采用三层结构：

- **Controller 层**：接收 HTTP 请求
- **Service 层**：做业务编排
- **Repository 层**：直接调用 IoTDB 原生客户端 `SessionPool`

调用链：

```
HTTP Request -> Controller -> Service -> Repository -> IoTDB
```

相关代码：

- [IoTDBController.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java)
- [IoTDBDataService.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/IoTDBDataService.java)
- [IoTDBDataServiceImpl.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java)
- [IoTDBRepository.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java)

------

# 二、统一数据模型说明

## 1. 单条记录结构

核心 DTO：

- [IoTDBRecord.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/model/dto/IoTDBRecord.java)
- [IoTDBField.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/model/dto/IoTDBField.java)

### `IoTDBRecord`

表示一条时序记录，包含：

- `device`：设备路径，例如 `root.machine.device01`
- `timestamp`：时间戳
- `fields`：测点列表

### `IoTDBField`

表示一个测点字段，包含：

- `measurement`：测点名，例如 `temperature`
- `dataType`：IoTDB 数据类型，例如 `DOUBLE`
- `value`：值

------

## 2. 通用返回结构

### `OperationResponse`

文件：

- [OperationResponse.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/model/response/OperationResponse.java)

字段：

- `success`：是否成功
- `message`：返回消息
- `affectedCount`：影响条数

### `QueryResponse`

文件：

- [QueryResponse.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/model/response/QueryResponse.java)

字段：

- `success`
- `message`
- `count`
- `rows`

------

# 三、Controller 接口文档

基类：

- 路径前缀：`/iotdb`
- 文件：[IoTDBController.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java)

------

## 1. 单条写入

### 接口

- **URL**: `POST /iotdb/write/single`

### Controller 方法

- [IoTDBController.java:34-37](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java#L34-L37)

### Service 方法

- [IoTDBDataServiceImpl.java:27-30](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java#L27-L30)

### Repository 方法

- [IoTDBRepository.java:35-49](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L35-L49)

### 请求参数



```json
{
  "device": "root.machine.device01",
  "timestamp": 1711267200000,
  "fields": [
    {
      "measurement": "temperature",
      "dataType": "DOUBLE",
      "value": 36.5
    },
    {
      "measurement": "pressure",
      "dataType": "FLOAT",
      "value": 1.2
    }
  ]
}
```

### 调用逻辑

1. Controller 接收 `SingleWriteRequest`
2. 调用 `ioTDBDataService.writeSingle(request)`
3. Service 调用 `repository.insertRecord(request)`
4. Repository：
   - 先检查/自动创建 timeseries
   - 再调用 `sessionPool.insertRecord(...)`

### 返回示例



```json
{
  "success": true,
  "message": "IoTDB single write success",
  "affectedCount": 1
}
```

------

## 2. 批量写入

### 接口

- **URL**: `POST /iotdb/write/batch`

### Controller 方法

- [IoTDBController.java:39-42](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java#L39-L42)

### Service 方法

- [IoTDBDataServiceImpl.java:32-35](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java#L32-L35)

### Repository 方法

- [IoTDBRepository.java:51-64](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L51-L64)

### 请求参数



```json
{
  "records": [
    {
      "device": "root.machine.device01",
      "timestamp": 1711267200000,
      "fields": [
        {
          "measurement": "temperature",
          "dataType": "DOUBLE",
          "value": 36.5
        }
      ]
    },
    {
      "device": "root.machine.device01",
      "timestamp": 1711267201000,
      "fields": [
        {
          "measurement": "temperature",
          "dataType": "DOUBLE",
          "value": 36.8
        }
      ]
    }
  ]
}
```

### 调用逻辑

1. Controller 接收 `BatchWriteRequest`
2. Service 调用 `repository.insertRecords(request.getRecords())`
3. Repository：
   - 按 `device + timestamp` 排序
   - 按 `MAX_BATCH_SIZE = 1000` 分块
   - 每块调用 `insertChunk`

### 返回示例



```json
{
  "success": true,
  "message": "IoTDB batch write success",
  "affectedCount": 2
}
```

------

## 3. 单条查询

### 接口

- **URL**: `POST /iotdb/query/single`

### Controller 方法

- [IoTDBController.java:44-47](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java#L44-L47)

### Service 方法

- [IoTDBDataServiceImpl.java:37-45](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java#L37-L45)

### Repository 方法

- [IoTDBRepository.java:66-91](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L66-L91)

### 请求参数



```json
{
  "device": "root.machine.device01",
  "measurements": ["temperature", "pressure"],
  "startTime": 1711267200000,
  "endTime": 1711268200000,
  "limit": 100,
  "offset": 0
}
```

### 调用逻辑

1. Controller 接收 `QueryRequest`
2. Service 调用 `repository.query(request)`
3. Repository：
   - 通过 `buildQuerySql(request)` 拼 SQL
   - 使用 `sessionPool.executeQueryStatement(sql)` 查询
   - 遍历结果集并转换成 `List<Map<String,Object>>`

### SQL 实际构造方式

参考：

- [IoTDBRepository.java:147-156](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L147-L156)

生成类似：



```sql
select temperature,pressure
from root.machine.device01
where time >= 1711267200000
  and time <= 1711268200000
limit 100
offset 0
```

### 返回示例



```json
{
  "success": true,
  "message": "IoTDB query success",
  "count": 2,
  "rows": [
    {
      "Time": 1711267200000,
      "temperature": 36.5,
      "pressure": 1.2
    },
    {
      "Time": 1711267201000,
      "temperature": 36.8,
      "pressure": 1.3
    }
  ]
}
```

------

## 4. 批量查询

### 接口

- **URL**: `POST /iotdb/query/batch`

### Controller 方法

- [IoTDBController.java:49-52](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java#L49-L52)

### Service 方法

- [IoTDBDataServiceImpl.java:47-50](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java#L47-L50)

### 请求参数



```json
{
  "queries": [
    {
      "device": "root.machine.device01",
      "measurements": ["temperature"],
      "startTime": 1711267200000,
      "endTime": 1711268200000,
      "limit": 100,
      "offset": 0
    },
    {
      "device": "root.machine.device02",
      "measurements": ["pressure"],
      "startTime": 1711267200000,
      "endTime": 1711268200000,
      "limit": 100,
      "offset": 0
    }
  ]
}
```

### 调用逻辑

1. Controller 接收 `BatchQueryRequest`
2. Service 遍历 `queries`
3. 对每个 query 调一次 `querySingle`
4. 最后返回 `List<QueryResponse>`

### 返回示例



```json
[
  {
    "success": true,
    "message": "IoTDB query success",
    "count": 2,
    "rows": [
      {
        "Time": 1711267200000,
        "temperature": 36.5
      }
    ]
  },
  {
    "success": true,
    "message": "IoTDB query success",
    "count": 1,
    "rows": [
      {
        "Time": 1711267200000,
        "pressure": 1.1
      }
    ]
  }
]
```

------

## 5. 查看示例接口

### 接口

- **URL**: `GET /iotdb/examples`

### Controller 方法

- [IoTDBController.java:54-57](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java#L54-L57)

### 作用

返回一整段示例请求文本

### 示例返回

是一个字符串，里面包含：

- 单条写入示例
- 批量写入示例
- 单条查询示例
- 单条更新示例
- 批量更新示例
- 删除示例

来源：

- [ApiUsageExamples.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/model/response/ApiUsageExamples.java)

------

## 6. 单条更新

### 接口

- **URL**: `POST /iotdb/update/single`

### Controller 方法

- [IoTDBController.java:59-62](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java#L59-L62)

### Service 方法

- [IoTDBDataServiceImpl.java:52-55](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java#L52-L55)

### 请求参数



```json
{
  "device": "root.machine.device01",
  "timestamp": 1711267200000,
  "fields": [
    {
      "measurement": "temperature",
      "dataType": "DOUBLE",
      "value": 37.0
    }
  ]
}
```

### 调用逻辑

1. Controller 接收 `UpdateRequest`
2. Service 调用 `repository.insertRecord(request)`

### 当前语义

这里的“更新”其实是：

- 同一时间点重新写入
- 用覆盖写的方式实现更新

### 返回示例



```json
{
  "success": true,
  "message": "IoTDB overwrite update success",
  "affectedCount": 1
}
```

------

## 7. 批量更新

### 接口

- **URL**: `POST /iotdb/update/batch`

### Controller 方法

- [IoTDBController.java:64-67](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java#L64-L67)

### Service 方法

- [IoTDBDataServiceImpl.java:57-68](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java#L57-L68)

### 请求参数



```json
{
  "startTime": 1711267200000,
  "endTime": 1711268200000,
  "records": [
    {
      "device": "root.machine.device01",
      "timestamp": 1711267200000,
      "fields": [
        {
          "measurement": "temperature",
          "dataType": "DOUBLE",
          "value": 37.0
        }
      ]
    },
    {
      "device": "root.machine.device01",
      "timestamp": 1711267201000,
      "fields": [
        {
          "measurement": "temperature",
          "dataType": "DOUBLE",
          "value": 37.2
        }
      ]
    }
  ]
}
```

### 调用逻辑

1. Controller 接收 `BatchUpdateRequest`
2. Service 判断 records 是否为空
3. 如果不为空：
   - 构造一个 `DeleteRequest`
   - 使用第一条记录的 `device`
   - 使用传入的 `startTime/endTime`
4. 调 `repository.delete(deleteRequest)`
5. 再调 `repository.insertRecords(request.getRecords())`

### 当前语义

- **先删后写**
- 即 delete + reinsert

### 返回示例



```json
{
  "success": true,
  "message": "IoTDB batch update success",
  "affectedCount": 2
}
```

### 当前限制

这个接口现在有个明确限制：

- 它默认只用 **第一条记录的 device** 去删
- 所以如果 `records` 里混了多个 device，逻辑不够严谨

这点后面建议继续优化。

------

## 8. 删除

### 接口

- **URL**: `DELETE /iotdb/delete`

### Controller 方法

- [IoTDBController.java:69-72](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/controller/IoTDBController.java#L69-L72)

### Service 方法

- [IoTDBDataServiceImpl.java:70-73](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java#L70-L73)

### Repository 方法

- [IoTDBRepository.java:93-101](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L93-L101)

### 请求参数



```json
{
  "device": "root.machine.device01",
  "measurements": ["temperature"],
  "startTime": 1711267200000,
  "endTime": 1711268200000
}
```

### 调用逻辑

1. Controller 接收 `DeleteRequest`
2. Service 调用 `repository.delete(request)`
3. Repository：
   - 调 `buildDeletePaths(request)` 生成 path
   - 调 `sessionPool.deleteData(paths, startTime, endTime)`

### 删除路径规则

参考：

- [IoTDBRepository.java:158-165](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L158-L165)

规则：

- 如果 `measurements` 为空：
  - 删除 `device.**`
- 如果 `measurements` 不为空：
  - 删除 `device.measurement`

### 返回示例



```json
{
  "success": true,
  "message": "IoTDB delete success",
  "affectedCount": 1
}
```

------

# 四、Service 接口定义

接口文件：

- [IoTDBDataService.java](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/IoTDBDataService.java)

当前定义的方法如下：



```java
OperationResponse writeSingle(SingleWriteRequest request);

OperationResponse writeBatch(BatchWriteRequest request);

QueryResponse querySingle(QueryRequest request);

List<QueryResponse> queryBatch(BatchQueryRequest request);

OperationResponse updateSingle(UpdateRequest request);

OperationResponse updateBatch(BatchUpdateRequest request);

OperationResponse delete(DeleteRequest request);
```

说明：

- 这是 IoTDB 模块对 controller 暴露的统一业务接口
- 目前 controller 所有接口都只依赖这个 service

------

# 五、当前实现的业务语义总结

## 写入

- 单条写入：直接写一条
- 批量写入：分块后批量写入

## 查询

- 单查：按 device + measurement + 时间范围查询
- 批查：多个 query 循环执行

## 更新

- 单条更新：覆盖写入
- 批量更新：删时间范围后重写

## 删除

- 按 device / measurement / 时间范围删

------

# 六、当前代码的几个注意点

## 1. 自动创建 timeseries

位置：

- [IoTDBRepository.java:121-133](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L121-L133)

说明：

- 写入前会自动检查测点是否存在
- 不存在就自动建

------

## 2. 批量写入分块大小

位置：

- [IoTDBRepository.java:27](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L27)

当前固定值：



```java
private static final int MAX_BATCH_SIZE = 1000;
```

------

## 3. 查询是 SQL 字符串拼接

位置：

- [IoTDBRepository.java:147-156](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/repository/IoTDBRepository.java#L147-L156)

说明：

- 现在可以用
- 但后面最好补更严格的路径校验

------

## 4. 批量更新暂时只适合单 device

位置：

- [IoTDBDataServiceImpl.java:62-67](vscode-webview://0r47urc0n81pnrhq3ojfu4dkuk7rp3ncqt9ol34r1q1qig6elkdv/src/main/java/com/industry/iotdb/service/impl/IoTDBDataServiceImpl.java#L62-L67)

说明：

- 如果你传多个 device，当前实现不够严谨

------

# 七、建议你现在怎么用

如果你当前是调试阶段，建议这样用：

1. 先用
    `GET /iotdb/examples`
    拿示例
2. 先测试
   - `POST /iotdb/write/single`
   - `POST /iotdb/query/single`
3. 再测试
   - `POST /iotdb/write/batch`
   - `POST /iotdb/update/single`
   - `DELETE /iotdb/delete`
4. `POST /iotdb/update/batch`
    当前建议只传**同一个 device** 的 records