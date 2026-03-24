package com.industry.iotdb.service;

import com.industry.iotdb.exception.IoTDBBadRequestException;
import com.industry.iotdb.exception.IoTDBUnavailableException;
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
import com.industry.iotdb.service.impl.IoTDBDataServiceImpl;
import com.industry.iotdb.support.IoTDBTestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IoTDBDataServiceTest {

    @Mock
    private IoTDBRepository repository;

    @InjectMocks
    private IoTDBDataServiceImpl service;

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
    void shouldWriteSingle() {
        when(repository.insertRecord(singleWriteRequest)).thenReturn(1);

        OperationResponse response = service.writeSingle(singleWriteRequest);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getAffectedCount());
        verify(repository).insertRecord(singleWriteRequest);
    }

    @Test
    void shouldWriteBatch() {
        when(repository.insertRecords(batchWriteRequest.getRecords())).thenReturn(2);

        OperationResponse response = service.writeBatch(batchWriteRequest);

        assertTrue(response.isSuccess());
        assertEquals(2, response.getAffectedCount());
        verify(repository).insertRecords(batchWriteRequest.getRecords());
    }

    @Test
    void shouldQuerySingle() {
        when(repository.query(queryRequest)).thenReturn(IoTDBTestDataFactory.queryResponse().getRows());

        QueryResponse response = service.querySingle(queryRequest);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getCount());
        verify(repository).query(queryRequest);
    }

    @Test
    void shouldQueryBatch() {
        when(repository.query(any(QueryRequest.class))).thenReturn(IoTDBTestDataFactory.queryResponse().getRows());

        List<QueryResponse> responses = service.queryBatch(batchQueryRequest);

        assertEquals(2, responses.size());
        verify(repository, times(2)).query(any(QueryRequest.class));
    }

    @Test
    void shouldUpdateSingle() {
        when(repository.insertRecord(updateRequest)).thenReturn(1);

        OperationResponse response = service.updateSingle(updateRequest);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getAffectedCount());
        verify(repository).insertRecord(updateRequest);
    }

    @Test
    void shouldUpdateBatch() {
        when(repository.delete(any(DeleteRequest.class))).thenReturn(1);
        when(repository.insertRecords(batchUpdateRequest.getRecords())).thenReturn(2);

        OperationResponse response = service.updateBatch(batchUpdateRequest);

        assertTrue(response.isSuccess());
        assertEquals(2, response.getAffectedCount());
        verify(repository).delete(any(DeleteRequest.class));
        verify(repository).insertRecords(batchUpdateRequest.getRecords());
    }

    @Test
    void shouldDelete() {
        when(repository.delete(deleteRequest)).thenReturn(1);

        OperationResponse response = service.delete(deleteRequest);

        assertTrue(response.isSuccess());
        assertEquals(1, response.getAffectedCount());
        verify(repository).delete(deleteRequest);
    }

    @Test
    void shouldThrowBadRequestWhenQueryTimeRangeInvalid() {
        queryRequest.setStartTime(1711268200000L);
        queryRequest.setEndTime(1711267200000L);

        IoTDBBadRequestException ex = assertThrows(IoTDBBadRequestException.class, () -> service.querySingle(queryRequest));

        assertEquals("Invalid time range for query: startTime must be less than or equal to endTime", ex.getMessage());
    }

    @Test
    void shouldThrowBadRequestWhenBatchUpdateContainsMixedDevices() {
        BatchUpdateRequest mixedDeviceRequest = IoTDBTestDataFactory.mixedDeviceBatchUpdateRequest();

        IoTDBBadRequestException ex = assertThrows(IoTDBBadRequestException.class, () -> service.updateBatch(mixedDeviceRequest));

        assertEquals("Batch update records must belong to the same device", ex.getMessage());
    }

    @Test
    void shouldPropagateUnavailableExceptionFromRepository() {
        IoTDBUnavailableException unavailableException = new IoTDBUnavailableException("Failed to query IoTDB data", new RuntimeException("down"));
        when(repository.query(queryRequest)).thenThrow(unavailableException);

        IoTDBUnavailableException ex = assertThrows(IoTDBUnavailableException.class, () -> service.querySingle(queryRequest));

        assertEquals("Failed to query IoTDB data", ex.getMessage());
    }
}
