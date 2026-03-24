package com.industry.iotdb.support;

import com.industry.iotdb.exception.IoTDBBadRequestException;
import org.apache.tsfile.enums.TSDataType;

public final class IoTDBTypeConverter {

    private IoTDBTypeConverter() {
    }

    public static TSDataType toType(String dataType) {
        try {
            return TSDataType.valueOf(dataType.toUpperCase());
        } catch (Exception ex) {
            throw new IoTDBBadRequestException("Unsupported IoTDB data type: " + dataType, ex);
        }
    }

    public static Object convertValue(String dataType, Object value) {
        if (value == null) {
            return null;
        }
        TSDataType type = toType(dataType);
        String text = String.valueOf(value);
        try {
            return switch (type) {
                case BOOLEAN -> Boolean.parseBoolean(text);
                case INT32, DATE -> Integer.parseInt(text);
                case INT64, TIMESTAMP -> Long.parseLong(text);
                case FLOAT -> Float.parseFloat(text);
                case DOUBLE -> Double.parseDouble(text);
                case TEXT, STRING, BLOB -> text;
                default -> throw new IoTDBBadRequestException("Unsupported IoTDB data type: " + type);
            };
        } catch (NumberFormatException ex) {
            throw new IoTDBBadRequestException("Invalid value for IoTDB data type: " + dataType, ex);
        }
    }
}
