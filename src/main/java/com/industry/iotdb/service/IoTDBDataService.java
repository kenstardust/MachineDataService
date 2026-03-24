package com.industry.iotdb.service;

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

public interface IoTDBDataService {

    OperationResponse writeSingle(SingleWriteRequest request);

    OperationResponse writeBatch(BatchWriteRequest request);

    QueryResponse querySingle(QueryRequest request);

    List<QueryResponse> queryBatch(BatchQueryRequest request);

    OperationResponse updateSingle(UpdateRequest request);

    OperationResponse updateBatch(BatchUpdateRequest request);

    OperationResponse delete(DeleteRequest request);
}
