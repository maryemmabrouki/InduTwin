package ui.industrial;

import industrial.IndustrialSupervisor;
import model.industrial.MachineState;
import model.industrial.SensorType;
import model.industrial.Threshold;
import ui.industrial.components.ModernBadge;
import ui.industrial.components.ModernGaugeCard;
import ui.industrial.theme.UITheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Panneau d'affichage moderne des capteurs de la machine sélectionnée.
 * Intègre les cartes Dark Slate {@link ModernGaugeCard} avec mini-courbes {@link ui.industrial.components.SparklineChart}
 * et jauges à seuils industriels.
 */
public class SensorPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    /**
     * Classe conservée pour rétrocompatibilité ascendante avec les tests unitaires existants.
     */
    @Deprecated
    public static class SensorGaugeWidget extends ModernGaugeCard {
        private static final long serialVersionUID = 1L;

        @Deprecated
        public SensorGaugeWidget(SensorType sensorType, String name, double minScale, double maxScale, Threshold threshold) {
            super(sensorType, name, minScale, maxScale, threshold);
        }
    }

    private final JLabel titleLabel;
    private final ModernBadge statusBadge;
    private final JPanel gaugesContainer;
    private final Map<SensorType, ModernGaugeCard> activeWidgets = new LinkedHashMap<>();

    private String currentMachineId = "M01";

    public SensorPanel() {
        setLayout(new BorderLayout(8, 10));
        setBackground(UITheme.BG_DARK);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // En-tête de la machine sélectionnée
        JPanel headerPanel = new JPanel(new BorderLayout(8, 0));
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER),
                BorderFactory.createEmptyBorder(0, 4, 10, 4)
        ));

        titleLabel = new JLabel("M01 — Presse hydraulique");
        titleLabel.setFont(UITheme.FONT_TITLE);
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);

        statusBadge = new ModernBadge(MachineState.STOPPED);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(statusBadge, BorderLayout.EAST);

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
        ModernGaugeCard card = new ModernGaugeCard(type, name, min, max, threshold);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));
        activeWidgets.put(type, card);
        gaugesContainer.add(card);
        gaugesContainer.add(Box.createRigidArea(new Dimension(0, 10)));
    }

    public synchronized void updateData(IndustrialSupervisor supervisor) {
        if (supervisor == null) return;

        IndustrialSupervisor.MachineSupervisionInfo info = supervisor.getMachineInfo(currentMachineId);
        if (info != null) {
            statusBadge.setState(info.getState());

            for (Map.Entry<SensorType, Double> entry : info.getLatestReadings().entrySet()) {
                ModernGaugeCard card = activeWidgets.get(entry.getKey());
                if (card != null) {
                    card.updateValue(entry.getValue());
                }
            }
        }
    }

    public String getCurrentMachineId() {
        return currentMachineId;
    }
}
