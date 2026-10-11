package ui.industrial;

import industrial.IndustrialSupervisor;
import model.industrial.AlarmLevel;
import model.industrial.MachineState;
import model.industrial.SensorType;
import model.industrial.Threshold;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Panneau d'affichage détaillé des capteurs de la machine sélectionnée
 * avec jauges dynamiques, indicateurs visuels et seuils Warning/Critique.
 */
public class SensorPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public static class SensorGaugeWidget extends JPanel {
        private static final long serialVersionUID = 1L;

        private final SensorType sensorType;
        private final Threshold threshold;
        private final double minScale;
        private final double maxScale;

        private final JLabel nameLabel;
        private final JLabel valueLabel;
        private final JLabel statusLabel;
        private final JLabel thresholdLabel;
        private final JProgressBar progressBar;

        public SensorGaugeWidget(SensorType sensorType, String name, double minScale, double maxScale, Threshold threshold) {
            this.sensorType = sensorType;
            this.minScale = minScale;
            this.maxScale = maxScale;
            this.threshold = threshold;

            setLayout(new BorderLayout(6, 6));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
            ));

            // En-tête : Nom du capteur et Valeur courante
            JPanel top = new JPanel(new BorderLayout());
            top.setOpaque(false);

            nameLabel = new JLabel(name);
            nameLabel.setFont(new Font("SansSerif", Font.BOLD, 13));
            nameLabel.setForeground(new Color(30, 41, 59));

            valueLabel = new JLabel("-- " + sensorType.getDefaultUnit());
            valueLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
            valueLabel.setForeground(new Color(15, 23, 42));

            top.add(nameLabel, BorderLayout.WEST);
            top.add(valueLabel, BorderLayout.EAST);

            // Jauge de progression
            progressBar = new JProgressBar((int) minScale, (int) maxScale);
            progressBar.setValue((int) minScale);
            progressBar.setStringPainted(false);
            progressBar.setPreferredSize(new Dimension(200, 14));
            progressBar.setForeground(new Color(34, 197, 94)); // Vert par défaut

            // Pied : Statut et Seuils
            JPanel bottom = new JPanel(new BorderLayout());
            bottom.setOpaque(false);

            statusLabel = new JLabel("Normal");
            statusLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
            statusLabel.setForeground(new Color(34, 197, 94));

            String threshText = String.format("Warning: %.1f | Critique: %.1f",
                    threshold.getWarningThreshold(), threshold.getCriticalThreshold());
            thresholdLabel = new JLabel(threshText);
            thresholdLabel.setFont(new Font("SansSerif", Font.PLAIN, 10));
            thresholdLabel.setForeground(new Color(100, 116, 139));

            bottom.add(statusLabel, BorderLayout.WEST);
            bottom.add(thresholdLabel, BorderLayout.EAST);

            add(top, BorderLayout.NORTH);
            add(progressBar, BorderLayout.CENTER);
            add(bottom, BorderLayout.SOUTH);
        }

        public void updateValue(double value) {
            valueLabel.setText(String.format("%.1f %s", value, sensorType.getDefaultUnit()));

            int intVal = (int) Math.round(value);
            progressBar.setValue(intVal);

            AlarmLevel level = threshold.evaluate(value);
            if (level == AlarmLevel.CRITICAL) {
                statusLabel.setText("CRITIQUE");
                statusLabel.setForeground(new Color(239, 68, 68));
                progressBar.setForeground(new Color(239, 68, 68));
                valueLabel.setForeground(new Color(239, 68, 68));
            } else if (level == AlarmLevel.WARNING) {
                statusLabel.setText("WARNING");
                statusLabel.setForeground(new Color(245, 158, 11));
                progressBar.setForeground(new Color(245, 158, 11));
                valueLabel.setForeground(new Color(245, 158, 11));
            } else {
                statusLabel.setText("NORMAL");
                statusLabel.setForeground(new Color(34, 197, 94));
                progressBar.setForeground(new Color(34, 197, 94));
                valueLabel.setForeground(new Color(15, 23, 42));
            }
        }
    }

    private final JLabel titleLabel;
    private final MachineStatusIndicator statusIndicator;
    private final JPanel gaugesContainer;
    private final Map<SensorType, SensorGaugeWidget> activeWidgets = new LinkedHashMap<>();

    private String currentMachineId = "M01";

    public SensorPanel() {
        setLayout(new BorderLayout(8, 10));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(12, 14, 12, 14)
        ));

        // En-tête de la machine sélectionnée
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(226, 232, 240)),
                BorderFactory.createEmptyBorder(0, 0, 8, 0)
        ));

        titleLabel = new JLabel("M01 — Presse hydraulique");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        titleLabel.setForeground(new Color(15, 23, 42));

        statusIndicator = new MachineStatusIndicator(MachineState.STOPPED);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusIndicator, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // Conteneur des jauges
        gaugesContainer = new JPanel();
        gaugesContainer.setLayout(new BoxLayout(gaugesContainer, BoxLayout.Y_AXIS));
        gaugesContainer.setOpaque(false);

        add(gaugesContainer, BorderLayout.CENTER);

        // Affiche la configuration initiale de M01
        loadMachineSensors("M01");
    }

    public synchronized void setMachine(String machineId) {
        if (!this.currentMachineId.equals(machineId)) {
            this.currentMachineId = machineId;
            loadMachineSensors(machineId);
        }
    }

    private void loadMachineSensors(String machineId) {
        gaugesContainer.removeAll();
        activeWidgets.clear();

        if ("M01".equals(machineId)) {
            titleLabel.setText("M01 — Presse hydraulique (Atelier A)");
            addWidget(SensorType.TEMPERATURE, "Température Huile", 20.0, 120.0, new Threshold(75.0, 90.0));
            addWidget(SensorType.PRESSURE, "Pression Circuit", 50.0, 300.0, new Threshold(220.0, 260.0));
            addWidget(SensorType.VIBRATION, "Vibrations Pompe", 0.5, 25.0, new Threshold(5.0, 8.0));
            addWidget(SensorType.SPEED, "Vitesse Rotation", 0.0, 2000.0, new Threshold(1500.0, 1800.0));
        } else if ("M02".equals(machineId)) {
            titleLabel.setText("M02 — Four industriel (Atelier B)");
            addWidget(SensorType.TEMPERATURE, "Température Foyer", 20.0, 150.0, new Threshold(80.0, 95.0));
            addWidget(SensorType.PRESSURE, "Pression Gaz", 0.5, 10.0, new Threshold(3.5, 5.0));
            addWidget(SensorType.ENERGY, "Consommation Énergétique", 10.0, 100.0, new Threshold(70.0, 85.0));
        } else if ("M03".equals(machineId)) {
            titleLabel.setText("M03 — Robot d'assemblage (Atelier C)");
            addWidget(SensorType.TEMPERATURE, "Température Moteurs", 15.0, 90.0, new Threshold(65.0, 80.0));
            addWidget(SensorType.VIBRATION, "Vibrations Bras", 0.2, 15.0, new Threshold(4.5, 7.0));
            addWidget(SensorType.SPEED, "Vitesse Angulaire", 0.0, 300.0, new Threshold(240.0, 280.0));
            addWidget(SensorType.LOAD, "Charge Utile", 0.0, 100.0, new Threshold(85.0, 95.0));
        }

        gaugesContainer.revalidate();
        gaugesContainer.repaint();
    }

    private void addWidget(SensorType type, String name, double min, double max, Threshold threshold) {
        SensorGaugeWidget widget = new SensorGaugeWidget(type, name, min, max, threshold);
        widget.setMaximumSize(new Dimension(Integer.MAX_VALUE, 75));
        activeWidgets.put(type, widget);
        gaugesContainer.add(widget);
        gaugesContainer.add(Box.createRigidArea(new Dimension(0, 8)));
    }

    public synchronized void updateData(IndustrialSupervisor supervisor) {
        if (supervisor == null) return;

        IndustrialSupervisor.MachineSupervisionInfo info = supervisor.getMachineInfo(currentMachineId);
        if (info != null) {
            statusIndicator.updateState(info.getState());

            for (Map.Entry<SensorType, Double> entry : info.getLatestReadings().entrySet()) {
                SensorGaugeWidget widget = activeWidgets.get(entry.getKey());
                if (widget != null) {
                    widget.updateValue(entry.getValue());
                }
            }
        }
    }

    public String getCurrentMachineId() {
        return currentMachineId;
    }
}
