package persistence;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initialiseur idempotent du schéma relationnel SQLite.
 * Crée les tables et index pour les machines, la télémétrie, les alarmes, les commandes et les événements.
 */
public class DatabaseInitializer {

    private final DatabaseManager dbManager;

    public DatabaseInitializer(DatabaseManager dbManager) {
        this.dbManager = dbManager;
    }

    /**
     * Exécute les scripts de création des tables et des index.
     */
    public void initialize() throws SQLException {
        try (Connection conn = dbManager.getConnection()) {
            initialize(conn);
        }
    }

    public static void initialize(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {

            // 1. Table des équipements industriels (Machines)
            stmt.execute("CREATE TABLE IF NOT EXISTS machines (" +
                    "machine_id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "type TEXT, " +
                    "state TEXT NOT NULL, " +
                    "last_seen INTEGER NOT NULL" +
                    ");");

            // 2. Table de télémétrie industrielle (Capteurs & mesures)
            stmt.execute("CREATE TABLE IF NOT EXISTS telemetry (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "machine_id TEXT NOT NULL, " +
                    "sensor_type TEXT NOT NULL, " +
                    "value REAL NOT NULL, " +
                    "unit TEXT NOT NULL, " +
                    "sequence_number INTEGER NOT NULL" +
                    ");");

            // 3. Table des alarmes et dépassements de seuils
            stmt.execute("CREATE TABLE IF NOT EXISTS alarms (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "machine_id TEXT NOT NULL, " +
                    "level TEXT NOT NULL, " +
                    "event_type TEXT NOT NULL, " +
                    "sensor_type TEXT, " +
                    "value REAL, " +
                    "description TEXT NOT NULL" +
                    ");");

            // 4. Table d'audit des commandes TCP point-à-point
            stmt.execute("CREATE TABLE IF NOT EXISTS commands (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "command_id TEXT NOT NULL, " +
                    "timestamp INTEGER NOT NULL, " +
                    "machine_id TEXT NOT NULL, " +
                    "command_type TEXT NOT NULL, " +
                    "status TEXT NOT NULL, " +
                    "message TEXT" +
                    ");");

            // 5. Table de journalisation des événements système
            stmt.execute("CREATE TABLE IF NOT EXISTS events (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "timestamp INTEGER NOT NULL, " +
                    "machine_id TEXT NOT NULL, " +
                    "event_type TEXT NOT NULL, " +
                    "level TEXT, " +
                    "description TEXT NOT NULL" +
                    ");");

            // Index pour l'optimisation des requêtes par machine et temporelles
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_machine ON telemetry(machine_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_timestamp ON telemetry(timestamp);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_telemetry_machine_time ON telemetry(machine_id, timestamp);");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_alarms_machine ON alarms(machine_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_alarms_timestamp ON alarms(timestamp);");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_commands_machine ON commands(machine_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_commands_timestamp ON commands(timestamp);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_commands_cmd_id ON commands(command_id);");

            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_machine ON events(machine_id);");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_events_timestamp ON events(timestamp);");
        }
    }
}
