package com.industry.iotdb.model.request;

import com.industry.iotdb.model.dto.IoTDBRecord;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class BatchUpdateRequest {

    @Valid
    @NotEmpty
    private List<IoTDBRecord> records;

    @NotNull
    private Long startTime;

    @NotNull
    private Long endTime;

    public List<IoTDBRecord> getRecords() {
        return records;
    }

    public void setRecords(List<IoTDBRecord> records) {
        this.records = records;
    }

    public Long getStartTime() {
        return startTime;
    }

    public void setStartTime(Long startTime) {
        this.startTime = startTime;
    }

    public Long getEndTime() {
        return endTime;
    }

    public void setEndTime(Long endTime) {
        this.endTime = endTime;
    }
}
