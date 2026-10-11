package ui.industrial;

import monitoring.NetworkStatistics;
import ui.industrial.components.ModernBadge;
import ui.industrial.theme.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.util.Map;

/**
 * Panneau de surveillance de la santé et des performances réseau (QoS, débit, pertes, latence).
 * Restitue fidèlement les métriques calculées en temps réel par NetworkStatistics dans le thème Dark Slate.
 */
public class NetworkHealthPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private static class KpiCard extends JPanel {
        private static final long serialVersionUID = 1L;

        private final JLabel valueLabel;
        private final JLabel subtitleLabel;

        public KpiCard(String title) {
            setLayout(new BorderLayout(4, 2));
            setBackground(UITheme.SURFACE_ALT);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(UITheme.BORDER, 1),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)
            ));

            JLabel titleLabel = new JLabel(title.toUpperCase());
            titleLabel.setFont(UITheme.FONT_TINY);
            titleLabel.setForeground(UITheme.TEXT_MUTED);

            valueLabel = new JLabel("--");
            valueLabel.setFont(UITheme.FONT_VALUE_MEDIUM);
            valueLabel.setForeground(UITheme.TEXT_PRIMARY);

            subtitleLabel = new JLabel("--");
            subtitleLabel.setFont(UITheme.FONT_TINY);
            subtitleLabel.setForeground(UITheme.TEXT_SECONDARY);

            add(titleLabel, BorderLayout.NORTH);
            add(valueLabel, BorderLayout.CENTER);
            add(subtitleLabel, BorderLayout.SOUTH);
        }

        public void setValue(String value, Color color) {
            valueLabel.setText(value);
            valueLabel.setForeground(color != null ? color : UITheme.TEXT_PRIMARY);
        }

        public void setSubtitle(String subtitle) {
            subtitleLabel.setText(subtitle);
        }
    }

    private final ModernBadge healthBadge;
    private final KpiCard cardRate;
    private final KpiCard cardLoss;
    private final KpiCard cardLatency;
    private final KpiCard cardVolume;
    private final JLabel typeBreakdownLabel;

    public NetworkHealthPanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(UITheme.SURFACE);
        setBorder(UITheme.createCardBorder());

        // En-tête avec titre et badge de santé global
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel titleLabel = new JLabel("MÉTRIQUES RÉSEAU (UDP MULTICAST 224.1.1.1:5555)");
        titleLabel.setFont(UITheme.FONT_SUBTITLE);
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);

        healthBadge = new ModernBadge("EN ATTENTE", UITheme.TEXT_MUTED);

        header.add(titleLabel, BorderLayout.WEST);
        header.add(healthBadge, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // Grille de cartes KPI industrielles (2x2)
        JPanel grid = new JPanel(new GridLayout(2, 2, 8, 8));
        grid.setOpaque(false);

        cardRate = new KpiCard("Cadence & Débit");
        cardLoss = new KpiCard("Pertes & Intégrité");
        cardLatency = new KpiCard("Latence Applicative");
        cardVolume = new KpiCard("Volume Cumulé");

        grid.add(cardRate);
        grid.add(cardLoss);
        grid.add(cardLatency);
        grid.add(cardVolume);

        add(grid, BorderLayout.CENTER);

        // Répartition par type de trame
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 2));

        typeBreakdownLabel = new JLabel("Répartition : TELEMETRY: 0 | HEARTBEAT: 0 | ALARM: 0 | SHAPE: 0");
        typeBreakdownLabel.setFont(UITheme.FONT_TINY);
        typeBreakdownLabel.setForeground(UITheme.TEXT_MUTED);

        footer.add(typeBreakdownLabel, BorderLayout.WEST);
        add(footer, BorderLayout.SOUTH);
    }

    public void updateStats(NetworkStatistics stats) {
        if (stats == null) {
            healthBadge.setCustom("INDISPONIBLE", UITheme.TEXT_MUTED);
            cardRate.setValue("-- pkt/s", UITheme.TEXT_MUTED);
            cardRate.setSubtitle("Débit : -- Ko/s");
            cardLoss.setValue("-- trames", UITheme.TEXT_MUTED);
            cardLoss.setSubtitle("Sauts : --");
            cardLatency.setValue("-- ms", UITheme.TEXT_MUTED);
            cardLatency.setSubtitle("min: -- | max: --");
            cardVolume.setValue("--", UITheme.TEXT_MUTED);
            cardVolume.setSubtitle("Volume : --");
            return;
        }

        // Mise à jour du badge de santé officiel
        String health = stats.getHealthStatus();
        if ("HEALTHY".equalsIgnoreCase(health)) {
            healthBadge.setCustom("HEALTHY", UITheme.STATUS_RUNNING);
        } else if ("WARNING".equalsIgnoreCase(health)) {
            healthBadge.setCustom("WARNING", UITheme.STATUS_WARNING);
        } else {
            healthBadge.setCustom(health != null ? health : "CRITICAL", UITheme.STATUS_CRITICAL);
        }

        // Card 1 : Cadence et Débit
        cardRate.setValue(String.format("%.1f pkt/s", stats.getPacketsPerSecond()), UITheme.BORDER_ACTIVE);
        cardRate.setSubtitle(String.format("Débit : %.2f Ko/s", stats.getThroughputKbPerSec()));

        // Card 2 : Pertes et Sauts de séquence
        long loss = stats.getPotentialPacketLoss();
        long gaps = stats.getSequenceGapsCount();
        Color lossColor = (loss > 0 || gaps > 0) ? UITheme.STATUS_CRITICAL : UITheme.STATUS_RUNNING;
        cardLoss.setValue(loss + " trames", lossColor);
        cardLoss.setSubtitle("Sauts de séquence : " + gaps);

        // Card 3 : Latence moyenne
        cardLatency.setValue(String.format("%.1f ms", stats.getAverageLatencyMs()), UITheme.TEXT_PRIMARY);
        cardLatency.setSubtitle(String.format("min: %.0f ms | max: %.0f ms",
                stats.getMinLatencyMs(), stats.getMaxLatencyMs()));

        // Card 4 : Total paquets
        cardVolume.setValue(String.format("%,d paquets", stats.getTotalPacketsReceived()), UITheme.TEXT_PRIMARY);
        cardVolume.setSubtitle(String.format("Taille : %.1f Ko", stats.getTotalBytesReceived() / 1024.0));

        // Répartition par type de message
        Map<String, Long> types = stats.getPacketsByMessageType();
        typeBreakdownLabel.setText(String.format("Répartition : TELEMETRY: %d | HEARTBEAT: %d | ALARM: %d | SHAPE: %d",
                types.getOrDefault("TELEMETRY", 0L),
                types.getOrDefault("HEARTBEAT", 0L),
                types.getOrDefault("ALARM", 0L),
                types.getOrDefault("SHAPE", 0L)));
    }
}
