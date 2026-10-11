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
 * Dépôt d'accès aux données pour les équipements industriels (Machines).
 */
public class MachineRepository {

    private final DatabaseManager dbManager;

    public MachineRepository(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    public void upsertMachine(String id, String name, String type, String state, long lastSeen) throws SQLException {
        String sql = "INSERT INTO machines (machine_id, name, type, state, last_seen) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(machine_id) DO UPDATE SET " +
                "name = coalesce(excluded.name, machines.name), " +
                "type = coalesce(excluded.type, machines.type), " +
                "state = excluded.state, " +
                "last_seen = excluded.last_seen;";

        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, name != null ? name : id);
            stmt.setString(3, type != null ? type : "MACHINE");
            stmt.setString(4, state != null ? state : "UNKNOWN");
            stmt.setLong(5, lastSeen);
            stmt.executeUpdate();
        }
    }

    public Map<String, Object> getMachine(String id) throws SQLException {
        String sql = "SELECT machine_id, name, type, state, last_seen FROM machines WHERE machine_id = ?;";
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("machine_id", rs.getString("machine_id"));
                    row.put("name", rs.getString("name"));
                    row.put("type", rs.getString("type"));
                    row.put("state", rs.getString("state"));
                    row.put("last_seen", rs.getLong("last_seen"));
                    return row;
                }
            }
        }
        return null;
    }

    public List<Map<String, Object>> getAllMachines() throws SQLException {
        String sql = "SELECT machine_id, name, type, state, last_seen FROM machines ORDER BY machine_id ASC;";
        List<Map<String, Object>> list = new ArrayList<>();
        try (Connection conn = dbManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("machine_id", rs.getString("machine_id"));
                row.put("name", rs.getString("name"));
                row.put("type", rs.getString("type"));
                row.put("state", rs.getString("state"));
                row.put("last_seen", rs.getLong("last_seen"));
                list.add(row);
            }
        }
        return list;
    }
}
