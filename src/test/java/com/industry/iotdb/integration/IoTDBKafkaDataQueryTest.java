package com.industry.iotdb.integration;

import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.service.IoTDBDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration",
        "machine.redis.enabled=false",
        "iotdb.host=10.1.40.171",
        "iotdb.port=6667",
        "iotdb.username=root",
        "iotdb.password=root",
        "iotdb.database=root.test"
})
class IoTDBKafkaDataQueryTest {

    @Autowired
    private IoTDBDataService ioTDBDataService;

    @Test
    void shouldQueryKafkaMessageData() {
        // 根据日志: offset=4554, timestampMs=1776663680068, channel01
        // 设备路径: root.test.channel_01

        QueryRequest request = new QueryRequest();
        request.setDevice("root.test.channel_01");
        request.setMeasurements(java.util.List.of("value"));
        request.setStartTime(1776663680060L);
        request.setEndTime(1776663680100L);
        request.setLimit(100);
        request.setOffset(0);

        var response = ioTDBDataService.querySingle(request);

        System.out.println("[IoTDB QUERY TEST] device=root.test.channel_01");
        System.out.println("[IoTDB QUERY TEST] time range: 1776663680060 - 1776663680100");
        System.out.println("[IoTDB QUERY TEST] success=" + response.isSuccess());
        System.out.println("[IoTDB QUERY TEST] count=" + response.getCount());
        System.out.println("[IoTDB QUERY TEST] rows=" + response.getRows());

        // 查询所有 channel_01 的数据
        QueryRequest allRequest = new QueryRequest();
        allRequest.setDevice("root.test.channel_01");
        allRequest.setMeasurements(java.util.List.of("value"));
        allRequest.setStartTime(1776663680000L);
        allRequest.setEndTime(1776663700000L);
        allRequest.setLimit(1000);
        allRequest.setOffset(0);

        var allResponse = ioTDBDataService.querySingle(allRequest);
        System.out.println("[IoTDB QUERY TEST] all channel_01 count=" + allResponse.getCount());
        System.out.println("[IoTDB QUERY TEST] all rows=" + allResponse.getRows());

        assertTrue(response.isSuccess(), "Query should succeed");
    }
}
