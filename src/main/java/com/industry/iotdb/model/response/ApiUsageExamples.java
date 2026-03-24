package com.industry.iotdb.model.response;

public class ApiUsageExamples {

    private ApiUsageExamples() {
    }

    public static String examples() {
        return "POST /iotdb/write/single\n"
                + "{\n"
                + "  \"device\": \"root.machine.device01\",\n"
                + "  \"timestamp\": 1711267200000,\n"
                + "  \"fields\": [\n"
                + "    {\"measurement\": \"temperature\", \"dataType\": \"DOUBLE\", \"value\": 36.5},\n"
                + "    {\"measurement\": \"pressure\", \"dataType\": \"FLOAT\", \"value\": 1.2}\n"
                + "  ]\n"
                + "}\n\n"
                + "POST /iotdb/write/batch\n"
                + "{\n"
                + "  \"records\": [\n"
                + "    {\"device\": \"root.machine.device01\", \"timestamp\": 1711267200000, \"fields\": [{\"measurement\": \"temperature\", \"dataType\": \"DOUBLE\", \"value\": 36.5}]},\n"
                + "    {\"device\": \"root.machine.device01\", \"timestamp\": 1711267201000, \"fields\": [{\"measurement\": \"temperature\", \"dataType\": \"DOUBLE\", \"value\": 36.8}]}\n"
                + "  ]\n"
                + "}\n\n"
                + "POST /iotdb/query/single\n"
                + "{\n"
                + "  \"device\": \"root.machine.device01\",\n"
                + "  \"measurements\": [\"temperature\", \"pressure\"],\n"
                + "  \"startTime\": 1711267200000,\n"
                + "  \"endTime\": 1711268200000,\n"
                + "  \"limit\": 100,\n"
                + "  \"offset\": 0\n"
                + "}\n\n"
                + "POST /iotdb/update/single\n"
                + "{\n"
                + "  \"device\": \"root.machine.device01\",\n"
                + "  \"timestamp\": 1711267200000,\n"
                + "  \"fields\": [{\"measurement\": \"temperature\", \"dataType\": \"DOUBLE\", \"value\": 37.0}]\n"
                + "}\n\n"
                + "POST /iotdb/update/batch\n"
                + "{\n"
                + "  \"startTime\": 1711267200000,\n"
                + "  \"endTime\": 1711268200000,\n"
                + "  \"records\": [\n"
                + "    {\"device\": \"root.machine.device01\", \"timestamp\": 1711267200000, \"fields\": [{\"measurement\": \"temperature\", \"dataType\": \"DOUBLE\", \"value\": 37.0}]},\n"
                + "    {\"device\": \"root.machine.device01\", \"timestamp\": 1711267201000, \"fields\": [{\"measurement\": \"temperature\", \"dataType\": \"DOUBLE\", \"value\": 37.2}]}\n"
                + "  ]\n"
                + "}\n\n"
                + "DELETE /iotdb/delete\n"
                + "{\n"
                + "  \"device\": \"root.machine.device01\",\n"
                + "  \"measurements\": [\"temperature\"],\n"
                + "  \"startTime\": 1711267200000,\n"
                + "  \"endTime\": 1711268200000\n"
                + "}";
    }
}
