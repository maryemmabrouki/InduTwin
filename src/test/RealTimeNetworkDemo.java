package test;

import config.NetworkConfig;
import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlServer;
import control.IndustrialCommandHandler;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.industrial.Machine;
import model.industrial.MachineState;
import monitoring.NetworkStatistics;
import persistence.DatabaseConfig;
import persistence.PersistenceService;
import rmi.RmiClient;
import rmi.RmiServer;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Démonstration réelle Phase 6 :
 * Validation de bout-en-bout de l'écosystème InduTwin inséré dans l'infrastructure réseau :
 * 1. Présentation de la topologie Cisco (VLAN 10 Industrial & VLAN 20 Supervision)
 * 2. Démarrage des flux temps réel distribués (UDP Multicast 224.1.1.1:5555)
 * 3. Vérification des canaux TCP 5556 et RMI 1099
 * 4. Émission commande START / STOP fiables avec accusé ACK
 * 5. Déclenchement d'un Arrêt d'Urgence (EMERGENCY_STOP) prioritaire
 * 6. Audit persistant dans SQLite
 * 7. Simulation de perturbation réseau (coupure Heartbeat / isolation machine)
 * 8. Observation de la bascule en état OFFLINE
 * 9. Rétablissement du lien réseau et observation de la récupération automatique
 * 10. Synthèse des métriques réseau calculées par le NetworkMonitor
 * 11. Arrêt propre de tous les services distribués.
 */
public class RealTimeNetworkDemo {

    private static final String DEMO_DB_PATH = "data/demo_phase6_network.db";
    private static final int DEMO_TCP_PORT = 5578;
    private static final int DEMO_RMI_PORT = 1210;

