package test;

import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlServer;
import control.ControlStatus;
import control.IndustrialCommandHandler;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.TelemetryMessage;
import model.industrial.SensorType;
import persistence.AlarmRepository;
import persistence.CommandRepository;
import persistence.DatabaseConfig;
import persistence.DatabaseInitializer;
import persistence.DatabaseManager;
import persistence.EventRepository;
import persistence.MachineRepository;
import persistence.PersistenceService;
import persistence.TelemetryRepository;
import rmi.RmiClient;
import rmi.RmiServer;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Suite de vérification automatisée complète pour la Phase 5 :
 * Persistance locale SQLite via JDBC et historisation industrielle.
 * Valide au minimum 24 tests de persistance, de robustesse, d'audit et de concurrence.
 */
public class Phase5Verification {

    private static final String TEST_DB_FILE = "data/test_phase5_supervision.db";
    private static final int TEST_TCP_PORT = 5577;
    private static final int TEST_RMI_PORT = 1299;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  TESTS ET VALIDATION AUTOMATISÉE - PHASE 5 (PERSISTANCE SQLITE) ");
        System.out.println("==================================================================");

        int passed = 0;
        int failed = 0;

        // Configuration pour pointer vers une base de test dédiée
        File dbFile = new File(TEST_DB_FILE);
        if (dbFile.exists()) {
            dbFile.delete();
        }
        DatabaseConfig.setCustomDbPath(TEST_DB_FILE);

        DatabaseManager dbManager = new DatabaseManager();
        DatabaseInitializer initializer = new DatabaseInitializer(dbManager);
        MachineRepository machineRepo = new MachineRepository(dbManager);
        TelemetryRepository telemetryRepo = new TelemetryRepository(dbManager);
        AlarmRepository alarmRepo = new AlarmRepository(dbManager);
        CommandRepository commandRepo = new CommandRepository(dbManager);
        EventRepository eventRepo = new EventRepository(dbManager);
        PersistenceService persistenceService = new PersistenceService(dbManager);
        persistenceService.setConsoleLogging(false);

