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
 * Dépôt d'accès aux données des événements système et changements d'états.
 */
public class EventRepository {

    private final DatabaseManager dbManager;

    public EventRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void saveEvent(long ts, String machineId, String eventType, String level, String desc) throws SQLException {
        String sql = "INSERT INTO events (timestamp, machine_id, event_type, level, description) " +
                "VALUES (?, ?, ?, ?, ?);";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, ts);
            stmt.setString(2, machineId);
            stmt.setString(3, eventType != null ? eventType : "");
            stmt.setString(4, level != null ? level : "INFO");
            stmt.setString(5, desc != null ? desc : "");
            stmt.executeUpdate();
        }
    }

    public List<Map<String, Object>> getRecentEvents(int limit) throws SQLException {
        String sql = "SELECT id, timestamp, machine_id, event_type, level, description " +
                "FROM events ORDER BY timestamp DESC LIMIT ?;";
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

    public List<Map<String, Object>> getEventsByMachine(String machineId, int limit) throws SQLException {
        String sql = "SELECT id, timestamp, machine_id, event_type, level, description " +
                "FROM events WHERE machine_id = ? ORDER BY timestamp DESC LIMIT ?;";
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

    public long getTotalCount() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM events;";
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
        row.put("event_type", rs.getString("event_type"));
        row.put("level", rs.getString("level"));
        row.put("description", rs.getString("description"));
        return row;
    }
}
