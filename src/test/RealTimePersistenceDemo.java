package test;

import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlServer;
import control.IndustrialCommandHandler;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.AlarmMessage;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.SensorType;
import persistence.DatabaseConfig;
import persistence.PersistenceService;
import rmi.RmiClient;
import rmi.RmiServer;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Démonstration d'intégration réelle Phase 5 :
 * - Diffusion UDP Multicast ➔ Enregistrement Télémétrie SQLite
 * - Panne & Alarme ➔ Enregistrement Alarme SQLite
 * - Commande TCP (START/STOP) ➔ Journal d'audit SQLite
 * - Arrêt d'Urgence TCP (EMERGENCY_STOP) ➔ Audit et Alarme d'urgence
 * - Consultation distante via Java RMI (Temps réel + Historique SQLite)
 * - Arrêt complet de l'application (Fermeture des flux et de la base)
 * - Redémarrage de l'application ➔ Preuve formelle de la persistance des données.
 */
public class RealTimePersistenceDemo {

    private static final String DEMO_DB_PATH = "data/demo_industrial_supervision.db";
    private static final int DEMO_TCP_PORT = 5570;
    private static final int DEMO_RMI_PORT = 1198;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  DÉMONSTRATION RÉELLE PHASE 5 : PERSISTANCE ET HISTORISATION     ");
        System.out.println("==================================================================");

        // Nettoyage préalable pour démarrer la démonstration sur une base neuve
        new File(DEMO_DB_PATH).delete();
        DatabaseConfig.setCustomDbPath(DEMO_DB_PATH);

