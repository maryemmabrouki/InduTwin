package ui.industrial;

import monitoring.NetworkStatistics;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Map;

/**
 * Panneau de surveillance de la santé et des performances réseau (QoS, débit, pertes, latence).
 * Prépare les corrélations futures avec les captures Wireshark et topologies Cisco.
 */
public class NetworkHealthPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel statusBadgeLabel;
    private final JLabel packetRateLabel;
    private final JLabel throughputLabel;
    private final JLabel packetLossLabel;
    private final JLabel sequenceGapsLabel;
    private final JLabel latencyLabel;
    private final JLabel totalPacketsLabel;
    private final JLabel typeBreakdownLabel;

    public NetworkHealthPanel() {
        setLayout(new BorderLayout(6, 8));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));

        // En-tête avec titre et badge de santé global
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel titleLabel = new JLabel("MÉTRIQUES RÉSEAU (UDP MULTICAST 224.1.1.1:5555)");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(new Color(71, 85, 105));

        statusBadgeLabel = new JLabel("● HEALTHY");
        statusBadgeLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        statusBadgeLabel.setForeground(new Color(34, 197, 94));

        header.add(titleLabel, BorderLayout.WEST);
        header.add(statusBadgeLabel, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Grille des métriques clés
        JPanel grid = new JPanel(new GridLayout(3, 2, 8, 6));
        grid.setOpaque(false);

        packetRateLabel = createMetricLabel("Cadence :", "0.0 pkt/s");
        throughputLabel = createMetricLabel("Débit :", "0.00 Ko/s");
        packetLossLabel = createMetricLabel("Pertes potentielles :", "0 trames");
        sequenceGapsLabel = createMetricLabel("Sauts de séquence :", "0");
        latencyLabel = createMetricLabel("Latence applicative moy. :", "0.0 ms");
        totalPacketsLabel = createMetricLabel("Total paquets reçus :", "0");

        grid.add(packetRateLabel);
        grid.add(throughputLabel);
        grid.add(packetLossLabel);
        grid.add(sequenceGapsLabel);
        grid.add(latencyLabel);
        grid.add(totalPacketsLabel);

        add(grid, BorderLayout.CENTER);

        // Répartition par type de trame
        typeBreakdownLabel = new JLabel("Répartition : TELEMETRY: 0 | HEARTBEAT: 0 | ALARM: 0 | SHAPE: 0");
        typeBreakdownLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        typeBreakdownLabel.setForeground(new Color(100, 116, 139));
        typeBreakdownLabel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(typeBreakdownLabel, BorderLayout.SOUTH);
    }

    private JLabel createMetricLabel(String title, String initialValue) {
        JLabel label = new JLabel(title + " " + initialValue);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(new Color(30, 41, 59));
        return label;
    }

    public void updateStats(NetworkStatistics stats) {
        if (stats == null) return;

        // Mise à jour du badge de santé
        String health = stats.getHealthStatus();
        statusBadgeLabel.setText("● " + health);
        if ("HEALTHY".equalsIgnoreCase(health)) {
            statusBadgeLabel.setForeground(new Color(34, 197, 94));
        } else if ("WARNING".equalsIgnoreCase(health)) {
            statusBadgeLabel.setForeground(new Color(245, 158, 11));
        } else {
            statusBadgeLabel.setForeground(new Color(239, 68, 68));
        }

        packetRateLabel.setText(String.format("Cadence : <b>%.1f</b> pkt/s", stats.getPacketsPerSecond()));
        packetRateLabel.setText("Cadence : " + stats.getPacketsPerSecond() + " pkt/s");

        throughputLabel.setText(String.format("Débit : %.2f Ko/s", stats.getThroughputKbPerSec()));

        packetLossLabel.setText("Pertes potentielles : " + stats.getPotentialPacketLoss() + " trames");
        if (stats.getPotentialPacketLoss() > 0) {
            packetLossLabel.setForeground(new Color(239, 68, 68));
        } else {
            packetLossLabel.setForeground(new Color(30, 41, 59));
        }

        sequenceGapsLabel.setText("Sauts de séquence : " + stats.getSequenceGapsCount());

        latencyLabel.setText(String.format("Latence moy. : %.1f ms (min: %.0f, max: %.0f)",
                stats.getAverageLatencyMs(), stats.getMinLatencyMs(), stats.getMaxLatencyMs()));

        totalPacketsLabel.setText("Total paquets : " + stats.getTotalPacketsReceived()
                + String.format(" (%.1f Ko)", stats.getTotalBytesReceived() / 1024.0));

        Map<String, Long> types = stats.getPacketsByMessageType();
        typeBreakdownLabel.setText(String.format("Répartition : TELEMETRY: %d | HEARTBEAT: %d | ALARM: %d | SHAPE: %d",
                types.getOrDefault("TELEMETRY", 0L),
                types.getOrDefault("HEARTBEAT", 0L),
                types.getOrDefault("ALARM", 0L),
                types.getOrDefault("SHAPE", 0L)));
    }
}
