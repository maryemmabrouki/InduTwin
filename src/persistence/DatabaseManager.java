package persistence;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Gestionnaire des connexions JDBC SQLite.
 * Assure le chargement du pilote, la création des répertoires parents
 * et l'application des pragmas de performance et concurrence (WAL, busy_timeout).
 */
public class DatabaseManager implements AutoCloseable {

    private final String jdbcUrl;
    private static boolean driverLoaded = false;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
            driverLoaded = true;
        } catch (ClassNotFoundException e) {
            System.err.println("[DatabaseManager] Pilote SQLite JDBC non trouvé: " + e.getMessage());
        }
    }

    public DatabaseManager() {
        this(DatabaseConfig.getJdbcUrl());
    }

    public DatabaseManager(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        ensureParentDirectory();
    }

    private void ensureParentDirectory() {
        String dbPath = DatabaseConfig.getDbPath();
        if (!":memory:".equalsIgnoreCase(dbPath)) {
            File file = new File(dbPath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
        }
    }

    /**
     * Ouvre une nouvelle connexion JDBC avec configuration WAL et timeout.
     */
    public Connection getConnection() throws SQLException {
        if (!driverLoaded) {
            throw new SQLException("Pilote SQLite JDBC non disponible");
        }

        Connection conn = DriverManager.getConnection(jdbcUrl);
        try (Statement stmt = conn.createStatement()) {
            // Configuration pour concurrence lecture/écriture optimale
            stmt.execute("PRAGMA journal_mode = WAL;");
            stmt.execute("PRAGMA busy_timeout = 5000;");
            stmt.execute("PRAGMA synchronous = NORMAL;");
        } catch (SQLException ignored) {
            // Certaines options ne s'appliquent pas sur bases en mémoire
        }
        return conn;
    }

    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    @Override
    public void close() {
        // En SQLite fichier, les connexions sont fermées individuellement
    }
}
