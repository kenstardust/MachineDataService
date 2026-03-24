package com.industry.iotdb.service.impl;

import com.industry.iotdb.exception.IoTDBBadRequestException;
import com.industry.iotdb.model.request.BatchQueryRequest;
import com.industry.iotdb.model.request.BatchUpdateRequest;
import com.industry.iotdb.model.request.BatchWriteRequest;
import com.industry.iotdb.model.request.DeleteRequest;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.model.request.SingleWriteRequest;
import com.industry.iotdb.model.request.UpdateRequest;
import com.industry.iotdb.model.response.OperationResponse;
import com.industry.iotdb.model.response.QueryResponse;
import com.industry.iotdb.repository.IoTDBRepository;
import com.industry.iotdb.service.IoTDBDataService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IoTDBDataServiceImpl implements IoTDBDataService {

    private final IoTDBRepository repository;

    public IoTDBDataServiceImpl(IoTDBRepository repository) {
        this.repository = repository;
    }

    @Override
    public OperationResponse writeSingle(SingleWriteRequest request) {
        return OperationResponse.success("IoTDB single write success", repository.insertRecord(request));
    }

    @Override
    public OperationResponse writeBatch(BatchWriteRequest request) {
        return OperationResponse.success("IoTDB batch write success", repository.insertRecords(request.getRecords()));
    }

    @Override
    public QueryResponse querySingle(QueryRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime(), "query");
        if (request.getLimit() != null && request.getLimit() <= 0) {
            throw new IoTDBBadRequestException("Query limit must be greater than 0");
        }
        if (request.getOffset() != null && request.getOffset() < 0) {
            throw new IoTDBBadRequestException("Query offset must be greater than or equal to 0");
        }
        QueryResponse response = new QueryResponse();
        response.setSuccess(true);
        response.setMessage("IoTDB query success");
        response.setRows(repository.query(request));
        response.setCount(response.getRows().size());
        return response;
    }

    @Override
    public List<QueryResponse> queryBatch(BatchQueryRequest request) {
        return request.getQueries().stream().map(this::querySingle).toList();
    }

    @Override
    public OperationResponse updateSingle(UpdateRequest request) {
        return OperationResponse.success("IoTDB overwrite update success", repository.insertRecord(request));
    }

    @Override
    public OperationResponse updateBatch(BatchUpdateRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime(), "batch update");
        if (request.getRecords().isEmpty()) {
            return OperationResponse.success("IoTDB batch update success", 0);
        }
        String device = request.getRecords().get(0).getDevice();
        boolean hasMixedDevices = request.getRecords().stream().anyMatch(record -> !device.equals(record.getDevice()));
        if (hasMixedDevices) {
            throw new IoTDBBadRequestException("Batch update records must belong to the same device");
        }
        DeleteRequest deleteRequest = new DeleteRequest();
        deleteRequest.setDevice(device);
        deleteRequest.setStartTime(request.getStartTime());
        deleteRequest.setEndTime(request.getEndTime());
        repository.delete(deleteRequest);
        return OperationResponse.success("IoTDB batch update success", repository.insertRecords(request.getRecords()));
    }

    @Override
    public OperationResponse delete(DeleteRequest request) {
        validateTimeRange(request.getStartTime(), request.getEndTime(), "delete");
        return OperationResponse.success("IoTDB delete success", repository.delete(request));
    }

    private void validateTimeRange(Long startTime, Long endTime, String operation) {
        if (startTime > endTime) {
            throw new IoTDBBadRequestException("Invalid time range for " + operation + ": startTime must be less than or equal to endTime");
        }
    }
}
