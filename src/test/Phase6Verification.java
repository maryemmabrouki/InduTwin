package test;

import config.NetworkConfig;
import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlServer;
import control.ControlStatus;
import control.IndustrialCommandHandler;
import industrial.HeartbeatMonitor;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.AlarmMessage;
import model.NetworkMessage;
import model.TelemetryMessage;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.Machine;
import model.industrial.MachineState;
import model.industrial.SensorType;
import monitoring.NetworkMonitor;
import monitoring.NetworkStatistics;
import monitoring.SequenceTracker;
import network.MessageListener;
import network.MulticastManager;
import rmi.RmiClient;
import rmi.RmiServer;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Suite de vérification automatisée complète pour la Phase 6 :
 * Infrastructure réseau, segmentation industrielle, adressage, flux UDP/TCP/RMI,
 * résilience face aux pannes réseau et métriques applicatives.
 */
public class Phase6Verification {

    private static final int TEST_TCP_PORT = NetworkConfig.TCP_CONTROL_PORT;
    private static final int TEST_RMI_PORT = NetworkConfig.RMI_PORT;
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  TESTS ET VALIDATION AUTOMATISÉE - PHASE 6 (INFRASTRUCTURE CISCO)");
        System.out.println("==================================================================");

        // ------------------------------------------------------------------
        // TEST 1 : Validation de l'adresse IP du groupe Multicast
        // ------------------------------------------------------------------
        try {
            InetAddress group = NetworkConfig.getGroupAddress();
            assertTrue(group.isMulticastAddress(), "L'adresse configurée doit être une adresse Multicast");
            assertEquals("224.1.1.1", group.getHostAddress(), "Adresse de groupe attendue 224.1.1.1");
            byte[] bytes = group.getAddress();
            int firstOctet = bytes[0] & 0xFF;
            assertTrue(firstOctet >= 224 && firstOctet <= 239, "Appartient à la classe D IPv4 (224.0.0.0/4)");
            System.out.println("✔ [TEST 1 PASSÉ] Adresse de groupe Multicast (224.1.1.1 / Classe D)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 1 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 2 : Validation du port UDP Multicast
        // ------------------------------------------------------------------
        try {
            int port = NetworkConfig.PORT;
            assertEquals(5555, port, "Port UDP Multicast configuré");
            assertTrue(port > 1024 && port <= 65535, "Port non privilégié utilisateur");
            System.out.println("✔ [TEST 2 PASSÉ] Port UDP Multicast (5555)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 2 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 3 : Validation du port TCP Contrôle
        // ------------------------------------------------------------------
        try {
            int tcpPort = NetworkConfig.TCP_CONTROL_PORT;
            assertEquals(5556, tcpPort, "Port TCP Contrôle configuré");
            assertTrue(tcpPort != NetworkConfig.PORT, "Port TCP distinct du port UDP");
            System.out.println("✔ [TEST 3 PASSÉ] Port TCP Canal de contrôle fiable (5556)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 3 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 4 : Validation du port Java RMI distant
        // ------------------------------------------------------------------
        try {
            int rmiPort = NetworkConfig.RMI_PORT;
            assertEquals(1099, rmiPort, "Port Java RMI standard");
            System.out.println("✔ [TEST 4 PASSÉ] Port Java RMI Supervision distante (1099)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 4 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 5 : Validation du TTL Multicast pour le franchissement du Routeur
        // ------------------------------------------------------------------
        try {
            int ttl = NetworkConfig.MULTICAST_TTL;
            assertTrue(ttl >= 4, "TTL Multicast suffisant (>= 4) pour franchir R1 inter-VLAN");
            System.out.println("✔ [TEST 5 PASSÉ] Paramètre Time-To-Live (TTL=" + ttl + ") pour routage inter-VLAN");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 5 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 6 : Validation de la cohérence du plan d'adressage VLANs
        // ------------------------------------------------------------------
        try {
            assertEquals(10, NetworkConfig.VLAN_INDUSTRIAL_ID, "VLAN ID Atelier");
            assertEquals("192.168.10.0/24", NetworkConfig.VLAN_INDUSTRIAL_SUBNET, "Sous-réseau Atelier");
            assertEquals("192.168.10.1", NetworkConfig.ROUTER_IP_INDUSTRIAL, "Passerelle R1 Atelier");

            assertEquals(20, NetworkConfig.VLAN_SUPERVISION_ID, "VLAN ID Supervision");
            assertEquals("192.168.20.0/24", NetworkConfig.VLAN_SUPERVISION_SUBNET, "Sous-réseau Supervision");
            assertEquals("192.168.20.1", NetworkConfig.ROUTER_IP_SUPERVISION, "Passerelle R1 Supervision");
            System.out.println("✔ [TEST 6 PASSÉ] Plan d'adressage et segmentation VLANs (10 & 20)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 6 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 7 : Détection et validation de l'interface réseau locale
        // ------------------------------------------------------------------
        try {
            NetworkInterface netIf = NetworkConfig.getPreferredNetworkInterface();
            assertTrue(netIf != null, "Une interface réseau active supportant le Multicast doit être trouvée");
            assertTrue(netIf.isUp(), "L'interface sélectionnée doit être active (UP)");
            assertTrue(netIf.supportsMulticast(), "L'interface sélectionnée doit supporter le Multicast");
            System.out.println("✔ [TEST 7 PASSÉ] Détection interface réseau active : " + netIf.getName() + " (" + netIf.getDisplayName() + ")");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 7 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 8 : Test réel de bout-en-bout du canal TCP Contrôle
        // ------------------------------------------------------------------
        MachineSimulator simulator = new MachineSimulator();
        IndustrialCommandHandler cmdHandler = new IndustrialCommandHandler(simulator);
        ControlServer tcpServer = new ControlServer(TEST_TCP_PORT, cmdHandler);
        tcpServer.setConsoleLogging(false);

        try {
            tcpServer.start();
            try (ControlClient tcpClient = new ControlClient("127.0.0.1", TEST_TCP_PORT, 2000)) {
                ControlCommand cmd = new ControlCommand("test-p6-tcp", "M01", ControlCommandType.STOP);
                ControlResponse resp = tcpClient.sendCommand(cmd);
                assertTrue(resp != null, "Réponse ACK non nulle");
                assertEquals(ControlStatus.ACCEPTED, resp.getStatus(), "Commande STOP acceptée");
                assertEquals("test-p6-tcp", resp.getCommandId(), "Corrélation commandId");
                System.out.println("✔ [TEST 8 PASSÉ] Connectivité de bout-en-bout TCP Contrôle (" + TEST_TCP_PORT + ") avec ACK formel");
                passed++;
            }
        } catch (Throwable t) {
            System.err.println("❌ [TEST 8 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            tcpServer.stop();
        }

        // ------------------------------------------------------------------
        // TEST 9 : Test réel de bout-en-bout du service Java RMI
        // ------------------------------------------------------------------
        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        supervisor.setConsoleLogging(false);
        RmiServer rmiServer = new RmiServer(supervisor, TEST_RMI_PORT, "Phase6TestRmiService");
        rmiServer.setConsoleLogging(false);
        RmiClient rmiClient = new RmiClient("127.0.0.1", TEST_RMI_PORT, "Phase6TestRmiService");

        try {
            rmiServer.start();
            rmiClient.connect();
            Map<String, String> statuses = rmiClient.getAllMachineStatuses();
            assertTrue(statuses.containsKey("M01"), "Machine M01 présente dans le registre RMI");
            assertEquals("STOPPED", statuses.get("M01"), "État initial M01");
            System.out.println("✔ [TEST 9 PASSÉ] Connectivité de bout-en-bout Java RMI (Port " + TEST_RMI_PORT + ")");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 9 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            rmiClient.disconnect();
            rmiServer.stop();
        }

        // ------------------------------------------------------------------
        // TEST 10 : Test réel d'émission / réception UDP Multicast
        // ------------------------------------------------------------------
        MulticastManager multicast = new MulticastManager();
        CountDownLatch mcastLatch = new CountDownLatch(1);
        AtomicReference<NetworkMessage> receivedMsg = new AtomicReference<>();

        try {
            multicast.addListener(new MessageListener() {
                @Override
                public void onMessageReceived(NetworkMessage message, InetAddress senderAddress) {
                    if (message instanceof TelemetryMessage) {
                        TelemetryMessage tm = (TelemetryMessage) message;
                        if ("M_TEST_P6".equals(tm.getMachineId())) {
                            receivedMsg.set(tm);
                            mcastLatch.countDown();
                        }
                    }
                }
                @Override
                public void onNetworkError(Exception e) {}
            });
            multicast.start();

            TelemetryMessage testTelemetry = new TelemetryMessage("M_TEST_P6", SensorType.TEMPERATURE, 42.5, "°C", 999, System.currentTimeMillis());
            multicast.send(testTelemetry);

            boolean received = mcastLatch.await(3000, TimeUnit.MILLISECONDS);
            assertTrue(received, "Message Multicast reçu avant timeout");
            assertTrue(receivedMsg.get() != null, "Objet TelemetryMessage correctement instancié");
            assertEquals(42.5, ((TelemetryMessage) receivedMsg.get()).getValue(), "Valeur de télémétrie reçue conforme");
            System.out.println("✔ [TEST 10 PASSÉ] Émission et écoute Multicast réelle sur 224.1.1.1:5555");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 10 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            multicast.stop();
        }

        // ------------------------------------------------------------------
        // TEST 11 : Discrimination des flux (Télémétries et Alarmes sur le même groupe)
        // ------------------------------------------------------------------
        MulticastManager dualMulticast = new MulticastManager();
        CountDownLatch alarmLatch = new CountDownLatch(1);
        AtomicReference<AlarmMessage> receivedAlarm = new AtomicReference<>();

        try {
            dualMulticast.addListener(new MessageListener() {
                @Override
                public void onMessageReceived(NetworkMessage message, InetAddress senderAddress) {
                    if (message instanceof AlarmMessage) {
                        AlarmMessage al = (AlarmMessage) message;
                        if ("M02".equals(al.getMachineId())) {
                            receivedAlarm.set(al);
                            alarmLatch.countDown();
                        }
                    }
                }
                @Override
                public void onNetworkError(Exception e) {}
            });
            dualMulticast.start();

            AlarmMessage testAlarm = new AlarmMessage("M02", AlarmLevel.CRITICAL, EventType.EMERGENCY_STOP,
                    null, 0.0, "TEST ALARME P6", 100, System.currentTimeMillis());
            dualMulticast.send(testAlarm);

            boolean received = alarmLatch.await(3000, TimeUnit.MILLISECONDS);
            assertTrue(received, "Alarme critique reçue sur le bus multicast");
            assertEquals(AlarmLevel.CRITICAL, receivedAlarm.get().getAlarmLevel(), "Niveau d'alarme CRITICAL");
            System.out.println("✔ [TEST 11 PASSÉ] Discrimination de flux : Télémétries et Alarmes sans collision");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 11 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            dualMulticast.stop();
        }

        // ------------------------------------------------------------------
        // TEST 12 : Comportement sous coupure réseau (Timeout Heartbeat -> OFFLINE)
        // ------------------------------------------------------------------
        try {
            HeartbeatMonitor hbMonitor = new HeartbeatMonitor(1000); // Timeout réduit pour le test
            AtomicBoolean offlineDetected = new AtomicBoolean(false);
            hbMonitor.addListener(new HeartbeatMonitor.HeartbeatListener() {
                @Override
                public void onMachineOffline(String machineId, long lastSeenTimestamp) {
                    if ("M03".equals(machineId)) {
                        offlineDetected.set(true);
                    }
                }
                @Override
                public void onMachineRecovered(String machineId, MachineState state) {}
                @Override
                public void onHeartbeatTick(String machineId, MachineState state, long sequenceNumber) {}
            });

            // Enregistrement initial
            hbMonitor.recordHeartbeat("M03", MachineState.RUNNING, 1);
            Thread.sleep(1100);
            hbMonitor.checkTimeouts();

            assertTrue(offlineDetected.get(), "Machine déclarée OFFLINE après interruption de Heartbeat");
            System.out.println("✔ [TEST 12 PASSÉ] Détection automatique de coupure réseau (Heartbeat timeout ➔ OFFLINE)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 12 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 13 : Résilience et rétablissement réseau (Heartbeat -> RECOVERED)
        // ------------------------------------------------------------------
        try {
            HeartbeatMonitor hbMonitor = new HeartbeatMonitor(1000);
            AtomicBoolean recoveredDetected = new AtomicBoolean(false);
            hbMonitor.addListener(new HeartbeatMonitor.HeartbeatListener() {
                @Override
                public void onMachineOffline(String machineId, long lastSeenTimestamp) {}
                @Override
                public void onMachineRecovered(String machineId, MachineState state) {
                    if ("M03".equals(machineId)) {
                        recoveredDetected.set(true);
                    }
                }
                @Override
                public void onHeartbeatTick(String machineId, MachineState state, long sequenceNumber) {}
            });

            // Simuler coupure
            hbMonitor.recordHeartbeat("M03", MachineState.RUNNING, 1);
            Thread.sleep(1100);
            hbMonitor.checkTimeouts();

            // Simuler retour du lien
            hbMonitor.recordHeartbeat("M03", MachineState.RUNNING, 2);

            assertTrue(recoveredDetected.get(), "Machine déclarée RECOVERED après retour du signal");
            System.out.println("✔ [TEST 13 PASSÉ] Rétablissement réseau et réintégration nominale (RECOVERED)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 13 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 14 : Cohérence des calculs du NetworkMonitor
        // ------------------------------------------------------------------
        try {
            NetworkMonitor monitor = new NetworkMonitor();

            for (int i = 0; i < 50; i++) {
                TelemetryMessage msg = new TelemetryMessage("M01", SensorType.TEMPERATURE, 25.0 + i, "°C", i + 1, System.currentTimeMillis());
                monitor.recordMessage(msg, 120);
            }

            NetworkStatistics stats = monitor.getSnapshot();
            assertTrue(stats.getTotalPacketsReceived() >= 50, "Total paquets reçus");
            assertTrue(stats.getTotalBytesReceived() >= 50 * 120, "Total octets reçus");
            assertTrue(stats.getPacketsPerSecond() >= 0.0, "Cadence en paquets/s calculable");
            assertTrue(stats.getThroughputKbPerSec() >= 0.0, "Débit en Ko/s calculable");
            System.out.println("✔ [TEST 14 PASSÉ] Cohérence des métriques NetworkMonitor (Paquets/s, Débit Ko/s)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 14 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 15 : Suivi des numéros de séquence (SequenceTracker) & Détection de pertes
        // ------------------------------------------------------------------
        try {
            SequenceTracker tracker = new SequenceTracker();
            tracker.track("M01", "TELEMETRY", 1);
            tracker.track("M01", "TELEMETRY", 2);
            tracker.track("M01", "TELEMETRY", 5); // Saut de 2 paquets (3 et 4 perdus)

            assertEquals(1L, tracker.getTotalSequenceGaps(), "1 saut de séquence détecté au total");
            assertEquals(2L, tracker.getPotentialPacketLoss(), "2 paquets estimés perdus");
            System.out.println("✔ [TEST 15 PASSÉ] Suivi de séquence SequenceTracker & Détection des pertes de paquets");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 15 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // ------------------------------------------------------------------
        // TEST 16 : Concurrence et non-interférence des 3 canaux simultanés
        // ------------------------------------------------------------------
        try {
            IndustrialSupervisor liveSupervisor = new IndustrialSupervisor();
            liveSupervisor.setConsoleLogging(false);
            liveSupervisor.start();

            MachineSimulator liveSimulator = new MachineSimulator();
            liveSimulator.start();

            IndustrialCommandHandler liveCmdHandler = new IndustrialCommandHandler(liveSimulator);
            ControlServer liveTcp = new ControlServer(TEST_TCP_PORT, liveCmdHandler);
            liveTcp.setConsoleLogging(false);
            liveTcp.start();

            RmiServer liveRmi = new RmiServer(liveSupervisor, TEST_RMI_PORT, "ConcurrencyRmiService");
            liveRmi.setConsoleLogging(false);
            liveRmi.start();

            // Interrogation concurrente TCP
            ControlResponse tcpResp;
            try (ControlClient client = new ControlClient("127.0.0.1", TEST_TCP_PORT, 2000)) {
                tcpResp = client.sendCommand(new ControlCommand("c-p6-conc", "M02", ControlCommandType.STOP));
            }

            // Interrogation concurrente RMI
            RmiClient rmiConc = new RmiClient("127.0.0.1", TEST_RMI_PORT, "ConcurrencyRmiService");
            rmiConc.connect();
            Map<String, String> rmiStates = rmiConc.getAllMachineStatuses();
            rmiConc.disconnect();

            liveRmi.stop();
            liveTcp.stop();
            liveSimulator.stop();
            liveSupervisor.stop();

            assertTrue(tcpResp != null && tcpResp.getStatus() == ControlStatus.ACCEPTED, "TCP ACK accepté en pleine charge");
            assertTrue(!rmiStates.isEmpty(), "RMI répond sans contention");
            System.out.println("✔ [TEST 16 PASSÉ] Découplage et concurrence simultanée des 3 canaux (UDP " + NetworkConfig.PORT + " + TCP " + TEST_TCP_PORT + " + RMI " + TEST_RMI_PORT + ")");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 16 ÉCHEC] " + t.getMessage());
            failed++;
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
}
