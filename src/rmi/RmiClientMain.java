package rmi;

import java.util.List;
import java.util.Map;

/**
 * Client console de démonstration Java RMI pour la supervision distante.
 * Interroge en direct le service de supervision et affiche l'état consolidé.
 */
public class RmiClientMain {

    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        System.out.println("=================================================");
        System.out.println("   SUPERVISION INDUSTRIELLE DISTRIBUÉE (RMI)    ");
        System.out.println("   Connexion au superviseur sur " + host + ":1099... ");
        System.out.println("=================================================");

        RmiClient client = new RmiClient(host, 1099);

        try {
            client.connect();

            System.out.println("\n=== REMOTE INDUSTRIAL MONITORING ===");

            // 1. États des machines
            Map<String, String> statuses = client.getAllMachineStatuses();
            for (Map.Entry<String, String> entry : statuses.entrySet()) {
                System.out.printf("%-5s : %s%n", entry.getKey(), entry.getValue());
            }

            // 2. Alarmes actives
            List<String> alarms = client.getActiveAlarms();
            System.out.printf("%nActive alarms : %d%n", alarms.size());
            for (String alarm : alarms) {
                System.out.println("  • " + alarm);
            }

            // 3. Statistiques réseau
            Map<String, Object> netStats = client.getNetworkStatistics();
            System.out.println("\nNetwork :");
            Object pps = netStats.get("packetsPerSecond");
            Object kbps = netStats.get("throughputKbPerSec");
            Object loss = netStats.get("potentialPacketLoss");
            Object health = netStats.get("healthStatus");

            System.out.printf("Packets/s : %.1f%n", (pps instanceof Number) ? ((Number) pps).doubleValue() : 0.0);
            System.out.printf("Throughput : %.1f KB/s%n", (kbps instanceof Number) ? ((Number) kbps).doubleValue() : 0.0);
            System.out.printf("Potential loss : %d%n", (loss instanceof Number) ? ((Number) loss).longValue() : 0);
            System.out.printf("Health : %s%n", health != null ? health : "UNKNOWN");

            // 4. Audit & Historique SQLite via RMI
            Map<String, Object> dbStats = client.getDatabaseStatistics();
            if (dbStats != null && !dbStats.isEmpty()) {
                System.out.println("\nSQLite History & Audit :");
                System.out.printf("Total Telemetry stored : %s%n", dbStats.get("totalTelemetry"));
                System.out.printf("Total Alarms stored    : %s%n", dbStats.get("totalAlarms"));
                System.out.printf("Total Commands audited : %s%n", dbStats.get("totalCommands"));

                List<String> recentCmds = client.getRecentCommands(3);
                if (!recentCmds.isEmpty()) {
                    System.out.println("Dernières commandes auditées :");
                    for (String cmdStr : recentCmds) {
                        System.out.println("  ▶ " + cmdStr);
                    }
                }
            }

            System.out.println("=================================================");

        } catch (Exception e) {
            System.err.println("❌ Erreur de communication RMI: " + e.getMessage());
            System.err.println("Assurez-vous que le serveur RMI (ou IndustrialDashboard) est en cours d'exécution.");
        } finally {
            client.disconnect();
        }
    }
}
