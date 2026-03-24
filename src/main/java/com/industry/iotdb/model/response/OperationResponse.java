package com.industry.iotdb.model.response;

public class OperationResponse {

    private boolean success;
    private String message;
    private Integer affectedCount;

    public static OperationResponse success(String message, Integer affectedCount) {
        OperationResponse response = new OperationResponse();
        response.setSuccess(true);
        response.setMessage(message);
        response.setAffectedCount(affectedCount);
        return response;
    }

    public static OperationResponse failure(String message) {
        OperationResponse response = new OperationResponse();
        response.setSuccess(false);
        response.setMessage(message);
        response.setAffectedCount(0);
        return response;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getAffectedCount() {
        return affectedCount;
    }

    public void setAffectedCount(Integer affectedCount) {
        this.affectedCount = affectedCount;
    }
}
