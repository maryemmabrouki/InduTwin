package test;

import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlStatus;
import industrial.InduTwinServer;
import persistence.DatabaseConfig;
import rmi.RmiClient;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;

/**
 * Test de non-régression et de validation du cycle de vie du serveur autonome InduTwinServer.
 * Totalement isolé dans une base SQLite temporaire dédiée (aucun accès à la base de production).
 * Valide également le rollback en cas d'échec partiel au démarrage.
 */
public class InduTwinServerTest {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  TEST DE VALIDATION DU CYCLE DE VIE - INDUTWINSERVER             ");
        System.out.println("==================================================================");

        int passed = 0;
        int failed = 0;

        File tempDbFile = null;
        InduTwinServer server = null;

        try {
            // Configuration stricte d'une base SQLite temporaire AVANT toute instanciation
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            tempDbFile = File.createTempFile("test_indutwin_srv_", ".db", dataDir);
            tempDbFile.deleteOnExit();

            File prodDbFile = new File(DatabaseConfig.DEFAULT_DB_PATH);
            String tempCanonical = tempDbFile.getCanonicalPath();
            String prodCanonical = prodDbFile.getCanonicalPath();

            if (tempCanonical.equalsIgnoreCase(prodCanonical)) {
                throw new IllegalStateException("ALERTE SÉCURITÉ : Le fichier temporaire correspond à la base réelle !");
            }

            DatabaseConfig.setCustomDbPath(tempDbFile.getPath());
            System.out.println("[SÉCURITÉ] Base SQLite isolée pour le test : " + tempDbFile.getPath());

            server = new InduTwinServer();
            server.setConsoleLogging(false);

            // 1. Démarrage nominal du serveur
            System.out.println("\n[1] Démarrage du serveur autonome...");
            server.start();

            if (server.isRunning()
                    && server.getSupervisor().isRunning()
                    && server.getSimulator().isRunning()
                    && server.getControlServer().isRunning()
                    && server.getRmiServer().isRunning()
                    && server.getPersistenceService().isRunning()) {
                System.out.println("  ✔ [TEST 1 PASSÉ] Tous les composants sont démarrés et actifs.");
                passed++;
            } else {
                System.err.println("  ✖ [TEST 1 ÉCHEC] Un composant n'est pas démarré.");
                failed++;
            }

            // 2. Vérification du canal TCP 5556
            System.out.println("\n[2] Test du canal de contrôle TCP (port 5556)...");
            try (ControlClient tcpClient = new ControlClient("127.0.0.1", 5556)) {
                ControlCommand cmd = new ControlCommand("test-srv-1", "M01", ControlCommandType.STOP);
                ControlResponse resp = tcpClient.sendCommand(cmd);
                if (resp != null && resp.getStatus() == ControlStatus.ACCEPTED) {
                    System.out.println("  ✔ [TEST 2 PASSÉ] Commande TCP traitée avec succès par le serveur autonome.");
                    passed++;
                } else {
                    System.err.println("  ✖ [TEST 2 ÉCHEC] Réponse TCP invalide : " + resp);
                    failed++;
                }
            }

            // 3. Vérification du canal Java RMI 1099
            System.out.println("\n[3] Test de la supervision distante RMI (port 1099)...");
            RmiClient rmiClient = new RmiClient("127.0.0.1", 1099);
            rmiClient.connect();
            Map<String, String> statuses = rmiClient.getAllMachineStatuses();
            if (statuses != null && statuses.containsKey("M01") && statuses.containsKey("M02") && statuses.containsKey("M03")) {
                System.out.println("  ✔ [TEST 3 PASSÉ] États machines consultés avec succès via RMI : " + statuses);
                passed++;
            } else {
                System.err.println("  ✖ [TEST 3 ÉCHEC] Échec consultation RMI : " + statuses);
                failed++;
            }

            // 4. Arrêt propre du serveur
            System.out.println("\n[4] Arrêt propre du serveur...");
            server.stop();

            if (!server.isRunning()
                    && !server.getControlServer().isRunning()
                    && !server.getRmiServer().isRunning()
                    && !server.getSimulator().isRunning()
                    && !server.getSupervisor().isRunning()
                    && !server.getPersistenceService().isRunning()) {
                System.out.println("  ✔ [TEST 4 PASSÉ] Tous les composants sont arrêtés et ressources libérées.");
                passed++;
            } else {
                System.err.println("  ✖ [TEST 4 ÉCHEC] Un composant est resté actif après stop().");
                failed++;
            }

            // 5. Vérification d'idempotence (double arrêt sécurisé)
            server.stop();
            System.out.println("  ✔ [TEST 5 PASSÉ] Double arrêt idempotent sans exception.");
            passed++;

            // 6. Test du rollback automatique en cas d'échec partiel au démarrage
            System.out.println("\n[6] Test du rollback automatique en cas d'échec partiel au démarrage...");
            ServerSocket blockerSocket = null;
            InduTwinServer failingServer = null;
            try {
                // On bloque le port 5556 pour provoquer un échec à l'étape 4 de start()
                blockerSocket = new ServerSocket(5556);
                failingServer = new InduTwinServer();
                failingServer.setConsoleLogging(false);

                boolean exceptionThrown = false;
                try {
                    failingServer.start();
                } catch (IOException expected) {
                    exceptionThrown = true;
                }

                // Vérifications du rollback :
                // L'exception a été propagée et tous les services démarrés en amont sont immédiatement arrêtés
                if (exceptionThrown
                        && !failingServer.isRunning()
                        && !failingServer.getSimulator().isRunning()
                        && !failingServer.getSupervisor().isRunning()
                        && !failingServer.getPersistenceService().isRunning()
                        && !failingServer.getControlServer().isRunning()) {
                    System.out.println("  ✔ [TEST 6 PASSÉ] Rollback validé : services arrêtés et exception préservée.");
                    passed++;
                } else {
                    System.err.println("  ✖ [TEST 6 ÉCHEC] Échec du rollback après collision de port.");
                    failed++;
                }
            } finally {
                if (failingServer != null) {
                    try {
                        failingServer.stop();
                    } catch (Exception ignored) {}
                }
                if (blockerSocket != null && !blockerSocket.isClosed()) {
                    try {
                        blockerSocket.close();
                    } catch (IOException ignored) {}
                }
            }

        } catch (Exception e) {
            System.err.println("✖ Exception inattendue : " + e.getMessage());
            e.printStackTrace();
            failed++;
        } finally {
            if (server != null && server.isRunning()) {
                try {
                    server.stop();
                } catch (Exception ignored) {}
            }

            // Restauration impérative du chemin par défaut de la base
            DatabaseConfig.resetDefaultDbPath();

            // Nettoyage sécurisé du fichier temporaire et de ses fichiers auxiliaires
            cleanupTestDatabase(tempDbFile);
        }

