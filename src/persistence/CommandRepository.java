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
 * Dépôt d'accès aux données pour l'audit des commandes TCP point-à-point.
 */
public class CommandRepository {

    private final DatabaseManager dbManager;

    public CommandRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void saveCommand(String cmdId, long ts, String machineId, String cmdType,
                            String status, String message) throws SQLException {
        String sql = "INSERT INTO commands (command_id, timestamp, machine_id, command_type, status, message) " +
                "VALUES (?, ?, ?, ?, ?, ?);";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cmdId != null ? cmdId : "");
            stmt.setLong(2, ts);
            stmt.setString(3, machineId != null ? machineId : "");
            stmt.setString(4, cmdType != null ? cmdType : "");
            stmt.setString(5, status != null ? status : "");
            stmt.setString(6, message != null ? message : "");
            stmt.executeUpdate();
        }
    }

    public List<Map<String, Object>> getRecentCommands(int limit) throws SQLException {
        String sql = "SELECT id, command_id, timestamp, machine_id, command_type, status, message " +
                "FROM commands ORDER BY timestamp DESC LIMIT ?;";
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

    public List<Map<String, Object>> getCommandsByMachine(String machineId, int limit) throws SQLException {
        String sql = "SELECT id, command_id, timestamp, machine_id, command_type, status, message " +
                "FROM commands WHERE machine_id = ? ORDER BY timestamp DESC LIMIT ?;";
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

    public Map<String, Object> getCommandById(String cmdId) throws SQLException {
        String sql = "SELECT id, command_id, timestamp, machine_id, command_type, status, message " +
                "FROM commands WHERE command_id = ? LIMIT 1;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, cmdId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    public Map<String, Integer> getCommandCountsByStatus() throws SQLException {
        String sql = "SELECT status, COUNT(*) AS count FROM commands GROUP BY status;";
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                counts.put(rs.getString("status"), rs.getInt("count"));
            }
        }
        return counts;
    }

    public long getTotalCount() throws SQLException {
        String sql = "SELECT COUNT(*) AS total FROM commands;";
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
        row.put("command_id", rs.getString("command_id"));
        row.put("timestamp", rs.getLong("timestamp"));
        row.put("machine_id", rs.getString("machine_id"));
        row.put("command_type", rs.getString("command_type"));
        row.put("status", rs.getString("status"));
        row.put("message", rs.getString("message"));
        return row;
    }
}
