package com.industry.iotdb.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.industry.iotdb.exception.IoTDBBadRequestException;
import com.industry.iotdb.exception.IoTDBUnavailableException;
import com.industry.iotdb.model.request.BatchQueryRequest;
import com.industry.iotdb.model.request.BatchUpdateRequest;
import com.industry.iotdb.model.request.BatchWriteRequest;
import com.industry.iotdb.model.request.DeleteRequest;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.model.request.SingleWriteRequest;
import com.industry.iotdb.model.request.UpdateRequest;
import com.industry.iotdb.service.IoTDBDataService;
import com.industry.iotdb.support.IoTDBTestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = IoTDBController.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)
class IoTDBControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IoTDBDataService ioTDBDataService;

    private SingleWriteRequest singleWriteRequest;
    private BatchWriteRequest batchWriteRequest;
    private QueryRequest queryRequest;
    private BatchQueryRequest batchQueryRequest;
    private UpdateRequest updateRequest;
    private BatchUpdateRequest batchUpdateRequest;
    private DeleteRequest deleteRequest;

    @BeforeEach
    void setUp() {
        singleWriteRequest = IoTDBTestDataFactory.singleWriteRequest();
        batchWriteRequest = IoTDBTestDataFactory.batchWriteRequest();
        queryRequest = IoTDBTestDataFactory.queryRequest();
        batchQueryRequest = IoTDBTestDataFactory.batchQueryRequest();
        updateRequest = IoTDBTestDataFactory.updateRequest();
        batchUpdateRequest = IoTDBTestDataFactory.batchUpdateRequest();
        deleteRequest = IoTDBTestDataFactory.deleteRequest();
    }

    @Test
    void shouldWriteSingle() throws Exception {
        when(ioTDBDataService.writeSingle(any(SingleWriteRequest.class))).thenReturn(IoTDBTestDataFactory.operationResponse("IoTDB single write success", 1));

        mockMvc.perform(post("/iotdb/write/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(singleWriteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.affectedCount").value(1));
    }

    @Test
    void shouldWriteBatch() throws Exception {
        when(ioTDBDataService.writeBatch(any(BatchWriteRequest.class))).thenReturn(IoTDBTestDataFactory.operationResponse("IoTDB batch write success", 2));

        mockMvc.perform(post("/iotdb/write/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchWriteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.affectedCount").value(2));
    }

    @Test
    void shouldQuerySingle() throws Exception {
        when(ioTDBDataService.querySingle(any(QueryRequest.class))).thenReturn(IoTDBTestDataFactory.queryResponse());

        mockMvc.perform(post("/iotdb/query/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void shouldQueryBatch() throws Exception {
        when(ioTDBDataService.queryBatch(any(BatchQueryRequest.class))).thenReturn(java.util.List.of(IoTDBTestDataFactory.queryResponse(), IoTDBTestDataFactory.queryResponse()));

        mockMvc.perform(post("/iotdb/query/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchQueryRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    void shouldReturnExamples() throws Exception {
        mockMvc.perform(get("/iotdb/examples"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldUpdateSingle() throws Exception {
        when(ioTDBDataService.updateSingle(any(UpdateRequest.class))).thenReturn(IoTDBTestDataFactory.operationResponse("IoTDB overwrite update success", 1));

        mockMvc.perform(post("/iotdb/update/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldUpdateBatch() throws Exception {
        when(ioTDBDataService.updateBatch(any(BatchUpdateRequest.class))).thenReturn(IoTDBTestDataFactory.operationResponse("IoTDB batch update success", 2));

        mockMvc.perform(post("/iotdb/update/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(batchUpdateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldDelete() throws Exception {
        when(ioTDBDataService.delete(any(DeleteRequest.class))).thenReturn(IoTDBTestDataFactory.operationResponse("IoTDB delete success", 1));

        mockMvc.perform(delete("/iotdb/delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deleteRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void shouldReturnBadRequestWhenValidationFails() throws Exception {
        QueryRequest invalidRequest = IoTDBTestDataFactory.queryRequest();
        invalidRequest.setDevice("");

        mockMvc.perform(post("/iotdb/query/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldReturnBadRequestWhenServiceThrowsBadRequestException() throws Exception {
        when(ioTDBDataService.querySingle(any(QueryRequest.class)))
                .thenThrow(new IoTDBBadRequestException("Invalid time range"));

        mockMvc.perform(post("/iotdb/query/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queryRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid time range"));
    }

    @Test
    void shouldReturnServiceUnavailableWhenServiceThrowsUnavailableException() throws Exception {
        when(ioTDBDataService.querySingle(any(QueryRequest.class)))
                .thenThrow(new IoTDBUnavailableException("Failed to query IoTDB data", new RuntimeException("down")));

        mockMvc.perform(post("/iotdb/query/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queryRequest)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("IoTDB service is temporarily unavailable"));
    }

    @Test
    void shouldReturnInternalServerErrorWhenUnexpectedExceptionThrown() throws Exception {
        when(ioTDBDataService.querySingle(any(QueryRequest.class)))
                .thenThrow(new RuntimeException("sensitive detail"));

        mockMvc.perform(post("/iotdb/query/single")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(queryRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Internal server error"));
    }
}
