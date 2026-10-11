package ui.industrial.components;

import model.industrial.AlarmLevel;
import model.industrial.SensorType;
import model.industrial.Threshold;
import ui.industrial.theme.UITheme;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.Locale;

/**
 * Composant Swing moderne représentant une carte de mesure pour capteur industriel (Dark Slate Card).
 * <p>
 * Regroupe visuellement :
 * <ul>
 *   <li>Nom du capteur et indicateur d'état sous forme de {@link ModernBadge}</li>
 *   <li>Valeur numérique formatée grand format en police monospace stabilisée et unité physique</li>
 *   <li>Mini-courbe d'évolution temporelle glissante {@link SparklineChart}</li>
 *   <li>Jauge de niveau vectorielle personnalisée avec repères visuels des seuils Warning et Critique</li>
 * </ul>
 * Conforme au système de design {@link UITheme} et optimisé pour le rafraîchissement rapide sans allocations superflues.
 */
public class ModernGaugeCard extends JPanel {

    private static final long serialVersionUID = 1L;

    private final SensorType sensorType;
    private final String sensorName;
    private final String unit;

    private double minScale;
    private double maxScale;
    private Threshold threshold;
    private double currentValue;

    private final JLabel nameLabel;
    private final JLabel valueLabel;
    private final JLabel unitLabel;
    private final JLabel thresholdLabel;
    private final ModernBadge statusBadge;
    private final SparklineChart sparkline;
    private final ModernGaugeBar gaugeBar;

    /**
     * Crée une carte de capteur basée sur un {@link SensorType} du modèle industriel.
     */
    public ModernGaugeCard(SensorType sensorType, String sensorName, double minScale, double maxScale, Threshold threshold) {
        this(sensorType, sensorName, sensorType != null ? sensorType.getDefaultUnit() : "", minScale, maxScale, threshold);
    }

    /**
     * Crée une carte de mesure entièrement paramétrée.
     */
    public ModernGaugeCard(SensorType sensorType, String sensorName, String unit, double minScale, double maxScale, Threshold threshold) {
        this.sensorType = sensorType;
        this.sensorName = sensorName != null ? sensorName : "Capteur";
        this.unit = unit != null ? unit : "";
        this.minScale = minScale;
        this.maxScale = maxScale > minScale ? maxScale : minScale + 100.0;
        this.threshold = threshold != null ? threshold : new Threshold(this.maxScale * 0.8, this.maxScale * 0.95);
        this.currentValue = minScale;

        setLayout(new BorderLayout(8, 6));
        setBackground(UITheme.SURFACE);
        setBorder(UITheme.createCardBorder());
        setPreferredSize(new Dimension(320, 105));
        setMinimumSize(new Dimension(220, 90));

        // 1. En-tête : Nom du capteur et Badge d'état
        JPanel topHeader = new JPanel(new BorderLayout(6, 0));
        topHeader.setOpaque(false);

        nameLabel = new JLabel(this.sensorName);
        nameLabel.setFont(UITheme.FONT_SUBTITLE);
        nameLabel.setForeground(UITheme.TEXT_PRIMARY);

        statusBadge = new ModernBadge("NORMAL", UITheme.STATUS_RUNNING);

        topHeader.add(nameLabel, BorderLayout.WEST);
        topHeader.add(statusBadge, BorderLayout.EAST);
        add(topHeader, BorderLayout.NORTH);

        // 2. Corps central : Valeur numérique (gauche) et Sparkline (droite)
        JPanel centerBody = new JPanel(new BorderLayout(10, 0));
        centerBody.setOpaque(false);

        JPanel valueBox = new JPanel();
        valueBox.setLayout(new GridLayout(2, 1, 0, 0));
        valueBox.setOpaque(false);

        JPanel numberRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        numberRow.setOpaque(false);

        valueLabel = new JLabel("--.-");
        valueLabel.setFont(UITheme.FONT_VALUE_MEDIUM);
        valueLabel.setForeground(UITheme.TEXT_PRIMARY);

        unitLabel = new JLabel(this.unit);
        unitLabel.setFont(UITheme.FONT_SMALL);
        unitLabel.setForeground(UITheme.TEXT_SECONDARY);

        numberRow.add(valueLabel);
        numberRow.add(unitLabel);

        String threshText = String.format(Locale.US, "Warn: %.1f · Crit: %.1f",
                this.threshold.getWarningThreshold(), this.threshold.getCriticalThreshold());
        thresholdLabel = new JLabel(threshText);
        thresholdLabel.setFont(UITheme.FONT_TINY);
        thresholdLabel.setForeground(UITheme.TEXT_MUTED);

        valueBox.add(numberRow);
        valueBox.add(thresholdLabel);

        sparkline = new SparklineChart(35, UITheme.STATUS_RUNNING);
        sparkline.setPreferredSize(new Dimension(110, 28));

        centerBody.add(valueBox, BorderLayout.CENTER);
        centerBody.add(sparkline, BorderLayout.EAST);
        add(centerBody, BorderLayout.CENTER);

        // 3. Pied : Jauge de progression horizontale avec repères de seuils
        JPanel bottomFooter = new JPanel(new BorderLayout(0, 2));
        bottomFooter.setOpaque(false);

        gaugeBar = new ModernGaugeBar(this.minScale, this.maxScale, this.threshold);
        gaugeBar.setPreferredSize(new Dimension(0, 7));

        bottomFooter.add(gaugeBar, BorderLayout.CENTER);
        add(bottomFooter, BorderLayout.SOUTH);
    }

