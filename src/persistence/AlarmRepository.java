package persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dépôt d'accès aux données des alarmes industrielles et dépassements de seuils.
 */
public class AlarmRepository {

    private final DatabaseManager dbManager;

    public AlarmRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void saveAlarm(long ts, String machineId, String level, String eventType,
                          String sensorType, Double value, String desc) throws SQLException {
        String sql = "INSERT INTO alarms (timestamp, machine_id, level, event_type, sensor_type, value, description) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?);";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, ts);
            stmt.setString(2, machineId);
            stmt.setString(3, level != null ? level : "WARNING");
            stmt.setString(4, eventType != null ? eventType : "ALARM");
            stmt.setString(5, sensorType);
            if (value != null) {
                stmt.setDouble(6, value);
            } else {
                stmt.setNull(6, Types.DOUBLE);
            }
            stmt.setString(7, desc != null ? desc : "");
            stmt.executeUpdate();
        }
    }

    public List<Map<String, Object>> getRecentAlarms(int limit) throws SQLException {
        String sql = "SELECT id, timestamp, machine_id, level, event_type, sensor_type, value, description " +
                "FROM alarms ORDER BY timestamp DESC LIMIT ?;";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> getAlarmsByMachine(String machineId, int limit) throws SQLException {
        String sql = "SELECT id, timestamp, machine_id, level, event_type, sensor_type, value, description " +
                "FROM alarms WHERE machine_id = ? ORDER BY timestamp DESC LIMIT ?;";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, machineId);
            stmt.setInt(2, Math.max(1, limit));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public List<Map<String, Object>> getAlarmsByPeriod(long startTime, long endTime) throws SQLException {
        String sql = "SELECT id, timestamp, machine_id, level, event_type, sensor_type, value, description " +
                "FROM alarms WHERE timestamp >= ? AND timestamp <= ? ORDER BY timestamp ASC;";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, startTime);
            stmt.setLong(2, endTime);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        }
        return list;
    }

    public Map<String, Integer> getAlarmCountsByLevel() throws SQLException {
        String sql = "SELECT level, COUNT(*) AS count FROM alarms GROUP BY level;";
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("level"), rs.getInt("count"));
            }
        }
        return counts;
    }

    public long getTotalCount() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM alarms;";
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
        row.put("level", rs.getString("level"));
        row.put("event_type", rs.getString("event_type"));
        row.put("sensor_type", rs.getString("sensor_type"));
        double val = rs.getDouble("value");
        row.put("value", rs.wasNull() ? null : val);
        row.put("description", rs.getString("description"));
        return row;
    }
}
