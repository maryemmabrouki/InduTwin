package persistence;

import java.io.File;

/**
 * Configuration centralisée de la persistance locale SQLite.
 * Définit le chemin du fichier de base de données et l'URL JDBC.
 */
public final class DatabaseConfig {

    public static final String DEFAULT_DB_DIR = "data";
    public static final String DEFAULT_DB_NAME = "industrial_supervision.db";
    public static final String DEFAULT_DB_PATH = DEFAULT_DB_DIR + File.separator + DEFAULT_DB_NAME;

    private static String customDbPath = null;

    private DatabaseConfig() {
        // Classe utilitaire non instanciable
    }

    /**
     * Définit un chemin personnalisé (utile pour les tests isolés ou fichiers temporaires).
     */
    public static synchronized void setCustomDbPath(String path) {
        customDbPath = path;
    }

    /**
     * Réinitialise le chemin vers le chemin par défaut.
     */
    public static synchronized void resetDefaultDbPath() {
        customDbPath = null;
    }

    /**
     * Retourne le chemin physique du fichier SQLite.
     */
    public static synchronized String getDbPath() {
        return customDbPath != null ? customDbPath : DEFAULT_DB_PATH;
    }

    /**
     * Retourne l'URL JDBC pour SQLite.
     */
    public static synchronized String getJdbcUrl() {
        String path = getDbPath();
        if (":memory:".equalsIgnoreCase(path)) {
            return "jdbc:sqlite::memory:";
        }
        return "jdbc:sqlite:" + path;
    }
}
