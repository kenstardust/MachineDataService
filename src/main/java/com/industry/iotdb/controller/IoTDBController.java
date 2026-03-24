package com.industry.iotdb.controller;

import com.industry.iotdb.model.request.BatchQueryRequest;
import com.industry.iotdb.model.request.BatchUpdateRequest;
import com.industry.iotdb.model.request.BatchWriteRequest;
import com.industry.iotdb.model.request.DeleteRequest;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.model.request.SingleWriteRequest;
import com.industry.iotdb.model.request.UpdateRequest;
import com.industry.iotdb.model.response.ApiUsageExamples;
import com.industry.iotdb.model.response.OperationResponse;
import com.industry.iotdb.model.response.QueryResponse;
import com.industry.iotdb.service.IoTDBDataService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/iotdb")
public class IoTDBController {

    private final IoTDBDataService ioTDBDataService;

    public IoTDBController(IoTDBDataService ioTDBDataService) {
        this.ioTDBDataService = ioTDBDataService;
    }

    @PostMapping("/write/single")
    public OperationResponse writeSingle(@Valid @RequestBody SingleWriteRequest request) {
        return ioTDBDataService.writeSingle(request);
    }

    @PostMapping("/write/batch")
    public OperationResponse writeBatch(@Valid @RequestBody BatchWriteRequest request) {
        return ioTDBDataService.writeBatch(request);
    }

    @PostMapping("/query/single")
    public QueryResponse querySingle(@Valid @RequestBody QueryRequest request) {
        return ioTDBDataService.querySingle(request);
    }

    @PostMapping("/query/batch")
    public List<QueryResponse> queryBatch(@Valid @RequestBody BatchQueryRequest request) {
        return ioTDBDataService.queryBatch(request);
    }

    @GetMapping("/examples")
    public String examples() {
        return ApiUsageExamples.examples();
    }

    @PostMapping("/update/single")
    public OperationResponse updateSingle(@Valid @RequestBody UpdateRequest request) {
        return ioTDBDataService.updateSingle(request);
    }

    @PostMapping("/update/batch")
    public OperationResponse updateBatch(@Valid @RequestBody BatchUpdateRequest request) {
        return ioTDBDataService.updateBatch(request);
    }

    @DeleteMapping("/delete")
    public OperationResponse delete(@Valid @RequestBody DeleteRequest request) {
        return ioTDBDataService.delete(request);
    }
}
