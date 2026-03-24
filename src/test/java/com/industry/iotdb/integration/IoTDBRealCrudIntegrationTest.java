package com.industry.iotdb.integration;

import com.industry.iotdb.model.dto.IoTDBField;
import com.industry.iotdb.model.dto.IoTDBRecord;
import com.industry.iotdb.model.request.BatchUpdateRequest;
import com.industry.iotdb.model.request.BatchWriteRequest;
import com.industry.iotdb.model.request.DeleteRequest;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.model.request.SingleWriteRequest;
import com.industry.iotdb.model.request.UpdateRequest;
import com.industry.iotdb.model.response.OperationResponse;
import com.industry.iotdb.model.response.QueryResponse;
import com.industry.iotdb.service.IoTDBDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
        "iotdb.host=10.1.40.171",
        "iotdb.port=6667",
        "iotdb.username=root",
        "iotdb.password=root",
        "iotdb.database=root.test"
})
class IoTDBRealCrudIntegrationTest {

    @Autowired
    private IoTDBDataService ioTDBDataService;

    @Test
    void shouldRunRealCrudOnRootTestDatabase() {
        String randomSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String device = "root.test.device_" + randomSuffix;
        long base = System.currentTimeMillis();

        double singleValue = randomDouble(30.0, 40.0);
        double batchValue1 = randomDouble(40.0, 50.0);
        double batchValue2 = randomDouble(50.0, 60.0);
        double updateSingleValue = randomDouble(60.0, 70.0);
        double updateBatchValue1 = randomDouble(70.0, 80.0);
        double updateBatchValue2 = randomDouble(80.0, 90.0);

        SingleWriteRequest singleWriteRequest = new SingleWriteRequest();
        singleWriteRequest.setDevice(device);
        singleWriteRequest.setTimestamp(base);
        singleWriteRequest.setFields(List.of(field("temperature", "DOUBLE", singleValue)));

        OperationResponse singleWriteResponse = ioTDBDataService.writeSingle(singleWriteRequest);
        assertTrue(singleWriteResponse.isSuccess());

        BatchWriteRequest batchWriteRequest = new BatchWriteRequest();
        batchWriteRequest.setRecords(List.of(
                record(device, base + 1, batchValue1),
                record(device, base + 2, batchValue2)
        ));

        OperationResponse batchWriteResponse = ioTDBDataService.writeBatch(batchWriteRequest);
        assertTrue(batchWriteResponse.isSuccess());
        assertEquals(2, batchWriteResponse.getAffectedCount());

        QueryRequest queryAllRequest = query(device, base, base + 2);
        QueryResponse queryAllResponse = ioTDBDataService.querySingle(queryAllRequest);
        assertTrue(queryAllResponse.isSuccess());
        assertTrue(queryAllResponse.getCount() >= 3);

        UpdateRequest updateRequest = new UpdateRequest();
        updateRequest.setDevice(device);
        updateRequest.setTimestamp(base);
        updateRequest.setFields(List.of(field("temperature", "DOUBLE", updateSingleValue)));

        OperationResponse updateSingleResponse = ioTDBDataService.updateSingle(updateRequest);
        assertTrue(updateSingleResponse.isSuccess());

        QueryResponse queryAfterSingleUpdate = ioTDBDataService.querySingle(query(device, base, base));
        assertContainsTemperature(queryAfterSingleUpdate.getRows(), updateSingleValue);

        BatchUpdateRequest batchUpdateRequest = new BatchUpdateRequest();
        batchUpdateRequest.setStartTime(base + 1);
        batchUpdateRequest.setEndTime(base + 2);
        batchUpdateRequest.setRecords(List.of(
                record(device, base + 1, updateBatchValue1),
                record(device, base + 2, updateBatchValue2)
        ));

        OperationResponse updateBatchResponse = ioTDBDataService.updateBatch(batchUpdateRequest);
        assertTrue(updateBatchResponse.isSuccess());
        assertEquals(2, updateBatchResponse.getAffectedCount());

        QueryResponse queryAfterBatchUpdate = ioTDBDataService.querySingle(query(device, base + 1, base + 2));
        assertContainsTemperature(queryAfterBatchUpdate.getRows(), updateBatchValue1);
        assertContainsTemperature(queryAfterBatchUpdate.getRows(), updateBatchValue2);

        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setDevice(device);
        deleteRequest.setMeasurements(List.of("temperature"));
        deleteRequest.setStartTime(base);
        deleteRequest.setEndTime(base);

        OperationResponse deleteResponse = ioTDBDataService.delete(deleteRequest);
        assertTrue(deleteResponse.isSuccess());

        QueryResponse queryAfterDelete = ioTDBDataService.querySingle(query(device, base, base + 2));
        assertTrue(queryAfterDelete.getCount() >= 1);

        System.out.println("[IoTDB REAL TEST] device=" + device);
        System.out.println("[IoTDB REAL TEST] singleValue=" + singleValue);
        System.out.println("[IoTDB REAL TEST] batchValue1=" + batchValue1 + ", batchValue2=" + batchValue2);
        System.out.println("[IoTDB REAL TEST] updateSingleValue=" + updateSingleValue);
        System.out.println("[IoTDB REAL TEST] updateBatchValue1=" + updateBatchValue1 + ", updateBatchValue2=" + updateBatchValue2);
    }

    private static QueryRequest query(String device, long start, long end) {
        QueryRequest request = new QueryRequest();
        request.setDevice(device);
        request.setMeasurements(List.of("temperature"));
        request.setStartTime(start);
        request.setEndTime(end);
        request.setLimit(100);
        request.setOffset(0);
        return request;
    }

    private static IoTDBRecord record(String device, long timestamp, double temperature) {
        IoTDBRecord record = new IoTDBRecord();
        record.setDevice(device);
        record.setTimestamp(timestamp);
        record.setFields(List.of(field("temperature", "DOUBLE", temperature)));
        return record;
    }

    private static IoTDBField field(String measurement, String dataType, Object value) {
        IoTDBField field = new IoTDBField();
        field.setMeasurement(measurement);
        field.setDataType(dataType);
        field.setValue(value);
        return field;
    }

    private static double randomDouble(double min, double max) {
        return min + Math.random() * (max - min);
    }

    private static void assertContainsTemperature(List<Map<String, Object>> rows, double expected) {
        boolean matched = rows.stream().anyMatch(row -> row.entrySet().stream()
                .filter(entry -> !"Time".equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Number.class::isInstance)
                .map(Number.class::cast)
                .anyMatch(number -> Math.abs(number.doubleValue() - expected) < 1e-6));
        assertTrue(matched, "Expected temperature not found in rows: " + expected);
    }
}
