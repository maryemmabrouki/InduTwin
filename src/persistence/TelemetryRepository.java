package persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dépôt d'accès aux données de télémétrie industrielle (mesures de capteurs).
 */
public class TelemetryRepository {

    private final DatabaseManager dbManager;

    public TelemetryRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void saveTelemetry(long ts, String machineId, String sensorType, double value, String unit, long seq) throws SQLException {
        String sql = "INSERT INTO telemetry (timestamp, machine_id, sensor_type, value, unit, sequence_number) " +
                "VALUES (?, ?, ?, ?, ?, ?);";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, ts);
            stmt.setString(2, machineId);
            stmt.setString(3, sensorType);
            stmt.setDouble(4, value);
            stmt.setString(5, unit);
            stmt.setLong(6, seq);
            stmt.executeUpdate();
        }
    }

    public List<Map<String, Object>> getRecentTelemetry(String machineId, int limit) throws SQLException {
        String sql = (machineId != null && !machineId.isBlank()) ?
                "SELECT id, timestamp, machine_id, sensor_type, value, unit, sequence_number FROM telemetry " +
                        "WHERE machine_id = ? ORDER BY timestamp DESC LIMIT ?;" :
                "SELECT id, timestamp, machine_id, sensor_type, value, unit, sequence_number FROM telemetry " +
                        "ORDER BY timestamp DESC LIMIT ?;";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (machineId != null && !machineId.isBlank()) {
                stmt.setString(1, machineId);
                stmt.setInt(2, Math.max(1, limit));
            } else {
                stmt.setInt(1, Math.max(1, limit));
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> getTelemetryByPeriod(String machineId, long startTime, long endTime) throws SQLException {
        String sql = (machineId != null && !machineId.isBlank()) ?
                "SELECT id, timestamp, machine_id, sensor_type, value, unit, sequence_number FROM telemetry " +
                        "WHERE machine_id = ? AND timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC;" :
                "SELECT id, timestamp, machine_id, sensor_type, value, unit, sequence_number FROM telemetry " +
                        "WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC;";

        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            int p = 1;
            if (machineId != null && !machineId.isBlank()) {
                stmt.setString(p++, machineId);
            }
            stmt.setLong(p++, startTime);
            stmt.setLong(p, endTime);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public long getTotalCount() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM telemetry;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getLong("total");
            }
        }
        return 0L;
    }

    private Map<String, Object> mapRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("timestamp", rs.getLong("timestamp"));
        row.put("machine_id", rs.getString("machine_id"));
        row.put("sensor_type", rs.getString("sensor_type"));
        row.put("value", rs.getDouble("value"));
        row.put("unit", rs.getString("unit"));
        row.put("sequence_number", rs.getLong("sequence_number"));
        return row;
    }
}
