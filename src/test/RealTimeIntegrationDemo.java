package test;

import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlServer;
import control.IndustrialCommandHandler;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import rmi.RmiClient;
import rmi.RmiServer;

import java.util.List;
import java.util.Map;

/**
 * Démonstration d'intégration réelle simultanée :
 * 1. UDP Multicast (Télémétrie, Heartbeat et Alarmes)
 * 2. TCP Canal de contrôle (Commandes industrielles et ACK)
 * 3. Java RMI (Supervision distante sans duplication)
 */
public class RealTimeIntegrationDemo {

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  DÉMONSTRATION RÉELLE PHASE 4 : UDP MULTICAST + TCP + JAVA RMI   ");
        System.out.println("==================================================================");

        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        MachineSimulator simulator = new MachineSimulator();
        IndustrialCommandHandler handler = new IndustrialCommandHandler(simulator);
        ControlServer tcpServer = new ControlServer(handler);
        RmiServer rmiServer = new RmiServer(supervisor);

        supervisor.setConsoleLogging(false);
        tcpServer.setConsoleLogging(false);
        rmiServer.setConsoleLogging(false);

        try {
            // 1. Démarrage des couches réseaux
            System.out.println("\n[1] Démarrage des services distribués...");
            supervisor.start();
            simulator.start();
            tcpServer.start();
            rmiServer.start();
            System.out.println("  ✔ Bus UDP Multicast actif (224.1.1.1:5555)");
            System.out.println("  ✔ Serveur TCP de contrôle écoute sur le port 5556");
            System.out.println("  ✔ Serveur Java RMI actif sur le port 1099");

            // Laisser circuler la télémétrie UDP quelques instants
            System.out.println("\n[2] Diffusion de la télémétrie UDP en continu...");
            Thread.sleep(1500);

            // 2. Connexion du client RMI et première consultation
            System.out.println("\n[3] Interrogation distante via Java RMI...");
            RmiClient rmiClient = new RmiClient("127.0.0.1", 1099);
            rmiClient.connect();

            Map<String, String> statuses = rmiClient.getAllMachineStatuses();
            System.out.println("  ▶ États reçus par RMI : " + statuses);

            Map<String, Object> netStats = rmiClient.getNetworkStatistics();
            System.out.printf("  ▶ Métriques réseau RMI : %.1f pkt/s | Débit: %.2f Ko/s | Statut: %s%n",
                    (Double) netStats.get("packetsPerSecond"),
                    (Double) netStats.get("throughputKbPerSec"),
                    netStats.get("healthStatus"));

            // 3. Envoi d'une commande industrielle TCP fiable (STOP sur M01)
            System.out.println("\n[4] Émission d'une commande fiable via TCP...");
            try (ControlClient tcpClient = new ControlClient("127.0.0.1", 5556)) {
                ControlCommand stopCmd = new ControlCommand("cmd-demo-01", "M01", ControlCommandType.STOP);
                System.out.println("  ▶ Envoi TCP : " + stopCmd.serialize());
                ControlResponse ackStop = tcpClient.sendCommand(stopCmd);
                System.out.println("  ◀ Réception ACK TCP : " + ackStop.serialize());

                // Vérification immédiate via RMI
                String m01Status = rmiClient.getMachineStatus("M01");
                System.out.println("  ▶ Vérification RMI de M01 après STOP : " + m01Status);

                // 4. Déclenchement de l'arrêt d'urgence EMERGENCY_STOP via TCP
                System.out.println("\n[5] Déclenchement d'un ARRÊT D'URGENCE (EMERGENCY_STOP) via TCP...");
                ControlCommand emCmd = new ControlCommand("cmd-demo-02", "M02", ControlCommandType.EMERGENCY_STOP);
                System.out.println("  ▶ Envoi TCP : " + emCmd.serialize());
                ControlResponse ackEm = tcpClient.sendCommand(emCmd);
                System.out.println("  ◀ Réception ACK TCP : " + ackEm.serialize());

                // Attente de propagation de l'alarme sur le bus UDP Multicast
                Thread.sleep(500);

                // 5. Consultation des alarmes via RMI (l'alarme générée par le TCP a transité par UDP !)
                System.out.println("\n[6] Consultation des alarmes via RMI (Preuve de la boucle TCP -> UDP -> RMI) :");
                List<String> alarms = rmiClient.getActiveAlarms();
                for (String a : alarms) {
                    System.out.println("  ⚠️ " + a);
                }

                // 6. Test commande incohérente (START sur machine en panne)
                System.out.println("\n[7] Test de robustesse : Envoi d'une commande illégale (START sur machine en FAILURE)...");
                ControlCommand badCmd = new ControlCommand("cmd-demo-03", "M02", ControlCommandType.START);
                ControlResponse badAck = tcpClient.sendCommand(badCmd);
                System.out.println("  ◀ Réponse TCP rejetée : " + badAck.serialize());

                // 7. Commande RESET pour lever la panne
                System.out.println("\n[8] Réarmement de la machine (RESET via TCP)...");
                ControlCommand resetCmd = new ControlCommand("cmd-demo-04", "M02", ControlCommandType.RESET);
                ControlResponse resetAck = tcpClient.sendCommand(resetCmd);
                System.out.println("  ◀ Réponse TCP RESET : " + resetAck.serialize());
                System.out.println("  ▶ Vérification RMI après RESET : " + rmiClient.getMachineStatus("M02"));
            }

            rmiClient.disconnect();
            System.out.println("\n==================================================================");
            System.out.println("  DÉMONSTRATION COMPLÈTE RÉUSSIE AVEC SUCCÈS (3 CANAUX COUPLÉS) !");
            System.out.println("==================================================================");

        } catch (Exception e) {
            System.err.println("❌ Erreur pendant la démonstration : " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("Arrêt des services...");
            tcpServer.stop();
            rmiServer.stop();
            simulator.stop();
            supervisor.stop();
            System.out.println("Tous les services arrêtés proprement.");
        }
    }
}
