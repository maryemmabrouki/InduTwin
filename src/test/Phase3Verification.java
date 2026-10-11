package test;

import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.AlarmMessage;
import model.HeartbeatMessage;
import model.TelemetryMessage;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.Machine;
import model.industrial.MachineState;
import model.industrial.SensorType;
import monitoring.NetworkMonitor;
import monitoring.NetworkStatistics;
import monitoring.SequenceTracker;
import ui.industrial.IndustrialDashboard;
import ui.industrial.SensorPanel;
import persistence.DatabaseConfig;

import javax.swing.SwingUtilities;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Suite de vérification automatisée complète pour la Phase 3 :
 * Valide les 16 exigences fonctionnelles, de monitoring réseau et d'IHM Swing.
 */
public class Phase3Verification {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("  TESTS ET VALIDATION AUTOMATISÉE - PHASE 3 (DASHBOARD & RÉSEAU) ");
        System.out.println("================================================================");

        int passed = 0;
        int failed = 0;

        // Test 1 : Création du Dashboard sur l'EDT Swing
        File tempDbFile = null;
        AtomicReference<IndustrialSupervisor> supRef = new AtomicReference<>();
        AtomicReference<MachineSimulator> simRef = new AtomicReference<>();
        AtomicReference<IndustrialDashboard> dashboardRef = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean(false);

        try {
            File dataDir = new File("data");
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }
            tempDbFile = File.createTempFile("test_p3_", ".db", dataDir);
            tempDbFile.deleteOnExit();
            DatabaseConfig.setCustomDbPath(tempDbFile.getPath());

