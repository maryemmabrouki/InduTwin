package ui.industrial;

import industrial.FaultInjector;
import industrial.MachineSimulator;
import model.industrial.Machine;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Panneau de contrôle des scénarios de test et d'injection de pannes (Fault Injection).
 * Déclenche les anomalies directement sur le simulateur industriel.
 */
public class FaultInjectionPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel statusLabel;

    public FaultInjectionPanel(MachineSimulator simulator) {
        setLayout(new BorderLayout(6, 6));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        JLabel titleLabel = new JLabel("INJECTION DE PANNES (TESTS / DÉMO)");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(new Color(71, 85, 105));
        add(titleLabel, BorderLayout.NORTH);

        JPanel buttonsGrid = new JPanel(new GridLayout(3, 2, 6, 6));
        buttonsGrid.setOpaque(false);

        JButton overheatBtn = new JButton("🔥 M02 : Surchauffe (98°C)");
        JButton vibBtn = new JButton("⚡ M01 : Vibration (9.5 mm/s)");
        JButton stopHbBtn = new JButton("🔌 M03 : Couper Heartbeat");
        JButton restoreHbBtn = new JButton("🔄 M03 : Rétablir Heartbeat");
        JButton resetAllBtn = new JButton("✅ Rétablir Tout");
        JButton stopPressBtn = new JButton("🛑 Arrêt Urgence M01");

        overheatBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        vibBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        stopHbBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        restoreHbBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        resetAllBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        stopPressBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));

        overheatBtn.addActionListener(e -> {
            if (simulator != null) {
                Machine m = simulator.getMachine("M02");
                simulator.getFaultInjector().injectOverheating(m, 98.0);
                setStatus("Surchauffe injectée sur M02 (cible: 98°C)");
            }
        });

        vibBtn.addActionListener(e -> {
            if (simulator != null) {
                Machine m = simulator.getMachine("M01");
                simulator.getFaultInjector().injectExcessiveVibration(m, 9.5);
                setStatus("Vibration excessive injectée sur M01 (cible: 9.5 mm/s)");
            }
        });

        stopHbBtn.addActionListener(e -> {
            if (simulator != null) {
                Machine m = simulator.getMachine("M03");
                simulator.getFaultInjector().injectHeartbeatLoss(m);
                setStatus("Heartbeat coupé pour M03 (Attente timeout 6s...)");
            }
        });

        restoreHbBtn.addActionListener(e -> {
            if (simulator != null) {
                Machine m = simulator.getMachine("M03");
                simulator.getFaultInjector().restoreNormalOperation(m);
                setStatus("Heartbeat rétabli pour M03 (RECOVERED)");
            }
        });

        resetAllBtn.addActionListener(e -> {
            if (simulator != null) {
                for (Machine m : simulator.getMachines().values()) {
                    simulator.getFaultInjector().restoreNormalOperation(m);
                }
                setStatus("Toutes les machines ont été rétablies à l'état nominal");
            }
        });

        stopPressBtn.addActionListener(e -> {
            if (simulator != null) {
                Machine m = simulator.getMachine("M01");
                m.transitionTo(model.industrial.MachineState.FAILURE);
                setStatus("Arrêt d'urgence déclenché sur M01 (FAILURE)");
            }
        });

        buttonsGrid.add(overheatBtn);
        buttonsGrid.add(vibBtn);
        buttonsGrid.add(stopHbBtn);
        buttonsGrid.add(restoreHbBtn);
        buttonsGrid.add(stopPressBtn);
        buttonsGrid.add(resetAllBtn);

        add(buttonsGrid, BorderLayout.CENTER);

        statusLabel = new JLabel("Prêt pour scénarios d'anomalies.");
        statusLabel.setFont(new Font("SansSerif", Font.ITALIC, 11));
        statusLabel.setForeground(new Color(100, 116, 139));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void setStatus(String text) {
        statusLabel.setText("Dernière action : " + text);
    }
}
