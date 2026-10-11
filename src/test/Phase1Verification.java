package test;

import config.NetworkConfig;
import model.NetworkMessage;
import model.ShapeData;
import model.ShapeMessage;
import network.MessageListener;
import network.MessageParser;
import network.MulticastManager;
import ui.DrawingPanel;

import java.awt.Color;
import java.net.InetAddress;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Script de vérification et de validation automatisée de la Phase 1.
 * Teste le modèle, le parseur, le DrawingPanel, le MulticastManager et la communication de bout en bout.
 */
public class Phase1Verification {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  TESTS ET VALIDATION AUTOMATISÉE - PHASE 1      ");
        System.out.println("=================================================");

        int passed = 0;
        int failed = 0;

        // Test 1 : Validation du modèle ShapeData et ShapeMessage
        try {
            ShapeData shape = new ShapeData("triangle", 100, 200, Color.RED);
            ShapeMessage msg = new ShapeMessage(shape);

            assertEquals("SHAPE", msg.getMessageType(), "Type de message");
            assertEquals("triangle,100,200," + Color.RED.getRGB(), msg.serialize(), "Sérialisation ShapeMessage");
            System.out.println("✅ [TEST 1 PASSÉ] Modèle ShapeData & ShapeMessage conforme");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 1 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 2 : Parsing et gestion des paquets invalides
        try {
            NetworkMessage decoded = MessageParser.decode("diamond,45,90,-16711936");
            if (!(decoded instanceof ShapeMessage)) {
                throw new AssertionError("Le décodage aurait dû renvoyer un ShapeMessage");
            }
            ShapeMessage sm = (ShapeMessage) decoded;
            assertEquals("diamond", sm.getShapeData().getType(), "Type décodé");
            assertEquals(45, sm.getShapeData().getX(), "X décodé");
            assertEquals(90, sm.getShapeData().getY(), "Y décodé");

            // Paquets corrompus : doivent renvoyer null sans lever d'exception
            assertNull(MessageParser.decode(null), "Null payload");
            assertNull(MessageParser.decode(""), "Empty payload");
            assertNull(MessageParser.decode("circle,10"), "Champs insuffisants");
            assertNull(MessageParser.decode("circle,invalid_x,20,-1"), "X non numérique");

            System.out.println("✅ [TEST 2 PASSÉ] MessageParser robuste (encodage, décodage, robustesse erreurs)");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 2 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 3 : Composant DrawingPanel autonome
        try {
            DrawingPanel panel = new DrawingPanel();
            assertEquals(0, panel.getShapesCount(), "Taille initiale DrawingPanel");
            panel.addShape(new ShapeData("circle", 10, 10, Color.BLUE));
            panel.addShape(new ShapeData("square", 20, 20, Color.GREEN));
            assertEquals(2, panel.getShapesCount(), "Taille après ajout de 2 formes");
            panel.clearShapes();
            assertEquals(0, panel.getShapesCount(), "Taille après clearShapes()");

            System.out.println("✅ [TEST 3 PASSÉ] DrawingPanel autonome & thread-safe");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 3 ÉCHEC] " + t.getMessage());
            failed++;
        }

        // Test 4 : Cycle de vie et transmission réseau réelle MulticastManager
        MulticastManager receiverNet = null;
        MulticastManager senderNet = null;

        try {
            receiverNet = new MulticastManager();
            senderNet = new MulticastManager();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<ShapeMessage> receivedMsg = new AtomicReference<>();
            AtomicReference<InetAddress> senderAddr = new AtomicReference<>();

            receiverNet.addListener(new MessageListener() {
                @Override
                public void onMessageReceived(NetworkMessage message, InetAddress sender) {
                    if (message instanceof ShapeMessage) {
                        receivedMsg.set((ShapeMessage) message);
                        senderAddr.set(sender);
                        latch.countDown();
                    }
                }

                @Override
                public void onNetworkError(Exception e) {
                    System.err.println("Erreur réseau pendant le test: " + e.getMessage());
                }
            });

            receiverNet.start();
            senderNet.start();

            assertTrue(receiverNet.isRunning(), "Receiver démarré");
            assertTrue(senderNet.isRunning(), "Sender démarré");

            // Pause courte pour stabilisation de l'adhésion au groupe multicast
            Thread.sleep(150);

            // Émission d'une forme test
            ShapeMessage toSend = new ShapeMessage("triangle", 123, 456, Color.MAGENTA);
            senderNet.send(toSend);

            boolean received = latch.await(3, TimeUnit.SECONDS);
            if (!received) {
                throw new AssertionError("Délai d'attente dépassé : paquet multicast non reçu en local après 3s");
            }

            ShapeMessage got = receivedMsg.get();
            assertEquals("triangle", got.getShapeData().getType(), "Forme reçue");
            assertEquals(123, got.getShapeData().getX(), "X reçu");
            assertEquals(456, got.getShapeData().getY(), "Y reçu");

            System.out.println("✅ [TEST 4 PASSÉ] Transmission Multicast réelle réussie de "
                    + senderAddr.get().getHostAddress() + " -> " + NetworkConfig.MULTICAST_GROUP + ":" + NetworkConfig.PORT);
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 4 ÉCHEC] " + t.getMessage());
            failed++;
        } finally {
            if (receiverNet != null) {
                receiverNet.stop();
            }
            if (senderNet != null) {
                senderNet.stop();
            }
        }

        // Test 5 : Arrêt propre des sockets et des threads
        try {
            if (receiverNet != null) {
                assertFalse(receiverNet.isRunning(), "Receiver arrêté proprement");
            }
            if (senderNet != null) {
                assertFalse(senderNet.isRunning(), "Sender arrêté proprement");
            }
            System.out.println("✅ [TEST 5 PASSÉ] Fermeture propre des sockets et terminaison des threads");
            passed++;
        } catch (Throwable t) {
            System.err.println("❌ [TEST 5 ÉCHEC] " + t.getMessage());
            failed++;
        }

        System.out.println("=================================================");
        System.out.println("RÉSULTAT FINAL : " + passed + " passés, " + failed + " échoués.");
        System.out.println("=================================================");

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

    private static void assertNull(Object value, String message) {
        if (value != null) {
            throw new AssertionError(message + " - Attendu: null, Reçu: <" + value + ">");
        }
    }
}