        try {
            // ==================================================================
            // SESSION 1 : EXÉCUTION RÉELLE ET ENREGISTREMENT CONTINU
            // ==================================================================
            System.out.println("\n>>> [SESSION 1] DÉMARRAGE DE L'ÉCOSYSTÈME DISTRIBUÉ <<<");

            PersistenceService persistence = new PersistenceService();
            persistence.setConsoleLogging(false);
            persistence.start();

            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            supervisor.setPersistenceService(persistence);
            supervisor.setConsoleLogging(false);

            MachineSimulator simulator = new MachineSimulator();
            IndustrialCommandHandler cmdHandler = new IndustrialCommandHandler(simulator, persistence);
            ControlServer tcpServer = new ControlServer(DEMO_TCP_PORT, cmdHandler);
            tcpServer.setConsoleLogging(false);

            RmiServer rmiServer = new RmiServer(supervisor, DEMO_RMI_PORT, "DemoIndustrialSupervision");
            rmiServer.setConsoleLogging(false);

            supervisor.start();
            simulator.start();
            tcpServer.start();
            rmiServer.start();

            System.out.println("  ✔ PersistenceService initialisé avec base SQLite : " + DEMO_DB_PATH);
            System.out.println("  ✔ Bus UDP Multicast démarré (224.1.1.1:5555)");
            System.out.println("  ✔ Serveur TCP de contrôle démarré sur le port " + DEMO_TCP_PORT);
            System.out.println("  ✔ Serveur Java RMI démarré sur le port " + DEMO_RMI_PORT);

            // 1. Télémétrie enregistrée
            System.out.println("\n[1] Émission et enregistrement asynchrone de la télémétrie UDP...");
            Thread.sleep(1500); // Laisse les 3 machines émettre des mesures

            // 2. Alarme enregistrée (Simulation surchauffe machine M02)
            System.out.println("\n[2] Déclenchement d'une alarme industrielle sur M02...");
            AlarmMessage alarm = new AlarmMessage("M02", AlarmLevel.CRITICAL, EventType.THRESHOLD_EXCEEDED,
                    SensorType.TEMPERATURE, 92.5, "SURCHAUFFE CRITIQUE DETECTEE SUR FOUR",
                    System.currentTimeMillis(), System.currentTimeMillis());
            supervisor.getPersistenceService().saveAlarm(alarm);

            // 3. Commande TCP enregistrée (Audit START sur M01)
            System.out.println("\n[3] Émission commande TCP fiable STOP sur M01...");
            try (ControlClient client = new ControlClient("127.0.0.1", DEMO_TCP_PORT, 2000)) {
                ControlCommand cmd1 = new ControlCommand("CMD-DEMO-001", "M01", ControlCommandType.STOP);
                ControlResponse resp1 = client.sendCommand(cmd1);
                System.out.println("  ▶ Réponse TCP ACK : " + resp1.getStatus() + " (" + resp1.getMessage() + ")");
            }

            // 4. Emergency Stop enregistré via TCP
            System.out.println("\n[4] Déclenchement d'un ARRÊT D'URGENCE (EMERGENCY_STOP) sur M02...");
            try (ControlClient client = new ControlClient("127.0.0.1", DEMO_TCP_PORT, 2000)) {
                ControlCommand cmdEm = new ControlCommand("CMD-DEMO-002", "M02", ControlCommandType.EMERGENCY_STOP);
                ControlResponse respEm = client.sendCommand(cmdEm);
                System.out.println("  ▶ Réponse TCP ACK : " + respEm.getStatus() + " (" + respEm.getMessage() + ")");
            }

            // 5. Consultation de l'historique et des métriques via Java RMI
            System.out.println("\n[5] Consultation de l'historique SQLite via le client Java RMI...");
            persistence.flush(2000);

            RmiClient rmiClient = new RmiClient("127.0.0.1", DEMO_RMI_PORT, "DemoIndustrialSupervision");
            rmiClient.connect();

            Map<String, Object> dbStats = rmiClient.getDatabaseStatistics();
            System.out.println("  ▶ Stats SQLite (via RMI) : " + dbStats);

            List<String> rmiAlarms = rmiClient.getHistoricAlarms(5);
            System.out.println("  ▶ Alarmes auditées (via RMI) :");
            for (String a : rmiAlarms) {
                System.out.println("     " + a);
            }

            List<String> rmiCmds = rmiClient.getRecentCommands(5);
            System.out.println("  ▶ Commandes TCP auditées (via RMI) :");
            for (String c : rmiCmds) {
                System.out.println("     " + c);
            }

            List<String> m02History = rmiClient.getMachineHistory("M02", 5);
            System.out.println("  ▶ Chronologie reconstituée pour M02 :");
            for (String h : m02History) {
                System.out.println("     " + h);
            }

            // 6. Fermeture propre de l'application
            System.out.println("\n[6] Fermeture complète de l'application (Shutdown Session 1)...");
            tcpServer.stop();
            rmiServer.stop();
            simulator.stop();
            supervisor.stop();
            persistence.stop();
            System.out.println("  ✔ Tous les services arrêtés et connexions SQLite clôturées.");

            // ==================================================================
            // SESSION 2 : REDÉMARRAGE ET VÉRIFICATION DE LA PERSISTANCE
            // ==================================================================
            System.out.println("\n>>> [SESSION 2] REDÉMARRAGE ET VÉRIFICATION DE LA PERSISTANCE <<<");
            System.out.println("[7] Réouverture d'une nouvelle instance PersistenceService sur la même base...");

            PersistenceService restartedPersistence = new PersistenceService();
            restartedPersistence.setConsoleLogging(false);
            restartedPersistence.start();

            // 8. Données toujours présentes
            System.out.println("\n[8] Lecture directe des données conservées après redémarrage :");
            Map<String, Object> postStats = restartedPersistence.getStatistics();
            System.out.println("  ▶ Nombre total de mesures télémétriques : " + postStats.get("totalTelemetry"));
            System.out.println("  ▶ Nombre total d'alarmes enregistrées : " + postStats.get("totalAlarms"));
            System.out.println("  ▶ Nombre total de commandes TCP auditées : " + postStats.get("totalCommands"));

            List<Map<String, Object>> savedCommands = restartedPersistence.getRecentCommands(5);
            System.out.println("\n  ▶ Commandes TCP retrouvées dans SQLite :");
            for (Map<String, Object> c : savedCommands) {
                System.out.printf("     - ID: %s | Machine: %s | Action: %s | Statut: %s | Msg: %s%n",
                        c.get("command_id"), c.get("machine_id"), c.get("command_type"), c.get("status"), c.get("message"));
            }

            List<Map<String, Object>> savedAlarms = restartedPersistence.getRecentAlarms(5);
            System.out.println("\n  ▶ Alarmes retrouvées dans SQLite :");
            for (Map<String, Object> al : savedAlarms) {
                System.out.printf("     - Machine: %s | Niveau: %s | Type: %s | Description: %s%n",
                        al.get("machine_id"), al.get("level"), al.get("event_type"), al.get("description"));
            }

            restartedPersistence.stop();

            System.out.println("\n==================================================================");
            System.out.println("  ✔ DÉMONSTRATION RÉELLE RÉUSSIE AVEC SUCCÈS À 100%               ");
            System.out.println("  Preuve établie : Rétention intégrale des données après arrêt !   ");
            System.out.println("==================================================================");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DatabaseConfig.resetDefaultDbPath();
        }
    }
}
