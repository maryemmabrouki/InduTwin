package test;

import industrial.FaultInjector;
import industrial.HeartbeatMonitor;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.AlarmMessage;
import model.HeartbeatMessage;
import model.NetworkMessage;
import model.ShapeMessage;
import model.TelemetryMessage;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.Machine;
import model.industrial.MachineState;
import model.industrial.Sensor;
import model.industrial.SensorReading;
import model.industrial.SensorType;
import model.industrial.Threshold;
import network.MessageListener;
import network.MessageParser;
import network.MulticastManager;

import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Suite de vérification automatisée complète pour la Phase 2 :
 * Valide les 15 tests fonctionnels exigés par le cahier des charges.
 */
public class Phase2Verification {

    public static void main(String[] args) {
        System.out.println("================================================================");
        System.out.println("  TESTS ET VALIDATION AUTOMATISÉE - PHASE 2 (INDUSTRIEL / IOT) ");
        System.out.println("================================================================");

        int passed = 0;
        int failed = 0;

        // Test 1 : Création d'une machine
        try {
            Machine m01 = new Machine("M01", "Presse hydraulique", MachineState.RUNNING);
            m01.addSensor(new Sensor(SensorType.TEMPERATURE, 55.0, 20.0, 120.0, 0.8, new Threshold(75.0, 90.0)));
            assertEquals("M01", m01.getId(), "Machine ID");
            assertEquals("Presse hydraulique", m01.getName(), "Machine Name");
            assertEquals(MachineState.RUNNING, m01.getState(), "Machine State initial");
            assertTrue(m01.getSensors().containsKey(SensorType.TEMPERATURE), "Capteur température présent");
            System.out.println("✅ [TEST 1 PASSÉ] Création et configuration d'une machine industrielle (M01)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 1 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 2 : Création et évolution temporelle réaliste d'un capteur
        try {
            Sensor tempSensor = new Sensor(SensorType.TEMPERATURE, 70.0, 20.0, 120.0, 0.5, new Threshold(80.0, 95.0));
            double previous = tempSensor.getCurrentValue();
            for (int i = 0; i < 5; i++) {
                SensorReading reading = tempSensor.tick();
                double current = reading.getValue();
                double delta = Math.abs(current - previous);
                assertTrue(delta < 2.0, "La variation de température par pas (" + delta + ") doit rester réaliste (< 2.0)");
                previous = current;
            }
            System.out.println("✅ [TEST 2 PASSÉ] Évolution temporelle réaliste d'un capteur (dérive continue)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 2 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 3 : Génération d'une télémétrie
        try {
            SensorReading reading = new SensorReading(SensorType.PRESSURE, 162.4, "bar", System.currentTimeMillis());
            TelemetryMessage tm = new TelemetryMessage("M01", reading.getSensorType(), reading.getValue(),
                    reading.getUnit(), 101, reading.getTimestamp());
            assertEquals("TELEMETRY", tm.getMessageType(), "Type de message");
            assertEquals("M01", tm.getMachineId(), "ID Machine");
            assertEquals(162.4, tm.getValue(), "Valeur capteur");
            assertEquals("bar", tm.getUnit(), "Unité");
            assertEquals(101L, tm.getSequenceNumber(), "Numéro de séquence");
            System.out.println("✅ [TEST 3 PASSÉ] Génération d'un objet TelemetryMessage");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 3 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 4 : Sérialisation et désérialisation de TelemetryMessage
        try {
            TelemetryMessage original = new TelemetryMessage("M02", SensorType.ENERGY, 48.7, "kW", 502, 1712000000000L);
            String serialized = original.serialize();
            NetworkMessage decoded = MessageParser.decode(serialized);

            assertTrue(decoded instanceof TelemetryMessage, "Décodage en TelemetryMessage");
            TelemetryMessage tm = (TelemetryMessage) decoded;
            assertEquals("M02", tm.getMachineId(), "Machine ID décodé");
            assertEquals(SensorType.ENERGY, tm.getSensorType(), "SensorType décodé");
            assertEquals(48.7, tm.getValue(), "Valeur décodée");
            assertEquals("kW", tm.getUnit(), "Unité décodée");
            assertEquals(502L, tm.getSequenceNumber(), "SeqNum décodé");
            System.out.println("✅ [TEST 4 PASSÉ] Sérialisation / Désérialisation TelemetryMessage");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 4 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 5 : Heartbeat (Message et sérialisation)
        try {
            HeartbeatMessage originalHb = new HeartbeatMessage("M03", MachineState.RUNNING, 888, 1712000000000L);
            String serializedHb = originalHb.serialize();
            NetworkMessage decodedHb = MessageParser.decode(serializedHb);

            assertTrue(decodedHb instanceof HeartbeatMessage, "Décodage en HeartbeatMessage");
            HeartbeatMessage hbm = (HeartbeatMessage) decodedHb;
            assertEquals("M03", hbm.getMachineId(), "Machine ID Heartbeat");
            assertEquals(MachineState.RUNNING, hbm.getState(), "État Heartbeat");
            assertEquals(888L, hbm.getSequenceNumber(), "SeqNum Heartbeat");
            System.out.println("✅ [TEST 5 PASSÉ] Protocole Heartbeat (Format et parsing)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 5 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 6 : Détection offline par le HeartbeatMonitor
        try {
            HeartbeatMonitor monitor = new HeartbeatMonitor(300); // 300 ms de timeout pour le test
            AtomicBoolean offlineDetected = new AtomicBoolean(false);
            AtomicReference<String> offlineMachine = new AtomicReference<>();

            monitor.addListener(new HeartbeatMonitor.HeartbeatListener() {
                @Override
                public void onMachineOffline(String machineId, long elapsedSeconds) {
                    offlineDetected.set(true);
                    offlineMachine.set(machineId);
                }

                @Override
                public void onMachineRecovered(String machineId, MachineState state) {}

                @Override
                public void onHeartbeatTick(String machineId, MachineState state, long sequenceNumber) {}
            });

            monitor.recordHeartbeat("M03", MachineState.RUNNING, 1);
            assertTrue(monitor.isMachineOnline("M03"), "M03 initialement en ligne");

            Thread.sleep(450); // Attente au-delà du timeout de 300ms
            monitor.checkTimeouts();

            assertTrue(offlineDetected.get(), "L'alerte offline doit être déclenchée");
            assertEquals("M03", offlineMachine.get(), "Machine déclarée hors ligne");
            assertFalse(monitor.isMachineOnline("M03"), "M03 doit être marquée offline");
            System.out.println("✅ [TEST 6 PASSÉ] Détection automatique d'une machine OFFLINE par timeout Heartbeat");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 6 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 7 : Retour online (Machine Recovered)
        try {
            HeartbeatMonitor monitor = new HeartbeatMonitor(200);
            AtomicBoolean recovered = new AtomicBoolean(false);

            monitor.addListener(new HeartbeatMonitor.HeartbeatListener() {
                @Override
                public void onMachineOffline(String machineId, long elapsedSeconds) {}

                @Override
                public void onMachineRecovered(String machineId, MachineState state) {
                    recovered.set(true);
                }

                @Override
                public void onHeartbeatTick(String machineId, MachineState state, long sequenceNumber) {}
            });

            monitor.recordHeartbeat("M01", MachineState.RUNNING, 1);
            Thread.sleep(250);
            monitor.checkTimeouts(); // M01 devient OFFLINE

            // Réception d'un nouveau heartbeat
            monitor.recordHeartbeat("M01", MachineState.RUNNING, 2);
            assertTrue(recovered.get(), "La machine doit être signalée RECOVERED");
            assertTrue(monitor.isMachineOnline("M01"), "La machine doit être de nouveau en ligne");
            System.out.println("✅ [TEST 7 PASSÉ] Détection du retour en ligne (RECOVERED) lors du rétablissement du Heartbeat");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 7 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 8 : Seuil WARNING
        try {
            Threshold threshold = new Threshold(80.0, 95.0);
            assertEquals(AlarmLevel.NONE, threshold.evaluate(75.0), "Valeur 75.0 -> NONE");
            assertEquals(AlarmLevel.WARNING, threshold.evaluate(82.5), "Valeur 82.5 -> WARNING");
            System.out.println("✅ [TEST 8 PASSÉ] Évaluation de seuil WARNING");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 8 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 9 : Seuil CRITICAL
        try {
            Threshold threshold = new Threshold(80.0, 95.0);
            assertEquals(AlarmLevel.CRITICAL, threshold.evaluate(96.0), "Valeur 96.0 -> CRITICAL");
            System.out.println("✅ [TEST 9 PASSÉ] Évaluation de seuil CRITICAL");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 9 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 10 : Génération et parsing d'une alarme (AlarmMessage)
        try {
            AlarmMessage originalAlarm = new AlarmMessage(
                    "M02", AlarmLevel.WARNING, EventType.THRESHOLD_EXCEEDED,
                    SensorType.TEMPERATURE, 84.2, "Temperature limite atteinte", 777L
            );
            String serialized = originalAlarm.serialize();
            NetworkMessage decoded = MessageParser.decode(serialized);

            assertTrue(decoded instanceof AlarmMessage, "Décodage en AlarmMessage");
            AlarmMessage am = (AlarmMessage) decoded;
            assertEquals("M02", am.getMachineId(), "ID Machine Alarme");
            assertEquals(AlarmLevel.WARNING, am.getAlarmLevel(), "Niveau Alarme");
            assertEquals(EventType.THRESHOLD_EXCEEDED, am.getEventType(), "Type d'événement");
            assertEquals(SensorType.TEMPERATURE, am.getSensorType(), "Capteur Alarme");
            assertEquals(84.2, am.getValue(), "Valeur Alarme");
            System.out.println("✅ [TEST 10 PASSÉ] Génération et décodage d'AlarmMessage");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 10 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 11 : Simulation Overheating via FaultInjector
        try {
            Machine m02 = new Machine("M02", "Four", MachineState.RUNNING);
            m02.addSensor(new Sensor(SensorType.TEMPERATURE, 72.0, 20.0, 150.0, 1.0, new Threshold(80.0, 95.0)));
            FaultInjector injector = new FaultInjector();

            injector.injectOverheating(m02, 98.0);
            // On simule plusieurs cycles
            for (int i = 0; i < 20; i++) {
                m02.tick();
            }

            assertTrue(m02.getSensor(SensorType.TEMPERATURE).getCurrentValue() >= 80.0,
                    "La température injectée doit avoir dérivé au-dessus de 80°C");
            assertTrue(m02.getState() == MachineState.WARNING || m02.getState() == MachineState.CRITICAL
                            || m02.getState() == MachineState.FAILURE,
                    "L'état de la machine doit refléter la surchauffe");
            System.out.println("✅ [TEST 11 PASSÉ] Injection de panne : Surchauffe progressive (Overheating)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 11 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 12 : Simulation Vibration excessive via FaultInjector
        try {
            Machine m01 = new Machine("M01", "Presse", MachineState.RUNNING);
            m01.addSensor(new Sensor(SensorType.VIBRATION, 2.0, 0.5, 25.0, 0.5, new Threshold(5.0, 8.0)));
            FaultInjector injector = new FaultInjector();

            injector.injectExcessiveVibration(m01, 10.0);
            for (int i = 0; i < 15; i++) {
                m01.tick();
            }

            assertTrue(m01.getSensor(SensorType.VIBRATION).getCurrentValue() >= 5.0,
                    "La vibration injectée doit dépasser le seuil d'alarme 5.0 mm/s");
            System.out.println("✅ [TEST 12 PASSÉ] Injection de panne : Vibration anormale (Excessive Vibration)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 12 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 13 : Multicast réel d'un message industriel
        MulticastManager senderNet = null;
        MulticastManager receiverNet = null;
        try {
            senderNet = new MulticastManager();
            receiverNet = new MulticastManager();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<TelemetryMessage> receivedTelem = new AtomicReference<>();

            receiverNet.addListener(new MessageListener() {
                @Override
                public void onMessageReceived(NetworkMessage message, InetAddress sender) {
                    if (message instanceof TelemetryMessage) {
                        receivedTelem.set((TelemetryMessage) message);
                        latch.countDown();
                    }
                }

                @Override
                public void onNetworkError(Exception e) {}
            });

            receiverNet.start();
            senderNet.start();
            Thread.sleep(150);

            TelemetryMessage toSend = new TelemetryMessage("M01", SensorType.PRESSURE, 175.5, "bar", 9999L);
            senderNet.send(toSend);

            boolean received = latch.await(3, TimeUnit.SECONDS);
            assertTrue(received, "Le paquet industriel doit être reçu en multicast réel");
            assertEquals("M01", receivedTelem.get().getMachineId(), "ID Machine reçu");
            assertEquals(175.5, receivedTelem.get().getValue(), "Valeur pression reçue");
            System.out.println("✅ [TEST 13 PASSÉ] Transmission Multicast UDP réelle d'un message industriel sur 224.1.1.1:5555");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 13 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            if (senderNet != null) senderNet.stop();
            if (receiverNet != null) receiverNet.stop();
        }

        // Test 14 : Compatibilité avec ShapeMessage (Non-régression Phase 1)
        try {
            NetworkMessage shapeMsg = MessageParser.decode("triangle,50,60,-65536");
            assertTrue(shapeMsg instanceof ShapeMessage, "Le décodage doit toujours supporter les formes du TP");
            ShapeMessage sm = (ShapeMessage) shapeMsg;
            assertEquals("triangle", sm.getShapeData().getType(), "Type forme décodé");
            assertEquals(50, sm.getShapeData().getX(), "X forme");
            assertEquals(60, sm.getShapeData().getY(), "Y forme");
            System.out.println("✅ [TEST 14 PASSÉ] Rétrocompatibilité totale avec ShapeMessage (Non-régression TP Phase 1)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 14 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 15 : Arrêt propre des simulateurs et threads
        try {
            MachineSimulator simulator = new MachineSimulator();
            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            supervisor.setConsoleLogging(false);

            supervisor.start();
            simulator.start();
            assertTrue(simulator.isRunning(), "Simulateur en cours d'exécution");
            assertTrue(supervisor.isRunning(), "Superviseur en cours d'exécution");

            Thread.sleep(200);

            simulator.stop();
            supervisor.stop();
            assertFalse(simulator.isRunning(), "Simulateur arrêté proprement");
            assertFalse(supervisor.isRunning(), "Superviseur arrêté proprement");
            System.out.println("✅ [TEST 15 PASSÉ] Gestion du cycle de vie et arrêt propre des threads / executors");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 15 ÉCHEC] " + t.getMessage());
            failed++;
        }

        System.out.println("================================================================");
        System.out.println("  RÉSULTAT DES TESTS : " + passed + " / 15 réussis, " + failed + " échoués.");
        System.out.println("================================================================");

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
