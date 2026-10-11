package test;

import config.NetworkConfig;
import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlServer;
import control.ControlStatus;
import control.IndustrialCommandHandler;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.IndustrialEvent;
import model.industrial.Machine;
import model.industrial.MachineState;
import rmi.RmiClient;
import rmi.RmiServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Suite de vérification automatisée complète pour la Phase 4 :
 * Canal de contrôle TCP fiable point-à-point + Supervision distante Java RMI.
 * Valide au minimum 22 tests fonctionnels, de robustesse, de timeout et de concurrence.
 */
public class Phase4Verification {

    private static final int TEST_TCP_PORT = 5566;
    private static final int TEST_RMI_PORT = 1199;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  TESTS ET VALIDATION AUTOMATISÉE - PHASE 4 (TCP + JAVA RMI)     ");
        System.out.println("==================================================================");

        int passed = 0;
        int failed = 0;

        MachineSimulator simulator = new MachineSimulator();
        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        IndustrialCommandHandler handler = new IndustrialCommandHandler(simulator);
        ControlServer tcpServer = new ControlServer(TEST_TCP_PORT, handler);
        RmiServer rmiServer = new RmiServer(supervisor, TEST_RMI_PORT, "TestIndustrialSupervisionService");

        tcpServer.setConsoleLogging(false);
        rmiServer.setConsoleLogging(false);
        supervisor.setConsoleLogging(false);

