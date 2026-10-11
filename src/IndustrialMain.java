import industrial.FaultInjector;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.industrial.Machine;

import java.util.Scanner;

/**
 * Point d'entrée principal pour la démonstration de la Phase 2 :
 * Simulation d'infrastructure industrielle multi-machines et supervision en temps réel.
 */
public class IndustrialMain {

    public static void main(String[] args) {
        System.out.println("=============================================================");
        System.out.println("  PLATEFORME DE SUPERVISION INDUSTRIELLE DISTRIBUÉE (PHASE 2) ");
        System.out.println("  Protocole : UDP Multicast 224.1.1.1:5555                   ");
        System.out.println("=============================================================");

        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        MachineSimulator simulator = new MachineSimulator();

        try {
            // Démarrage du superviseur et du simulateur
            supervisor.start();
            simulator.start();

            System.out.println("\nMachines actives dans la simulation :");
            System.out.println("  • M01 : Presse hydraulique  [Température, Pression, Vibration, Vitesse]");
            System.out.println("  • M02 : Four industriel     [Température, Pression, Énergie]");
            System.out.println("  • M03 : Robot industriel    [Température, Vibration, Vitesse, Charge]");

            System.out.println("\nCommandes de démonstration interactives disponibles :");
            System.out.println("  [1] Scénario 2 : Injecter Surchauffe sur M02 (Warning 85°C -> Critical 98°C)");
            System.out.println("  [2] Scénario 3 : Couper le Heartbeat de M03 (Détection OFFLINE après 6s)");
            System.out.println("  [3] Scénario 4 : Rétablir le Heartbeat et état nominal de M03");
            System.out.println("  [4] Injecter Vibration excessive sur M01 (Vibration 9.5 mm/s)");
            System.out.println("  [5] Rétablir toutes les machines à l'état nominal");
            System.out.println("  [Q] Quitter et arrêter la simulation");
            System.out.println("-------------------------------------------------------------");

            FaultInjector injector = simulator.getFaultInjector();

            // Si lancé en mode non-interactif (arguments de script), exécuter une séquence démo
            if (args.length > 0 && "--auto".equalsIgnoreCase(args[0])) {
                runAutoDemo(simulator, supervisor, injector);
                return;
            }

            try (Scanner scanner = new Scanner(System.in)) {
                boolean running = true;

                while (running && scanner.hasNextLine()) {
                    String input = scanner.nextLine().trim();

                    switch (input) {
                        case "1":
                            System.out.println("\n🔥 [SCÉNARIO 2] Déclenchement de la surchauffe sur M02...");
                            Machine m02 = simulator.getMachine("M02");
                            injector.injectOverheating(m02, 98.0);
                            break;

                        case "2":
                            System.out.println("\n⚠️ [SCÉNARIO 3] Coupure du Heartbeat de M03 (Attendez ~6s pour l'alerte OFFLINE)...");
                            Machine m03 = simulator.getMachine("M03");
                            injector.injectHeartbeatLoss(m03);
                            break;

                        case "3":
                            System.out.println("\n🔄 [SCÉNARIO 4] Rétablissement du Heartbeat de M03...");
                            Machine m03Restore = simulator.getMachine("M03");
                            injector.restoreNormalOperation(m03Restore);
                            break;

                        case "4":
                            System.out.println("\n⚡ Injection de vibration anormale sur M01 (Presse)...");
                            Machine m01 = simulator.getMachine("M01");
                            injector.injectExcessiveVibration(m01, 9.5);
                            break;

                        case "5":
                            System.out.println("\n✅ Rétablissement de toutes les machines...");
                            for (Machine m : simulator.getMachines().values()) {
                                injector.restoreNormalOperation(m);
                            }
                            break;

                        case "q":
                        case "Q":
                            running = false;
                            break;

                        default:
                            System.out.println("Option non reconnue. Tapez 1, 2, 3, 4, 5 ou Q.");
                            break;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("Erreur d'exécution: " + e.getMessage());
            e.printStackTrace();
        } finally {
            System.out.println("\nArrêt en cours des composants...");
            simulator.stop();
            supervisor.stop();
            System.out.println("Plateforme arrêtée avec succès.");
        }
    }

    private static void runAutoDemo(MachineSimulator simulator, IndustrialSupervisor supervisor, FaultInjector injector) {
        try {
            System.out.println("\n>>> Démarrage de la séquence automatisée de démonstration (15 secondes)...");
            Thread.sleep(3000);

            System.out.println("\n>>> [DÉMO] Étape 1 : Injection surchauffe sur M02");
            injector.injectOverheating(simulator.getMachine("M02"), 98.0);
            Thread.sleep(4000);

            System.out.println("\n>>> [DÉMO] Étape 2 : Coupure du Heartbeat de M03");
            injector.injectHeartbeatLoss(simulator.getMachine("M03"));
            Thread.sleep(7000);

            System.out.println("\n>>> [DÉMO] Étape 3 : Rétablissement de M03");
            injector.restoreNormalOperation(simulator.getMachine("M03"));
            Thread.sleep(2000);

        } catch (InterruptedException ignored) {
        } finally {
            simulator.stop();
            supervisor.stop();
        }
    }
}
