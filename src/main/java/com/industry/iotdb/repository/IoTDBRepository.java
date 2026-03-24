package com.industry.iotdb.repository;

import com.industry.iotdb.exception.IoTDBOperationException;
import com.industry.iotdb.model.dto.IoTDBField;
import com.industry.iotdb.model.dto.IoTDBRecord;
import com.industry.iotdb.model.request.DeleteRequest;
import com.industry.iotdb.model.request.QueryRequest;
import com.industry.iotdb.support.IoTDBTypeConverter;
import org.apache.iotdb.isession.pool.SessionDataSetWrapper;
import org.apache.iotdb.rpc.IoTDBConnectionException;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.session.pool.SessionPool;
import org.apache.tsfile.enums.TSDataType;
import org.apache.tsfile.read.common.Field;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class IoTDBRepository {

    private static final int MAX_BATCH_SIZE = 1000;

    private final SessionPool sessionPool;

    public IoTDBRepository(SessionPool sessionPool) {
        this.sessionPool = sessionPool;
    }

    public int insertRecord(IoTDBRecord record) {
        try {
            ensureTimeseries(record);
            sessionPool.insertRecord(
                    record.getDevice(),
                    record.getTimestamp(),
                    measurementNames(record.getFields()),
                    measurementTypes(record.getFields()),
                    measurementValues(record.getFields())
            );
            return 1;
        } catch (IoTDBConnectionException | StatementExecutionException ex) {
            throw new IoTDBOperationException("Failed to insert IoTDB record", ex);
        }
    }

    public int insertRecords(List<IoTDBRecord> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        int affected = 0;
        List<IoTDBRecord> normalized = records.stream()
                .sorted(Comparator.comparing(IoTDBRecord::getDevice).thenComparing(IoTDBRecord::getTimestamp))
                .toList();
        for (int i = 0; i < normalized.size(); i += MAX_BATCH_SIZE) {
            List<IoTDBRecord> chunk = normalized.subList(i, Math.min(i + MAX_BATCH_SIZE, normalized.size()));
            affected += insertChunk(chunk);
        }
        return affected;
    }

    public List<Map<String, Object>> query(QueryRequest request) {
        String sql = buildQuerySql(request);
        try {
            SessionDataSetWrapper wrapper = sessionPool.executeQueryStatement(sql);
            try {
                List<Map<String, Object>> rows = new ArrayList<>();
                List<String> columnNames = wrapper.getColumnNames();
                while (wrapper.hasNext()) {
                    org.apache.tsfile.read.common.RowRecord rowRecord = wrapper.next();
                    Map<String, Object> row = new HashMap<>();
                    row.put("Time", rowRecord.getTimestamp());
                    List<Field> fields = rowRecord.getFields();
                    for (int i = 0; i < fields.size() && i + 1 < columnNames.size(); i++) {
                        Field field = fields.get(i);
                        row.put(columnNames.get(i + 1), field == null ? null : field.getObjectValue(field.getDataType()));
                    }
                    rows.add(row);
                }
                return rows;
            } finally {
                sessionPool.closeResultSet(wrapper);
            }
        } catch (IoTDBConnectionException | StatementExecutionException ex) {
            throw new IoTDBOperationException("Failed to query IoTDB data", ex);
        }
    }

    public int delete(DeleteRequest request) {
        try {
            List<String> paths = buildDeletePaths(request);
            sessionPool.deleteData(paths, request.getStartTime(), request.getEndTime());
            return paths.size();
        } catch (IoTDBConnectionException | StatementExecutionException ex) {
            throw new IoTDBOperationException("Failed to delete IoTDB data", ex);
        }
    }

    private int insertChunk(List<IoTDBRecord> chunk) {
        try {
            for (IoTDBRecord record : chunk) {
                ensureTimeseries(record);
            }
            sessionPool.insertRecords(
                    chunk.stream().map(IoTDBRecord::getDevice).toList(),
                    chunk.stream().map(IoTDBRecord::getTimestamp).toList(),
                    chunk.stream().map(record -> measurementNames(record.getFields())).toList(),
                    chunk.stream().map(record -> measurementTypes(record.getFields())).toList(),
                    chunk.stream().map(record -> measurementValues(record.getFields())).toList()
            );
            return chunk.size();
        } catch (IoTDBConnectionException | StatementExecutionException ex) {
            throw new IoTDBOperationException("Failed to batch insert IoTDB records", ex);
        }
    }

    private void ensureTimeseries(IoTDBRecord record) throws IoTDBConnectionException, StatementExecutionException {
        for (IoTDBField field : record.getFields()) {
            String path = record.getDevice() + "." + field.getMeasurement();
            if (!sessionPool.checkTimeseriesExists(path)) {
                sessionPool.createTimeseries(
                        path,
                        IoTDBTypeConverter.toType(field.getDataType()),
                        org.apache.tsfile.file.metadata.enums.TSEncoding.PLAIN,
                        org.apache.tsfile.file.metadata.enums.CompressionType.SNAPPY
                );
            }
        }
    }

    private List<String> measurementNames(List<IoTDBField> fields) {
        return fields.stream().map(IoTDBField::getMeasurement).collect(Collectors.toList());
    }

    private List<TSDataType> measurementTypes(List<IoTDBField> fields) {
        return fields.stream().map(field -> IoTDBTypeConverter.toType(field.getDataType())).collect(Collectors.toList());
    }

    private List<Object> measurementValues(List<IoTDBField> fields) {
        return fields.stream().map(field -> IoTDBTypeConverter.convertValue(field.getDataType(), field.getValue())).collect(Collectors.toList());
    }

    private String buildQuerySql(QueryRequest request) {
        String selectClause = (request.getMeasurements() == null || request.getMeasurements().isEmpty())
                ? "*"
                : String.join(",", request.getMeasurements());
        return "select " + selectClause + " from " + request.getDevice()
                + " where time >= " + request.getStartTime()
                + " and time <= " + request.getEndTime()
                + " limit " + request.getLimit()
                + " offset " + request.getOffset();
    }

    private List<String> buildDeletePaths(DeleteRequest request) {
        if (request.getMeasurements() == null || request.getMeasurements().isEmpty()) {
            return List.of(request.getDevice() + ".**");
        }
        return request.getMeasurements().stream()
                .map(measurement -> request.getDevice() + "." + measurement)
                .toList();
    }
}
