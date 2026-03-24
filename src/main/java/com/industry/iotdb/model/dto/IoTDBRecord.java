package com.industry.iotdb.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class IoTDBRecord {

    @NotBlank
    private String device;

    @NotNull
    private Long timestamp;

    @Valid
    @NotEmpty
    private List<IoTDBField> fields;

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public List<IoTDBField> getFields() {
        return fields;
    }

    public void setFields(List<IoTDBField> fields) {
        this.fields = fields;
    }
}
