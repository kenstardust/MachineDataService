package com.industry.iotdb.model.request;

import com.industry.iotdb.model.dto.IoTDBRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class BatchWriteRequest {

    @Valid
    @NotEmpty
    private List<IoTDBRecord> records;

    public List<IoTDBRecord> getRecords() {
        return records;
    }

    public void setRecords(List<IoTDBRecord> records) {
        this.records = records;
    }
}