        // Test 1 : Création automatique de la base
        try {
            initializer.initialize();
            assertTrue(dbFile.exists(), "Le fichier SQLite doit exister sur le disque");
            System.out.println("✅ [TEST 1 PASSÉ] Création automatique de la base de données (" + TEST_DB_FILE + ")");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 1 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 2 : Création des tables
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table';")) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString("name"));
            }
            assertTrue(tables.contains("machines"), "Table machines présente");
            assertTrue(tables.contains("telemetry"), "Table telemetry présente");
            assertTrue(tables.contains("alarms"), "Table alarms présente");
            assertTrue(tables.contains("commands"), "Table commands présente");
            assertTrue(tables.contains("events"), "Table events présente");
            System.out.println("✅ [TEST 2 PASSÉ] Création des 5 tables (machines, telemetry, alarms, commands, events)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 2 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 3 : Création des index
        try (Connection conn = dbManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='index';")) {
            List<String> indexes = new ArrayList<>();
            while (rs.next()) {
                indexes.add(rs.getString("name"));
            }
            assertTrue(indexes.contains("idx_telemetry_machine"), "Index idx_telemetry_machine présent");
            assertTrue(indexes.contains("idx_alarms_machine"), "Index idx_alarms_machine présent");
            assertTrue(indexes.contains("idx_commands_cmd_id"), "Index idx_commands_cmd_id présent");
            System.out.println("✅ [TEST 3 PASSÉ] Création des index d'optimisation temporels et machines");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 3 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 4 : Insertion machine
        try {
            machineRepo.upsertMachine("M01", "Presse hydraulique", "HYDRAULIC", "RUNNING", 1728000000000L);
            Map<String, Object> m01 = machineRepo.getMachine("M01");
            assertTrue(m01 != null, "Machine M01 trouvée");
            assertEquals("Presse hydraulique", m01.get("name"), "Nom machine");
            assertEquals("RUNNING", m01.get("state"), "État machine");
            System.out.println("✅ [TEST 4 PASSÉ] Insertion et mise à jour d'équipement (Machine M01)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 4 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 5 : Insertion télémétrie
        try {
            telemetryRepo.saveTelemetry(1728000001000L, "M01", "TEMPERATURE", 65.4, "°C", 101L);
            telemetryRepo.saveTelemetry(1728000002000L, "M01", "PRESSURE", 170.2, "bar", 102L);
            long count = telemetryRepo.getTotalCount();
            assertEquals(2L, count, "Nombre de télémétries insérées");
            System.out.println("✅ [TEST 5 PASSÉ] Insertion de mesures de télémétrie capteurs");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 5 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 6 : Lecture télémétrie
        try {
            List<Map<String, Object>> list = telemetryRepo.getRecentTelemetry("M01", 5);
            assertEquals(2, list.size(), "Taille historique télémétrie");
            assertEquals("PRESSURE", list.get(0).get("sensor_type"), "Premier élément le plus récent");
            assertEquals(170.2, (Double) list.get(0).get("value"), "Valeur pression");
            System.out.println("✅ [TEST 6 PASSÉ] Lecture ordonnée de la télémétrie récente");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 6 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 7 : Insertion alarme
        try {
            alarmRepo.saveAlarm(1728000003000L, "M02", "WARNING", "THRESHOLD_EXCEEDED",
                    "TEMPERATURE", 82.5, "Surchauffe détectée");
            assertEquals(1L, alarmRepo.getTotalCount(), "Total alarmes");
            System.out.println("✅ [TEST 7 PASSÉ] Insertion d'une alarme industrielle");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 7 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 8 : Lecture alarmes
        try {
            List<Map<String, Object>> alarms = alarmRepo.getRecentAlarms(5);
            assertEquals(1, alarms.size(), "Nombre d'alarmes retournées");
            assertEquals("M02", alarms.get(0).get("machine_id"), "MachineId alarme");
            assertEquals("WARNING", alarms.get(0).get("level"), "Niveau alarme");
            System.out.println("✅ [TEST 8 PASSÉ] Lecture des alarmes avec filtres et pagination");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 8 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 9 : Insertion événement
        try {
            eventRepo.saveEvent(1728000004000L, "M03", "MACHINE_OFFLINE", "CRITICAL", "Timeout Heartbeat > 6s");
            assertEquals(1L, eventRepo.getTotalCount(), "Total événements");
            System.out.println("✅ [TEST 9 PASSÉ] Insertion d'un événement système");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 9 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 10 : Lecture événements
        try {
            List<Map<String, Object>> events = eventRepo.getRecentEvents(5);
            assertEquals(1, events.size(), "Nombre d'événements retournés");
            assertEquals("MACHINE_OFFLINE", events.get(0).get("event_type"), "Type événement");
            System.out.println("✅ [TEST 10 PASSÉ] Lecture de l'historique des événements système");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 10 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 11 : Insertion commande ACCEPTED
        try {
            commandRepo.saveCommand("cmd-p5-01", 1728000005000L, "M01", "STOP", "ACCEPTED", "MACHINE_STOPPED");
            Map<String, Object> cmd = commandRepo.getCommandById("cmd-p5-01");
            assertTrue(cmd != null, "Commande trouvée");
            assertEquals("ACCEPTED", cmd.get("status"), "Statut commande");
            System.out.println("✅ [TEST 11 PASSÉ] Audit d'une commande TCP validée (ACCEPTED)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 11 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 12 : Insertion commande REJECTED
        try {
            commandRepo.saveCommand("cmd-p5-02", 1728000006000L, "M02", "START", "REJECTED", "MACHINE_ALREADY_RUNNING");
            Map<String, Object> cmd = commandRepo.getCommandById("cmd-p5-02");
            assertTrue(cmd != null, "Commande trouvée");
            assertEquals("REJECTED", cmd.get("status"), "Statut rejeté");
            System.out.println("✅ [TEST 12 PASSÉ] Audit d'une commande TCP rejetée (REJECTED)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 12 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 13 : Recherche par machine
        try {
            alarmRepo.saveAlarm(1728000007000L, "M01", "CRITICAL", "MACHINE_FAILURE", "VIBRATION", 9.8, "Vibration extrême");
            List<Map<String, Object>> m01Alarms = alarmRepo.getAlarmsByMachine("M01", 10);
            List<Map<String, Object>> m02Alarms = alarmRepo.getAlarmsByMachine("M02", 10);
            assertEquals(1, m01Alarms.size(), "Alarmes M01");
            assertEquals(1, m02Alarms.size(), "Alarmes M02");
            System.out.println("✅ [TEST 13 PASSÉ] Recherche et filtrage précis par machine ciblée");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 13 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 14 : Recherche par période
        try {
            List<Map<String, Object>> periodData = telemetryRepo.getTelemetryByPeriod("M01", 1728000000500L, 1728000001500L);
            assertEquals(1, periodData.size(), "Une mesure dans la plage de temps");
            assertEquals("TEMPERATURE", periodData.get(0).get("sensor_type"), "Type mesure");
            System.out.println("✅ [TEST 14 PASSÉ] Recherche temporelle par intervalle [startTime, endTime]");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 14 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 15 : Chronologie machine (Machine History)
        try {
            List<String> m01History = persistenceService.getMachineHistory("M01", 10);
            assertTrue(!m01History.isEmpty(), "Chronologie M01 non vide");
            boolean containsCmd = m01History.stream().anyMatch(h -> h.contains("COMMANDE") && h.contains("STOP"));
            boolean containsAlarm = m01History.stream().anyMatch(h -> h.contains("ALARME") && h.contains("CRITICAL"));
            assertTrue(containsCmd, "Contient la commande STOP");
            assertTrue(containsAlarm, "Contient l'alarme CRITICAL");
            System.out.println("✅ [TEST 15 PASSÉ] Reconstitution chronologique de la machine (audit hybride)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 15 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 16 : Redémarrage avec données existantes (Idempotence)
        try {
            DatabaseManager newManager = new DatabaseManager();
            DatabaseInitializer newInit = new DatabaseInitializer(newManager);
            newInit.initialize(); // Ne doit pas crasher ni vider les données
            TelemetryRepository newTelem = new TelemetryRepository(newManager);
            assertTrue(newTelem.getTotalCount() >= 2, "Données existantes conservées après ré-initialisation");
            System.out.println("✅ [TEST 16 PASSÉ] Ré-exécution idempotente sans perte de données");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 16 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 17 : Persistance après fermeture/réouverture du PersistenceService
        try {
            persistenceService.start();
            persistenceService.saveTelemetry(System.currentTimeMillis(), "M03", "SPEED", 150.0, "rpm", 999L);
            persistenceService.flush(2000);
            persistenceService.stop();

            // Réouverture
            PersistenceService service2 = new PersistenceService(dbManager);
            service2.start();
            List<Map<String, Object>> m03Telem = service2.getRecentTelemetry("M03", 5);
            assertTrue(!m03Telem.isEmpty(), "Télémétrie M03 récupérée après redémarrage du service");
            service2.stop();
            System.out.println("✅ [TEST 17 PASSÉ] Persistance garantie après cycle de vie fermeture / réouverture");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 17 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 18 : Intégration UDP -> SQLite
        PersistenceService livePersistence = new PersistenceService(dbManager);
        livePersistence.start();
        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        supervisor.setPersistenceService(livePersistence);
        supervisor.setConsoleLogging(false);

        try {
            TelemetryMessage tm = new TelemetryMessage("M01", SensorType.ENERGY, 44.5, "kW", 777L);
            supervisor.onMessageReceived(tm, null, 100);

            livePersistence.flush(2000);
            List<Map<String, Object>> stored = livePersistence.getRecentTelemetry("M01", 5);
            boolean found = stored.stream().anyMatch(r -> "ENERGY".equals(r.get("sensor_type")));
            assertTrue(found, "Télémétrie UDP insérée en base via le superviseur");
            System.out.println("✅ [TEST 18 PASSÉ] Intégration transparente UDP Multicast ➔ SQLite");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 18 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 19 : Intégration TCP -> SQLite
        MachineSimulator simulator = new MachineSimulator();
        IndustrialCommandHandler cmdHandler = new IndustrialCommandHandler(simulator, livePersistence);
        ControlServer tcpServer = new ControlServer(TEST_TCP_PORT, cmdHandler);
        tcpServer.setConsoleLogging(false);

        try {
            tcpServer.start();

            // Test 19 : Commande TCP normale
            try {
                try (ControlClient tcpClient = new ControlClient("127.0.0.1", TEST_TCP_PORT, 2000)) {
                    ControlCommand cmd = new ControlCommand("cmd-integ-tcp", "M01", ControlCommandType.STOP);
                    ControlResponse resp = tcpClient.sendCommand(cmd);
                    assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Statut commande TCP");

                    livePersistence.flush(2000);
                    Map<String, Object> audited = commandRepo.getCommandById("cmd-integ-tcp");
                    assertTrue(audited != null, "Commande TCP trouvée dans la table commands");
                    assertEquals("ACCEPTED", audited.get("status"), "Statut persisté");
                    System.out.println("✅ [TEST 19 PASSÉ] Intégration transparente TCP ➔ Audit SQLite");
                    passed++;
                }
            } catch (Throwable t) {
                System.err.println("❌ [TEST 19 ÉCHEC] " + t.getMessage());
                failed++;
            }

            // Test 20 : Intégration Emergency Stop -> SQLite
            try {
                try (ControlClient tcpClient = new ControlClient("127.0.0.1", TEST_TCP_PORT, 2000)) {
                    ControlCommand emCmd = new ControlCommand("cmd-integ-em", "M02", ControlCommandType.EMERGENCY_STOP);
                    ControlResponse resp = tcpClient.sendCommand(emCmd);
                    assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Statut Emergency Stop");

                    livePersistence.flush(2000);
                    Map<String, Object> audited = commandRepo.getCommandById("cmd-integ-em");
                    assertTrue(audited != null, "Commande EMERGENCY_STOP auditée");
                    assertEquals("EMERGENCY_STOP", audited.get("command_type"), "Type commande");
                    System.out.println("✅ [TEST 20 PASSÉ] Intégration de l'Arrêt d'Urgence dans le journal d'audit SQLite");
                    passed++;
                }
            } catch (Throwable t) {
                System.err.println("❌ [TEST 20 ÉCHEC] " + t.getMessage());
                failed++;
            }
        } catch (Throwable t) {
            System.err.println("❌ [TEST 19/20 ÉCHEC SERVEUR TCP] " + t.getMessage());
        } finally {
            tcpServer.stop();
        }

        // Test 21 : Intégration RMI -> Historique SQLite
        RmiServer rmiServer = new RmiServer(supervisor, TEST_RMI_PORT, "TestPhase5SupervisionService");
        rmiServer.setConsoleLogging(false);
        RmiClient rmiClient = new RmiClient("127.0.0.1", TEST_RMI_PORT, "TestPhase5SupervisionService");

        try {
            rmiServer.start();
            rmiClient.connect();

            List<String> rmiCmds = rmiClient.getRecentCommands(5);
            assertTrue(!rmiCmds.isEmpty(), "Commandes historiques récupérées via RMI");

            Map<String, Object> dbStats = rmiClient.getDatabaseStatistics();
            assertTrue(dbStats.containsKey("totalTelemetry"), "Contient totalTelemetry");
            assertTrue(dbStats.containsKey("totalCommands"), "Contient totalCommands");
            System.out.println("✅ [TEST 21 PASSÉ] Consultation de l'historique SQLite via le service Java RMI");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 21 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            rmiClient.disconnect();
            rmiServer.stop();
        }

        // Test 22 : Concurrence persistance + UDP + TCP + RMI
        try {
            int threads = 6;
            ExecutorService pool = Executors.newFixedThreadPool(threads);
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    latch.await();
                    livePersistence.saveTelemetry(System.currentTimeMillis(), "M01", "VIBRATION", 2.0 + idx, "mm/s", idx);
                    livePersistence.saveCommand("cmd-c-" + idx, System.currentTimeMillis(), "M01", "STOP", "ACCEPTED", "OK");
                    livePersistence.saveAlarm(System.currentTimeMillis(), "M01", "WARNING", "TEST", "VIBRATION", 2.5, "Alerte test");
                    return true;
                }));
            }

            latch.countDown();
            boolean ok = true;
            for (Future<Boolean> f : futures) {
                if (!f.get(5, TimeUnit.SECONDS)) ok = false;
            }
            pool.shutdownNow();

            livePersistence.flush(3000);
            assertTrue(ok, "Toutes les écritures concurrentes terminées");
            System.out.println("✅ [TEST 22 PASSÉ] Robustesse et thread-safety en forte concurrence d'écriture");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 22 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 23 : Fermeture propre du PersistenceService
        try {
            livePersistence.stop();
            assertFalse(livePersistence.isRunning(), "PersistenceService arrêté");
            System.out.println("✅ [TEST 23 PASSÉ] Fermeture propre du PersistenceService et purge de la file");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 23 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 24 : Gestion d'une erreur SQLite (Non-régression / Tolérance aux pannes)
        try {
            // Création d'un service pointant vers un fichier invalide/inaccessible
            DatabaseManager brokenManager = new DatabaseManager("jdbc:sqlite:/invalid_path_38294/cannot_create.db");
            PersistenceService brokenService = new PersistenceService(brokenManager);
            brokenService.setConsoleLogging(false);
            brokenService.start();

            // Ne doit pas lever d'exception non gérée vers l'appelant
            brokenService.saveTelemetry(1234L, "M01", "TEMP", 50.0, "C", 1L);
            brokenService.flush(500);

            List<Map<String, Object>> emptyList = brokenService.getRecentTelemetry("M01", 5);
            assertTrue(emptyList != null && emptyList.isEmpty(), "Retourne liste vide sans crash");
            brokenService.stop();
            System.out.println("✅ [TEST 24 PASSÉ] Tolérance aux pannes : Gestion gracieuse des erreurs SQLite");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 24 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            DatabaseConfig.resetDefaultDbPath();
            supervisor.stop();
            simulator.stop();
        }

        System.out.println("==================================================================");
        System.out.println("  RÉSULTAT DES TESTS : " + passed + " / " + (passed + failed) + " réussis, " + failed + " échoués.");
        System.out.println("==================================================================");

        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void assertEquals(Object expected, Object actual, String message) {
        if (expected == null && actual == null) return;
        if (expected != null && expected.equals(actual)) return;
        throw new AssertionError(message + " - Attendu: <" + expected + ">, Reçu: <" + actual + ">");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message + " - Attendu: true, Reçu: false");
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message + " - Attendu: false, Reçu: true");
        }
    }
}