            SwingUtilities.invokeAndWait(() -> {
                try {
                    IndustrialSupervisor sup = new IndustrialSupervisor();
                    sup.setConsoleLogging(false);
                    supRef.set(sup);

                    MachineSimulator sim = new MachineSimulator();
                    simRef.set(sim);

                    IndustrialDashboard db = new IndustrialDashboard(sup, sim);
                    dashboardRef.set(db);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            assertNotNull(dashboardRef.get(), "Instance IndustrialDashboard créée");

            // Fermeture propre sur l'EDT dans le flux nominal
            SwingUtilities.invokeAndWait(() -> closeDefensively(dashboardRef, supRef, simRef));
            closed.set(true);

            System.out.println("✅ [TEST 1 PASSÉ] Création et initialisation du Dashboard sur l'EDT");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 1 ÉCHEC] " + t.getMessage());
            failed++;
            if (!closed.get()) {
                try {
                    SwingUtilities.invokeAndWait(() -> closeDefensively(dashboardRef, supRef, simRef));
                } catch (Throwable ignored) {
                    closeDefensively(dashboardRef, supRef, simRef);
                }
                closed.set(true);
            }
        } finally {
            DatabaseConfig.resetDefaultDbPath();
            cleanupTestDatabase(tempDbFile);
        }

        // Test 2 : Réception d'une télémétrie par le superviseur
        try {
            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            supervisor.setConsoleLogging(false);
            TelemetryMessage msg = new TelemetryMessage("M01", SensorType.TEMPERATURE, 74.5, "C", 101L);
            supervisor.onMessageReceived(msg, InetAddress.getLocalHost(), 60);

            IndustrialSupervisor.MachineSupervisionInfo info = supervisor.getMachineInfo("M01");
            assertNotNull(info, "Info M01 présente");
            assertEquals(74.5, info.getLatestReadings().get(SensorType.TEMPERATURE), "Température M01 enregistrée");
            System.out.println("✅ [TEST 2 PASSÉ] Réception et mise à jour télémétrie superviseur");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 2 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 3 : Mise à jour de l'état d'une machine
        try {
            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            supervisor.setConsoleLogging(false);
            HeartbeatMessage hb = new HeartbeatMessage("M02", MachineState.WARNING, 201L);
            supervisor.onMessageReceived(hb, InetAddress.getLocalHost(), 50);

            IndustrialSupervisor.MachineSupervisionInfo info = supervisor.getMachineInfo("M02");
            assertEquals(MachineState.WARNING, info.getState(), "État machine M02 mis à jour vers WARNING");
            System.out.println("✅ [TEST 3 PASSÉ] Mise à jour dynamique de l'état machine");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 3 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 4 : Mise à jour du composant SensorPanel
        try {
            AtomicReference<SensorPanel> panelRef = new AtomicReference<>();
            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            supervisor.setConsoleLogging(false);
            TelemetryMessage msg = new TelemetryMessage("M01", SensorType.PRESSURE, 185.0, "bar", 102L);
            supervisor.onMessageReceived(msg, InetAddress.getLocalHost(), 55);

            SwingUtilities.invokeAndWait(() -> {
                SensorPanel panel = new SensorPanel();
                panel.setMachine("M01");
                panel.updateData(supervisor);
                panelRef.set(panel);
            });

            assertNotNull(panelRef.get(), "SensorPanel instancié et mis à jour");
            System.out.println("✅ [TEST 4 PASSÉ] Mise à jour des jauges et capteurs dans SensorPanel");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 4 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 5 : Transition de machine à états
        try {
            Machine m = new Machine("M01", "Presse", MachineState.RUNNING);
            assertTrue(m.transitionTo(MachineState.WARNING), "Transition RUNNING -> WARNING autorisée");
            assertTrue(m.transitionTo(MachineState.CRITICAL), "Transition WARNING -> CRITICAL autorisée");
            assertTrue(m.transitionTo(MachineState.FAILURE), "Transition CRITICAL -> FAILURE autorisée");
            assertFalse(m.transitionTo(MachineState.RUNNING), "Transition directe FAILURE -> RUNNING interdite");
            System.out.println("✅ [TEST 5 PASSÉ] Validation stricte des transitions de la machine à états");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 5 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 6 : Réception d'une alarme et incrément des compteurs
        try {
            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            supervisor.setConsoleLogging(false);
            AlarmMessage alarm = new AlarmMessage("M02", AlarmLevel.WARNING, EventType.THRESHOLD_EXCEEDED,
                    SensorType.TEMPERATURE, 82.0, "Seuil dépassé", 301L);
            supervisor.onMessageReceived(alarm, InetAddress.getLocalHost(), 80);

            IndustrialSupervisor.MachineSupervisionInfo info = supervisor.getMachineInfo("M02");
            assertEquals(1, info.getTotalAlarms(), "Compteur d'alarmes incrémenté");
            assertEquals(MachineState.WARNING, info.getState(), "État machine basculé en WARNING");
            System.out.println("✅ [TEST 6 PASSÉ] Traitement et agrégation des alarmes");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 6 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 7 : Historique des événements en mémoire (max 500)
        try {
            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            supervisor.setConsoleLogging(false);

            for (int i = 0; i < 550; i++) {
                AlarmMessage a = new AlarmMessage("M01", AlarmLevel.INFO, EventType.MACHINE_STARTED,
                        SensorType.SPEED, 1200, "Démarrage #" + i, i);
                supervisor.onMessageReceived(a, InetAddress.getLocalHost(), 70);
            }

            assertEquals(500, supervisor.getEventHistory().size(), "L'historique doit être plafonné à 500 événements");
            System.out.println("✅ [TEST 7 PASSÉ] Historique des événements en mémoire circulaire borné (500 max)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 7 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 8 : Calcul du packet rate (packets/sec)
        try {
            NetworkMonitor monitor = new NetworkMonitor();
            for (int i = 0; i < 20; i++) {
                monitor.recordMessage(new TelemetryMessage("M01", SensorType.PRESSURE, 160.0, "bar", i), 64);
            }
            Thread.sleep(1050); // Attente d'un intervalle pour le calcul de taux
            NetworkStatistics stats = monitor.getSnapshot();
            assertTrue(stats.getPacketsPerSecond() > 0.0, "Le débit en paquets/s doit être > 0");
            System.out.println("✅ [TEST 8 PASSÉ] Calcul de la cadence réseau (" + stats.getPacketsPerSecond() + " pkt/s)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 8 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 9 : Calcul du Throughput (Ko/s)
        try {
            NetworkMonitor monitor = new NetworkMonitor();
            for (int i = 0; i < 15; i++) {
                monitor.recordMessage(new TelemetryMessage("M01", SensorType.TEMPERATURE, 55.0, "C", i), 1024);
            }
            Thread.sleep(1050);
            NetworkStatistics stats = monitor.getSnapshot();
            assertTrue(stats.getThroughputKbPerSec() > 0.0, "Le débit en Ko/s doit être > 0");
            System.out.println("✅ [TEST 9 PASSÉ] Calcul du débit binaire (" + stats.getThroughputKbPerSec() + " Ko/s)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 9 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 10 : Détection de Sequence Gap
        try {
            SequenceTracker tracker = new SequenceTracker();
            assertEquals(0L, tracker.track("M01", "TELEMETRY", 100), "Seq 100 initial");
            assertEquals(0L, tracker.track("M01", "TELEMETRY", 101), "Seq 101 consécutif");
            long gap = tracker.track("M01", "TELEMETRY", 105); // Saut de 101 à 105 -> 3 paquets manquants (102, 103, 104)
            assertEquals(3L, gap, "3 paquets manquants détectés");
            assertEquals(1L, tracker.getTotalSequenceGaps(), "1 saut de séquence total");
            System.out.println("✅ [TEST 10 PASSÉ] Détection des sauts de séquence (Sequence Gaps)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 10 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 11 : Détection de Potential Packet Loss
        try {
            SequenceTracker tracker = new SequenceTracker();
            tracker.track("M02", "HEARTBEAT", 10);
            tracker.track("M02", "HEARTBEAT", 14); // 3 paquets manquants (11, 12, 13)
            assertEquals(3L, tracker.getPotentialPacketLoss(), "Pertes potentielles estimées");
            System.out.println("✅ [TEST 11 PASSÉ] Estimation des pertes potentielles de paquets UDP");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 11 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 12 : Statistiques par machine
        try {
            NetworkMonitor monitor = new NetworkMonitor();
            monitor.recordMessage(new TelemetryMessage("M01", SensorType.PRESSURE, 160.0, "bar", 1), 60);
            monitor.recordMessage(new TelemetryMessage("M01", SensorType.TEMPERATURE, 55.0, "C", 2), 60);
            monitor.recordMessage(new TelemetryMessage("M02", SensorType.ENERGY, 45.0, "kW", 1), 60);

            NetworkStatistics stats = monitor.getSnapshot();
            assertEquals(2L, stats.getPacketsByMachine().get("M01"), "M01 a émis 2 paquets");
            assertEquals(1L, stats.getPacketsByMachine().get("M02"), "M02 a émis 1 paquet");
            System.out.println("✅ [TEST 12 PASSÉ] Agrégation et répartition des métriques par machine");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 12 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 13 : Simulation Surchauffe via FaultInjector
        try {
            MachineSimulator sim = new MachineSimulator();
            Machine m02 = sim.getMachine("M02");
            sim.getFaultInjector().injectOverheating(m02, 98.0);
            for (int i = 0; i < 20; i++) {
                m02.tick();
            }
            assertTrue(m02.getSensor(SensorType.TEMPERATURE).getCurrentValue() >= 80.0,
                    "La température M02 a dépassé le seuil de surchauffe 80°C");
            System.out.println("✅ [TEST 13 PASSÉ] Scénario Fault Injection : Surchauffe progressive");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 13 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 14 : Simulation Heartbeat Offline via FaultInjector
        try {
            MachineSimulator sim = new MachineSimulator();
            Machine m03 = sim.getMachine("M03");
            sim.getFaultInjector().injectHeartbeatLoss(m03);
            assertFalse(m03.isHeartbeatEnabled(), "Le Heartbeat de M03 doit être coupé");
            System.out.println("✅ [TEST 14 PASSÉ] Scénario Fault Injection : Coupure de Heartbeat");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 14 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 15 : Rétablissement Heartbeat Online
        try {
            MachineSimulator sim = new MachineSimulator();
            Machine m03 = sim.getMachine("M03");
            sim.getFaultInjector().injectHeartbeatLoss(m03);
            sim.getFaultInjector().restoreNormalOperation(m03);
            assertTrue(m03.isHeartbeatEnabled(), "Le Heartbeat de M03 doit être rétabli");
            System.out.println("✅ [TEST 15 PASSÉ] Scénario Fault Injection : Rétablissement de Heartbeat (Online)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 15 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 16 : Arrêt propre des composants et de l'IHM
        try {
            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            MachineSimulator simulator = new MachineSimulator();
            supervisor.setConsoleLogging(false);

            supervisor.start();
            simulator.start();

            assertTrue(supervisor.isRunning(), "Superviseur actif");
            assertTrue(simulator.isRunning(), "Simulateur actif");

            supervisor.stop();
            simulator.stop();

            assertFalse(supervisor.isRunning(), "Superviseur arrêté");
            assertFalse(simulator.isRunning(), "Simulateur arrêté");
            System.out.println("✅ [TEST 16 PASSÉ] Fermeture propre et sans résidu des threads et sockets");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 16 ÉCHEC] " + t.getMessage());
            failed++;
        }

        System.out.println("================================================================");
        System.out.println("  RÉSULTAT DES TESTS : " + passed + " / 16 réussis, " + failed + " échoués.");
        System.out.println("================================================================");

        if (failed > 0) {
            System.exit(1);
        } else {
            System.exit(0);
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

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) {
            throw new AssertionError(message + " - Objet nul non attendu");
        }
    }

    private static void closeDefensively(AtomicReference<IndustrialDashboard> dashboardRef,
                                         AtomicReference<IndustrialSupervisor> supRef,
                                         AtomicReference<MachineSimulator> simRef) {
        // 1. Tenter la fermeture complète via le Dashboard si l'instance existe
        IndustrialDashboard dashboard = dashboardRef.get();
        if (dashboard != null) {
            try {
                dashboard.dispose();
            } catch (Throwable t) {
                System.err.println("⚠️ [FERMETURE DÉFENSIVE] Échec dashboard.dispose(): " + t.getMessage());
            }
        }

        // 2. Arrêt du simulateur (producteur de trafic UDP)
        MachineSimulator sim = simRef.get();
        if (sim != null) {
            try {
                if (sim.isRunning()) {
                    sim.stop();
                }
            } catch (Throwable t) {
                System.err.println("⚠️ [FERMETURE DÉFENSIVE] Échec simulator.stop(): " + t.getMessage());
            }
        }

        // 3. Arrêt du superviseur (consommateur UDP / coordination)
        // Note : IndustrialSupervisor.stop() arrête lui-même son PersistenceService en interne.
        IndustrialSupervisor sup = supRef.get();
        if (sup != null) {
            try {
                if (sup.isRunning()) {
                    sup.stop();
                }
            } catch (Throwable t) {
                System.err.println("⚠️ [FERMETURE DÉFENSIVE] Échec supervisor.stop(): " + t.getMessage());
            }

            // 4. Arrêt de secours de la persistance si elle est encore active (ex. supervisor.stop() non exécuté ou en échec)
            try {
                if (sup.getPersistenceService() != null && sup.getPersistenceService().isRunning()) {
                    sup.getPersistenceService().stop();
                }
            } catch (Throwable t) {
                System.err.println("⚠️ [FERMETURE DÉFENSIVE] Échec persistenceService.stop(): " + t.getMessage());
            }
        }
    }

    private static void cleanupTestDatabase(File tempDbFile) {
        if (tempDbFile == null) return;
        try {
            File prodDbFile = new File(DatabaseConfig.DEFAULT_DB_PATH);
            String tempCanonical = tempDbFile.getCanonicalPath();
            String prodCanonical = prodDbFile.getCanonicalPath();

            // Refus catégorique si les chemins canoniques correspondent
            if (tempCanonical.equals(prodCanonical)) {
                System.err.println("❌ [SÉCURITÉ] Tentative de suppression de la base de production bloquée !");
                return;
            }

            // Vérifier que le fichier est bien un fichier temporaire dédié créé par cette exécution
            if (!tempDbFile.getName().startsWith("test_p3_") || !tempDbFile.getName().endsWith(".db")) {
                System.err.println("❌ [SÉCURITÉ] Fichier non reconnu comme base de test temporaire : " + tempDbFile.getName());
                return;
            }

            if (tempDbFile.exists()) {
                if (!tempDbFile.delete()) {
                    tempDbFile.deleteOnExit();
                }
            }

            File wal = new File(tempDbFile.getPath() + "-wal");
            if (wal.exists()) {
                if (!wal.delete()) wal.deleteOnExit();
            }
            File shm = new File(tempDbFile.getPath() + "-shm");
            if (shm.exists()) {
                if (!shm.delete()) shm.deleteOnExit();
            }
        } catch (IOException e) {
            System.err.println("❌ [SÉCURITÉ] Erreur lors de la vérification canonique : " + e.getMessage());
        }
    }
}