        // Test 1 : Démarrage du TCP Server
        try {
            tcpServer.start();
            assertTrue(tcpServer.isRunning(), "Serveur TCP doit être démarré");
            assertEquals(TEST_TCP_PORT, tcpServer.getPort(), "Port TCP");
            System.out.println("✅ [TEST 1 PASSÉ] Démarrage du TCP Server (Port " + TEST_TCP_PORT + ")");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 1 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 2 : Connexion TCP client
        ControlClient client = new ControlClient("127.0.0.1", TEST_TCP_PORT, 2000);
        try {
            client.connect();
            assertTrue(client.isConnected(), "Client TCP doit être connecté");
            System.out.println("✅ [TEST 2 PASSÉ] Connexion TCP client établie avec succès");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 2 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 3 : Commande START valide
        try {
            Machine m01 = simulator.getMachine("M01");
            m01.transitionTo(MachineState.STOPPED);
            assertEquals(MachineState.STOPPED, m01.getState(), "M01 initialement STOPPED");

            ControlCommand startCmd = new ControlCommand("cmd-start-1", "M01", ControlCommandType.START);
            ControlResponse resp = client.sendCommand(startCmd);

            assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Statut START");
            assertEquals("cmd-start-1", resp.getCommandId(), "Corrélation commandId");
            assertEquals("M01", resp.getMachineId(), "MachineId ciblé");
            assertEquals(MachineState.RUNNING, m01.getState(), "État machine après START");
            System.out.println("✅ [TEST 3 PASSÉ] Commande START valide (STOPPED -> RUNNING)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 3 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 4 : Commande STOP valide
        try {
            Machine m01 = simulator.getMachine("M01");
            assertEquals(MachineState.RUNNING, m01.getState(), "M01 doit être RUNNING avant STOP");

            ControlCommand stopCmd = new ControlCommand("cmd-stop-1", "M01", ControlCommandType.STOP);
            ControlResponse resp = client.sendCommand(stopCmd);

            assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Statut STOP");
            assertEquals(MachineState.STOPPED, m01.getState(), "État machine après STOP");
            System.out.println("✅ [TEST 4 PASSÉ] Commande STOP valide (RUNNING -> STOPPED)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 4 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 5 : Commande RESET valide
        try {
            Machine m01 = simulator.getMachine("M01");
            m01.transitionTo(MachineState.FAILURE);
            assertEquals(MachineState.FAILURE, m01.getState(), "M01 en FAILURE");

            ControlCommand resetCmd = new ControlCommand("cmd-reset-1", "M01", ControlCommandType.RESET);
            ControlResponse resp = client.sendCommand(resetCmd);

            assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Statut RESET");
            assertEquals(MachineState.STOPPED, m01.getState(), "État machine après RESET");
            System.out.println("✅ [TEST 5 PASSÉ] Commande RESET valide après FAILURE (FAILURE -> STOPPED)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 5 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 6 : Commande MAINTENANCE valide
        try {
            Machine m01 = simulator.getMachine("M01");
            m01.transitionTo(MachineState.STOPPED);

            ControlCommand maintCmd = new ControlCommand("cmd-maint-1", "M01", ControlCommandType.MAINTENANCE);
            ControlResponse resp = client.sendCommand(maintCmd);

            assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Statut MAINTENANCE");
            assertEquals(MachineState.MAINTENANCE, m01.getState(), "État machine après MAINTENANCE");
            System.out.println("✅ [TEST 6 PASSÉ] Commande MAINTENANCE valide (STOPPED -> MAINTENANCE)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 6 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 7 : Commande RESUME valide
        try {
            Machine m01 = simulator.getMachine("M01");
            assertEquals(MachineState.MAINTENANCE, m01.getState(), "M01 en MAINTENANCE");

            ControlCommand resumeCmd = new ControlCommand("cmd-resume-1", "M01", ControlCommandType.RESUME);
            ControlResponse resp = client.sendCommand(resumeCmd);

            assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Statut RESUME");
            assertEquals(MachineState.STOPPED, m01.getState(), "État machine après RESUME");
            System.out.println("✅ [TEST 7 PASSÉ] Commande RESUME valide après maintenance (MAINTENANCE -> STOPPED)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 7 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 8 : EMERGENCY_STOP
        try {
            Machine m01 = simulator.getMachine("M01");
            m01.transitionTo(MachineState.RUNNING);

            ControlCommand emStopCmd = new ControlCommand("cmd-em-1", "M01", ControlCommandType.EMERGENCY_STOP);
            ControlResponse resp = client.sendCommand(emStopCmd);

            assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Statut EMERGENCY_STOP");
            assertEquals(MachineState.FAILURE, m01.getState(), "État machine après EMERGENCY_STOP (doit être FAILURE)");
            System.out.println("✅ [TEST 8 PASSÉ] EMERGENCY_STOP déclenche un arrêt immédiat en FAILURE");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 8 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 9 : ACK correctement reçu et formalisé
        try {
            ControlCommand cmd = new ControlCommand("cmd-fmt-1", "M02", ControlCommandType.STOP);
            ControlResponse resp = client.sendCommand(cmd);
            String serialized = resp.serialize();
            assertTrue(serialized.startsWith("ACK,"), "Trame ACK commence par ACK,");
            ControlResponse parsed = ControlResponse.parse(serialized);
            assertEquals("cmd-fmt-1", parsed.getCommandId(), "ID ACK parsé");
            assertEquals("M02", parsed.getMachineId(), "MachineId ACK parsé");
            assertEquals(ControlCommandType.STOP, parsed.getCommandType(), "Type ACK parsé");
            System.out.println("✅ [TEST 9 PASSÉ] ACK formel correctement reçu, sérialisé et parsé");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 9 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 10 : Corrélation commandId
        try {
            String uniqueId = "CORR-UUID-" + System.nanoTime();
            ControlCommand corrCmd = new ControlCommand(uniqueId, "M03", ControlCommandType.STOP);
            ControlResponse resp = client.sendCommand(corrCmd);
            assertEquals(uniqueId, resp.getCommandId(), "Corrélation parfaite du commandId requête/réponse");
            System.out.println("✅ [TEST 10 PASSÉ] Corrélation stricte du commandId entre requête et réponse ACK");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 10 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 11 : Commande invalide (malformée ou nulle)
        try {
            ControlResponse parsedNull = ControlResponse.parse("TRASH,123,456");
            assertTrue(parsedNull == null, "Ligne malformée retourne null au parsing");
            ControlResponse resp = handler.handleCommand(null);
            assertEquals(ControlStatus.INVALID, resp.getStatus(), "Commande nulle donne statut INVALID");
            System.out.println("✅ [TEST 11 PASSÉ] Commande invalide ou malformée rejetée avec statut INVALID");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 11 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 12 : Commande incohérente (START sur machine déjà RUNNING)
        try {
            Machine m02 = simulator.getMachine("M02");
            m02.transitionTo(MachineState.RUNNING);

            ControlCommand badCmd = new ControlCommand("cmd-incoh-1", "M02", ControlCommandType.START);
            ControlResponse resp = client.sendCommand(badCmd);

            assertEquals(ControlStatus.REJECTED, resp.getStatus(), "START sur RUNNING doit être REJECTED");
            assertTrue(resp.getMessage().contains("ALREADY_RUNNING"), "Message de rejet explicite");
            System.out.println("✅ [TEST 12 PASSÉ] Commande incohérente rejetée sans altérer l'état métier");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 12 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 13 : Timeout TCP
        ServerSocket dummyServer = null;
        try {
            // Création d'un serveur silencieux qui n'envoie aucune réponse pour provoquer un socket timeout
            dummyServer = new ServerSocket(0);
            final ServerSocket finalDummy = dummyServer;
            int dummyPort = dummyServer.getLocalPort();

            Thread serverThread = new Thread(() -> {
                try (Socket s = finalDummy.accept()) {
                    if (s != null && !s.isClosed()) {
                        // Ne rien envoyer, dormir pour dépasser le timeout du client
                        Thread.sleep(1000);
                    }
                } catch (Exception ignored) {}
            });
            serverThread.setDaemon(true);
            serverThread.start();

            try (ControlClient timeoutClient = new ControlClient("127.0.0.1", dummyPort, 200)) {
                ControlCommand cmd = new ControlCommand("cmd-timeout", "M01", ControlCommandType.STOP);
                ControlResponse resp = timeoutClient.sendCommand(cmd);
                assertEquals(ControlStatus.TIMEOUT, resp.getStatus(), "Statut TIMEOUT attendu");
                System.out.println("✅ [TEST 13 PASSÉ] Gestion correcte du timeout TCP sans blocage");
                passed++;
            }
        } catch (Throwable t) {
            System.err.println("❌ [TEST 13 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            if (dummyServer != null) {
                try {
                    dummyServer.close();
                } catch (IOException ignored) {}
            }
        }

        // Test 14 : Déconnexion propre
        try {
            client.disconnect();
            assertFalse(client.isConnected(), "Client doit être déconnecté");
            System.out.println("✅ [TEST 14 PASSÉ] Déconnexion TCP propre et fermeture des flux");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 14 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 15 : Démarrage RMI
        try {
            rmiServer.start();
            assertTrue(rmiServer.isRunning(), "Serveur RMI démarré");
            assertEquals(TEST_RMI_PORT, rmiServer.getPort(), "Port RMI");
            System.out.println("✅ [TEST 15 PASSÉ] Démarrage du serveur Java RMI (Port " + TEST_RMI_PORT + ")");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 15 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 16 : Appel getMachineStatus via RMI
        RmiClient rmiClient = new RmiClient("127.0.0.1", TEST_RMI_PORT, "TestIndustrialSupervisionService");
        try {
            rmiClient.connect();
            assertTrue(rmiClient.isConnected(), "RmiClient connecté");

            String statusM01 = rmiClient.getMachineStatus("M01");
            assertTrue(statusM01 != null && !statusM01.isBlank(), "Statut M01 retourné");
            System.out.println("✅ [TEST 16 PASSÉ] Appel distant RMI 'getMachineStatus(\"M01\")' -> " + statusM01);
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 16 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 17 : Appel getAllMachineStatuses via RMI
        try {
            Map<String, String> allStatuses = rmiClient.getAllMachineStatuses();
            assertTrue(allStatuses.containsKey("M01"), "M01 dans la map des états");
            assertTrue(allStatuses.containsKey("M02"), "M02 dans la map des états");
            assertTrue(allStatuses.containsKey("M03"), "M03 dans la map des états");
            System.out.println("✅ [TEST 17 PASSÉ] Appel distant RMI 'getAllMachineStatuses()' -> " + allStatuses);
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 17 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 18 : Appel getActiveAlarms via RMI
        try {
            // Enregistrement d'un événement d'alarme dans le superviseur
            IndustrialEvent testAlarm = new IndustrialEvent(
                    EventType.MACHINE_FAILURE,
                    "M01",
                    AlarmLevel.CRITICAL,
                    "Test alarme critique pour RMI",
                    System.currentTimeMillis()
            );
            // On force un message d'alarme dans le superviseur
            model.AlarmMessage am = new model.AlarmMessage(
                    "M01", AlarmLevel.CRITICAL, EventType.MACHINE_FAILURE,
                    null, 0.0, "Test alarme critique pour RMI", 100L
            );
            supervisor.onMessageReceived(am, null, 100);

            List<String> alarms = rmiClient.getActiveAlarms();
            assertTrue(!alarms.isEmpty(), "Liste des alarmes non vide");
            boolean found = alarms.stream().anyMatch(a -> a.contains("M01") && a.contains("CRITICAL"));
            assertTrue(found, "Alarme M01 trouvée dans la liste");
            System.out.println("✅ [TEST 18 PASSÉ] Appel distant RMI 'getActiveAlarms()' (" + alarms.size() + " alarmes)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 18 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 19 : Appel getNetworkStatistics via RMI
        try {
            Map<String, Object> stats = rmiClient.getNetworkStatistics();
            assertTrue(stats.containsKey("packetsPerSecond"), "Contient packetsPerSecond");
            assertTrue(stats.containsKey("throughputKbPerSec"), "Contient throughputKbPerSec");
            assertTrue(stats.containsKey("healthStatus"), "Contient healthStatus");
            assertTrue(stats.containsKey("potentialPacketLoss"), "Contient potentialPacketLoss");
            System.out.println("✅ [TEST 19 PASSÉ] Appel distant RMI 'getNetworkStatistics()' -> " + stats.get("healthStatus"));
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 19 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 20 : Concurrence TCP + RMI + UDP
        try {
            supervisor.start();
            simulator.start();

            int threadCount = 8;
            ExecutorService concurrencyPool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Future<Boolean>> futures = new ArrayList<>();

            // 4 threads TCP
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                futures.add(concurrencyPool.submit(() -> {
                    startLatch.await();
                    try (ControlClient tc = new ControlClient("127.0.0.1", TEST_TCP_PORT, 2000)) {
                        ControlCommand c = new ControlCommand("cmd-conc-" + idx, "M02", ControlCommandType.STOP);
                        ControlResponse r = tc.sendCommand(c);
                        return r != null && r.getStatus() != null;
                    }
                }));
            }

            // 4 threads RMI
            for (int i = 0; i < 4; i++) {
                futures.add(concurrencyPool.submit(() -> {
                    startLatch.await();
                    RmiClient rc = new RmiClient("127.0.0.1", TEST_RMI_PORT, "TestIndustrialSupervisionService");
                    String s = rc.getMachineStatus("M02");
                    Map<String, Object> net = rc.getNetworkStatistics();
                    return s != null && net != null;
                }));
            }

            // Déclenchement simultané
            startLatch.countDown();
            boolean allPassed = true;
            for (Future<Boolean> f : futures) {
                if (!f.get(5, TimeUnit.SECONDS)) {
                    allPassed = false;
                }
            }

            concurrencyPool.shutdownNow();
            assertTrue(allPassed, "Toutes les requêtes concurrentes doivent réussir");
            System.out.println("✅ [TEST 20 PASSÉ] Concurrence TCP + RMI + UDP validée sans interblocage ni contention");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 20 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 21 : Fermeture propre TCP
        try {
            tcpServer.stop();
            assertFalse(tcpServer.isRunning(), "Serveur TCP doit être arrêté");
            System.out.println("✅ [TEST 21 PASSÉ] Arrêt propre du serveur TCP et libération du socket port");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 21 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 22 : Fermeture propre RMI
        try {
            rmiClient.disconnect();
            rmiServer.stop();
            assertFalse(rmiServer.isRunning(), "Serveur RMI doit être arrêté");
            System.out.println("✅ [TEST 22 PASSÉ] Arrêt propre du serveur Java RMI et déliement du registre");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 22 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 23 : Rejet machine inconnue (Robustesse)
        try {
            ControlCommand unknownCmd = new ControlCommand("cmd-unk", "M99", ControlCommandType.START);
            ControlResponse resp = handler.handleCommand(unknownCmd);
            assertEquals(ControlStatus.REJECTED, resp.getStatus(), "Statut machine inconnue");
            assertTrue(resp.getMessage().contains("MACHINE_NOT_FOUND"), "Message MACHINE_NOT_FOUND attendu");
            System.out.println("✅ [TEST 23 PASSÉ] Rejet immédiat avec ACK/REJECTED pour machine inconnue");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 23 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 24 : Appel getRecentEvents via RMI
        try {
            rmiServer.start();
            rmiClient.connect();
            List<String> events = rmiClient.getRecentEvents(5);
            assertTrue(events != null, "Liste d'événements non nulle");
            System.out.println("✅ [TEST 24 PASSÉ] Appel distant RMI 'getRecentEvents(5)' (" + events.size() + " événements retournés)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 24 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            rmiClient.disconnect();
            rmiServer.stop();
            simulator.stop();
            supervisor.stop();
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
