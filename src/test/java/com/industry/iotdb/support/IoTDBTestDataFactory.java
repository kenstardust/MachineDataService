package com.industry.iotdb.support;

import com.industry.iotdb.model.dto.IoTDBField;
import com.industry.iotdb.model.dto.IoTDBRecord;
import com.industry.iotdb.model.request.BatchQueryRequest;
import com.industry.iotdb.model.request.BatchUpdateRequest;
import com.industry.iotdb.model.request.BatchWriteRequest;
import com.industry.iotdb.model.request.DeleteRequest;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.model.request.SingleWriteRequest;
import com.industry.iotdb.model.request.UpdateRequest;
import com.industry.iotdb.model.response.OperationResponse;
import com.industry.iotdb.model.response.QueryResponse;

import java.util.List;
import java.util.Map;

public final class IoTDBTestDataFactory {

    private IoTDBTestDataFactory() {
    }

    public static SingleWriteRequest singleWriteRequest() {
        SingleWriteRequest request = new SingleWriteRequest();
        request.setDevice("root.machine.device01");
        request.setTimestamp(1711267200000L);
        request.setFields(List.of(field("temperature", "DOUBLE", 36.5), field("pressure", "FLOAT", 1.2)));
        return request;
    }

    public static BatchWriteRequest batchWriteRequest() {
        BatchWriteRequest request = new BatchWriteRequest();
        request.setRecords(List.of(record("root.machine.device01", 1711267200000L, 36.5), record("root.machine.device01", 1711267201000L, 36.8)));
        return request;
    }

    public static QueryRequest queryRequest() {
        QueryRequest request = new QueryRequest();
        request.setDevice("root.machine.device01");
        request.setMeasurements(List.of("temperature", "pressure"));
        request.setStartTime(1711267200000L);
        request.setEndTime(1711268200000L);
        request.setLimit(100);
        request.setOffset(0);
        return request;
    }

    public static BatchQueryRequest batchQueryRequest() {
        BatchQueryRequest request = new BatchQueryRequest();
        request.setQueries(List.of(queryRequest(), queryRequest()));
        return request;
    }

    public static UpdateRequest updateRequest() {
        UpdateRequest request = new UpdateRequest();
        request.setDevice("root.machine.device01");
        request.setTimestamp(1711267200000L);
        request.setFields(List.of(field("temperature", "DOUBLE", 37.0)));
        return request;
    }

    public static BatchUpdateRequest batchUpdateRequest() {
        BatchUpdateRequest request = new BatchUpdateRequest();
        request.setStartTime(1711267200000L);
        request.setEndTime(1711268200000L);
        request.setRecords(List.of(record("root.machine.device01", 1711267200000L, 37.0), record("root.machine.device01", 1711267201000L, 37.2)));
        return request;
    }

    public static BatchUpdateRequest mixedDeviceBatchUpdateRequest() {
        BatchUpdateRequest request = new BatchUpdateRequest();
        request.setStartTime(1711267200000L);
        request.setEndTime(1711268200000L);
        request.setRecords(List.of(record("root.machine.device01", 1711267200000L, 37.0), record("root.machine.device02", 1711267201000L, 37.2)));
        return request;
    }

    public static DeleteRequest deleteRequest() {
        DeleteRequest request = new DeleteRequest();
        request.setDevice("root.machine.device01");
        request.setMeasurements(List.of("temperature"));
        request.setStartTime(1711267200000L);
        request.setEndTime(1711268200000L);
        return request;
    }

    public static QueryResponse queryResponse() {
        QueryResponse response = new QueryResponse();
        response.setSuccess(true);
        response.setMessage("IoTDB query success");
        response.setCount(1);
        response.setRows(List.of(Map.of("Time", 1711267200000L, "temperature", 36.5)));
        return response;
    }

    public static OperationResponse operationResponse(String message, int affectedCount) {
        return OperationResponse.success(message, affectedCount);
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
}