    /**
     * Met à jour la mesure actuelle de façon thread-safe, fluide et sans clignotement.
     *
     * @param value Nouvelle mesure physique
     */
    public void updateValue(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return;
        }

        if (SwingUtilities.isEventDispatchThread()) {
            applyValue(value);
        } else {
            SwingUtilities.invokeLater(() -> applyValue(value));
        }
    }

    private void applyValue(double value) {
        this.currentValue = value;

        // Évaluation des seuils
        AlarmLevel level = threshold.evaluate(value);
        Color stateColor;
        String statusText;

        if (level == AlarmLevel.CRITICAL) {
            stateColor = UITheme.STATUS_CRITICAL;
            statusText = "CRITIQUE";
        } else if (level == AlarmLevel.WARNING) {
            stateColor = UITheme.STATUS_WARNING;
            statusText = "WARNING";
        } else {
            stateColor = UITheme.STATUS_RUNNING;
            statusText = "NORMAL";
        }

        // Mise à jour de la valeur textuelle
        valueLabel.setText(String.format(Locale.US, "%.1f", value));
        valueLabel.setForeground(level != AlarmLevel.NONE ? stateColor : UITheme.TEXT_PRIMARY);

        // Mise à jour du badge
        statusBadge.setCustom(statusText, stateColor);

        // Mise à jour de la jauge vectorielle
        gaugeBar.setValue(value, stateColor);

        // Mise à jour de la courbe de tendance (sparkline)
        sparkline.setLineColor(stateColor);
        sparkline.addValue(value);
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public String getSensorName() {
        return sensorName;
    }

    public String getUnit() {
        return unit;
    }

    public double getCurrentValue() {
        return currentValue;
    }

    public double getMinScale() {
        return minScale;
    }

    public double getMaxScale() {
        return maxScale;
    }

    public Threshold getThreshold() {
        return threshold;
    }

    public SparklineChart getSparkline() {
        return sparkline;
    }

    public ModernBadge getStatusBadge() {
        return statusBadge;
    }

    /**
     * Ajuste l'échelle de mesure et les seuils d'alerte.
     */
    public synchronized void setScale(double minScale, double maxScale, Threshold threshold) {
        this.minScale = minScale;
        this.maxScale = maxScale > minScale ? maxScale : minScale + 1.0;
        if (threshold != null) {
            this.threshold = threshold;
            String threshText = String.format(Locale.US, "Warn: %.1f · Crit: %.1f",
                    this.threshold.getWarningThreshold(), this.threshold.getCriticalThreshold());
            thresholdLabel.setText(threshText);
        }
        gaugeBar.setScale(this.minScale, this.maxScale, this.threshold);
        applyValue(this.currentValue);
    }

    // =========================================================================
    // JAUGE VECTORIELLE MODERNE AVEC MARQUEURS DE SEUILS
    // =========================================================================

    private static class ModernGaugeBar extends JComponent {
        private static final long serialVersionUID = 1L;

        private double min;
        private double max;
        private double value;
        private Threshold threshold;
        private Color barColor = UITheme.STATUS_RUNNING;

        public ModernGaugeBar(double min, double max, Threshold threshold) {
            this.min = min;
            this.max = max > min ? max : min + 1.0;
            this.value = min;
            this.threshold = threshold;
            setOpaque(false);
        }

        public void setValue(double value, Color color) {
            this.value = value;
            this.barColor = color != null ? color : UITheme.STATUS_RUNNING;
            repaint();
        }

        public void setScale(double min, double max, Threshold threshold) {
            this.min = min;
            this.max = max > min ? max : min + 1.0;
            this.threshold = threshold;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            int w = getWidth();
            int h = getHeight();
            if (w <= 4 || h <= 2) return;

            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int radius = UITheme.RADIUS_GAUGE;

                // 1. Fond de piste sombre
                g2.setColor(UITheme.SURFACE_ALT);
                g2.fillRoundRect(0, 0, w, h, radius, radius);

                // 2. Barre de niveau remplie
                double span = max - min;
                if (span <= 0.0001) span = 1.0;

                double ratio = (value - min) / span;
                ratio = Math.max(0.0, Math.min(1.0, ratio));

                int fillWidth = (int) Math.round(ratio * w);
                if (fillWidth > 0) {
                    g2.setColor(barColor);
                    g2.fillRoundRect(0, 0, fillWidth, h, radius, radius);
                }

                // 3. Repères verticaux de seuils Warning & Critique
                if (threshold != null) {
                    // Repère Warning (Ligne ambre discrète)
                    double warnRatio = (threshold.getWarningThreshold() - min) / span;
                    if (warnRatio >= 0.0 && warnRatio <= 1.0) {
                        int warnX = (int) Math.round(warnRatio * w);
                        g2.setColor(UITheme.withAlpha(UITheme.STATUS_WARNING, 200));
                        g2.fillRect(warnX - 1, 0, 2, h);
                    }

                    // Repère Critique (Ligne rouge discrète)
                    double critRatio = (threshold.getCriticalThreshold() - min) / span;
                    if (critRatio >= 0.0 && critRatio <= 1.0) {
                        int critX = (int) Math.round(critRatio * w);
                        g2.setColor(UITheme.withAlpha(UITheme.STATUS_CRITICAL, 220));
                        g2.fillRect(critX - 1, 0, 2, h);
                    }
                }

                // 4. Contour fin subtil
                g2.setColor(UITheme.withAlpha(UITheme.BORDER, 140));
                g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);

            } finally {
                g2.dispose();
            }
        }
    }
}