        System.out.println("\n==================================================================");
        System.out.printf("  BILAN DU SERVEUR : %d / %d tests réussis, %d échoués.%n", passed, (passed + failed), failed);
        System.out.println("==================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void cleanupTestDatabase(File tempDbFile) {
        if (tempDbFile == null) return;
        try {
            File prodDbFile = new File(DatabaseConfig.DEFAULT_DB_PATH);
            String tempCanonical = tempDbFile.getCanonicalPath();
            String prodCanonical = prodDbFile.getCanonicalPath();

            // Refus catégorique si les chemins correspondent à la base réelle
            if (tempCanonical.equalsIgnoreCase(prodCanonical)) {
                System.err.println("❌ [SÉCURITÉ] Tentative de suppression de la base réelle bloquée !");
                return;
            }

            if (!tempDbFile.getName().startsWith("test_indutwin_srv_") || !tempDbFile.getName().endsWith(".db")) {
                System.err.println("❌ [SÉCURITÉ] Fichier non reconnu comme base de test : " + tempDbFile.getName());
                return;
            }

            // Suppression de la base principale
            if (tempDbFile.exists()) {
                if (!tempDbFile.delete()) {
                    tempDbFile.deleteOnExit();
                }
            }

            // Suppression des fichiers auxiliaires SQLite WAL (-wal et -shm)
            File walFile = new File(tempDbFile.getPath() + "-wal");
            if (walFile.exists()) {
                if (!walFile.delete()) {
                    walFile.deleteOnExit();
                }
            }

            File shmFile = new File(tempDbFile.getPath() + "-shm");
            if (shmFile.exists()) {
                if (!shmFile.delete()) {
                    shmFile.deleteOnExit();
                }
            }
        } catch (Exception e) {
            System.err.println("Avertissement nettoyage base temporaire: " + e.getMessage());
        }
    }
}