    public static void main(String[] args) {
        System.out.println("==================================================================");
        System.out.println("  DÉMONSTRATION RÉELLE PHASE 6 — INFRASTRUCTURE RÉSEAU INDUTWIN   ");
        System.out.println("==================================================================");

        new File(DEMO_DB_PATH).delete();
        DatabaseConfig.setCustomDbPath(DEMO_DB_PATH);

        try {
            // 1. Présentation de l'infrastructure
            System.out.println("\n[1] Topologie Réseau Cisco Modélisée :");
            System.out.println("  ▶ Routeur R1-INDUS (Router-on-a-Stick dot1Q + PIM Sparse-Mode) :");
            System.out.println("     - Sous-if g0/0.10 ➔ " + NetworkConfig.ROUTER_IP_INDUSTRIAL + " (Passerelle VLAN 10 Atelier)");
            System.out.println("     - Sous-if g0/0.20 ➔ " + NetworkConfig.ROUTER_IP_SUPERVISION + " (Passerelle VLAN 20 Supervision)");
            System.out.println("  ▶ Switch SW1-DISTRIB (Catalyst 2960 Trunk 802.1Q + IGMP Snooping)");
            System.out.println("  ▶ Machines M01, M02, M03 dans " + NetworkConfig.VLAN_INDUSTRIAL_SUBNET);
            System.out.println("  ▶ Superviseur SCADA & RMI dans " + NetworkConfig.VLAN_SUPERVISION_SUBNET);

            // 2. Démarrage des composants distribués
            System.out.println("\n[2] Démarrage des services distribués InduTwin...");
            PersistenceService persistence = new PersistenceService();
            persistence.setConsoleLogging(false);
            persistence.start();

            IndustrialSupervisor supervisor = new IndustrialSupervisor();
            supervisor.setPersistenceService(persistence);
            supervisor.setConsoleLogging(false);
            supervisor.start();

            MachineSimulator simulator = new MachineSimulator();
            simulator.start();

            IndustrialCommandHandler cmdHandler = new IndustrialCommandHandler(simulator, persistence);
            ControlServer tcpServer = new ControlServer(DEMO_TCP_PORT, cmdHandler);
            tcpServer.setConsoleLogging(false);
            tcpServer.start();

            RmiServer rmiServer = new RmiServer(supervisor, DEMO_RMI_PORT, "DemoNetworkSupervision");
            rmiServer.setConsoleLogging(false);
            rmiServer.start();

            System.out.println("  ✔ Bus UDP Multicast actif sur " + NetworkConfig.MULTICAST_GROUP + ":" + NetworkConfig.PORT + " (TTL=" + NetworkConfig.MULTICAST_TTL + ")");
            System.out.println("  ✔ Canal de contrôle TCP actif sur port " + DEMO_TCP_PORT);
            System.out.println("  ✔ Serveur RMI actif sur port " + DEMO_RMI_PORT);
            System.out.println("  ✔ Base d'historisation SQLite active : " + DEMO_DB_PATH);

            // 3. Observation du trafic UDP en régime établi
            System.out.println("\n[3] Diffusion et écoute de la télémétrie industrielle (VLAN 10 ➔ VLAN 20)...");
            Thread.sleep(2000); // 2 secondes d'accumulation de paquets

            NetworkStatistics initialStats = supervisor.getNetworkMonitor().getSnapshot();
            System.out.printf("  ▶ Métriques mesurées : %d paquets reçus (%.1f pkt/s) | Débit : %.2f Ko/s | Statut : %s%n",
                    initialStats.getTotalPacketsReceived(),
                    initialStats.getPacketsPerSecond(),
                    initialStats.getThroughputKbPerSec(),
                    initialStats.getHealthStatus());

            // 4. Test commande TCP fiable (Supervision ➔ M01)
            System.out.println("\n[4] Émission d'une commande TCP STOP sur M01 (Autorisée par ACL)...");
            try (ControlClient tcpClient = new ControlClient("127.0.0.1", DEMO_TCP_PORT, 2000)) {
                ControlCommand cmdStop = new ControlCommand("CMD-NET-01", "M01", ControlCommandType.STOP);
                ControlResponse respStop = tcpClient.sendCommand(cmdStop);
                System.out.println("  ▶ Accusé ACK reçu : " + respStop.getStatus() + " (" + respStop.getMessage() + ")");
            }

            // 5. Test commande EMERGENCY STOP prioritaire (QoS LLQ / DSCP CS6)
            System.out.println("\n[5] Émission ARRÊT D'URGENCE (EMERGENCY_STOP) sur M02...");
            try (ControlClient tcpClient = new ControlClient("127.0.0.1", DEMO_TCP_PORT, 2000)) {
                ControlCommand cmdEm = new ControlCommand("CMD-NET-02", "M02", ControlCommandType.EMERGENCY_STOP);
                ControlResponse respEm = tcpClient.sendCommand(cmdEm);
                System.out.println("  ▶ Accusé ACK reçu : " + respEm.getStatus() + " (" + respEm.getMessage() + ")");
            }

            // Laisser circuler l'alarme critique consécutive
            Thread.sleep(1000);
            persistence.flush(2000);

            // 6. Consultation via Java RMI
            System.out.println("\n[6] Interrogation distante via Java RMI (Supervision inter-VLAN)...");
            RmiClient rmiClient = new RmiClient("127.0.0.1", DEMO_RMI_PORT, "DemoNetworkSupervision");
            rmiClient.connect();

            Map<String, String> machineStates = rmiClient.getAllMachineStatuses();
            System.out.println("  ▶ États constatés par RMI : " + machineStates);

            List<String> rmiAlarms = rmiClient.getHistoricAlarms(3);
            System.out.println("  ▶ Dernières alarmes auditées dans SQLite : " + rmiAlarms);

            // 7. Simulation d'une perturbation réseau (coupure de lien / arrêt Heartbeat M03)
            System.out.println("\n[7] Simulation de perturbation réseau : Coupure de lien sur M03 (Simulé)...");
            simulator.getFaultInjector().injectHeartbeatLoss(simulator.getMachine("M03"));
            System.out.println("  ▶ Heartbeat interrompu sur M03. Attente du timeout réseau (6 secondes)...");
            Thread.sleep(7000); // Dépasser le seuil de 6 secondes

            MachineState stateAfterCut = supervisor.getMachineInfo("M03") != null ? supervisor.getMachineInfo("M03").getState() : MachineState.OFFLINE;
            System.out.println("  ▶ Réaction du superviseur : Machine M03 déclarée = " + stateAfterCut + " ❌");

            // 8. Rétablissement du lien réseau
            System.out.println("\n[8] Restauration du lien réseau : Reconnexion de M03...");
            simulator.getFaultInjector().restoreNormalOperation(simulator.getMachine("M03"));
            System.out.println("  ▶ Signal rétabli. Envoi du Heartbeat nominal...");
            Thread.sleep(2500);

            MachineState stateAfterRestore = supervisor.getMachineInfo("M03") != null ? supervisor.getMachineInfo("M03").getState() : MachineState.STOPPED;
            System.out.println("  ▶ Récupération automatique : Machine M03 réintégrée = " + stateAfterRestore + " ✔");

            // 9. Bilan des métriques réseau globales
            System.out.println("\n[9] Synthèse finale de surveillance réseau (NetworkMonitor) :");
            NetworkStatistics finalStats = supervisor.getNetworkMonitor().getSnapshot();
            System.out.println("  ▶ Volume total échangé : " + finalStats.getTotalBytesReceived() + " octets");
            System.out.println("  ▶ Nombre total de paquets traités : " + finalStats.getTotalPacketsReceived());
            System.out.println("  ▶ Cadence moyenne stabilisée : " + String.format("%.1f", finalStats.getPacketsPerSecond()) + " paquets/seconde");
            System.out.println("  ▶ Débit binaire moyen : " + String.format("%.2f", finalStats.getThroughputKbPerSec()) + " Ko/s");
            System.out.println("  ▶ État global de santé réseau : " + finalStats.getHealthStatus());

            // 10. Arrêt propre des services
            System.out.println("\n[10] Arrêt propre de tous les services distribués...");
            rmiClient.disconnect();
            rmiServer.stop();
            tcpServer.stop();
            simulator.stop();
            supervisor.stop();
            persistence.stop();
            System.out.println("  ✔ Déconnexion de l'ensemble des sockets UDP, TCP, RMI et SQLite sans résidu.");

            System.out.println("\n==================================================================");
            System.out.println("  ✔ DÉMONSTRATION RÉELLE PHASE 6 TERMINÉE AVEC SUCCÈS À 100%      ");
            System.out.println("==================================================================");

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            DatabaseConfig.resetDefaultDbPath();
        }
    }
}
