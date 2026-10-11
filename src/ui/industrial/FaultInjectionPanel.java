package ui.industrial;

import industrial.FaultInjector;
import industrial.MachineSimulator;
import model.industrial.Machine;
import ui.industrial.components.ModernButton;
import ui.industrial.theme.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
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
        setBackground(UITheme.SURFACE);
        setBorder(UITheme.createCardBorder());

        JLabel titleLabel = new JLabel("INJECTION DE PANNES (TESTS / DÉMO)");
        titleLabel.setFont(UITheme.FONT_SUBTITLE);
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);
        add(titleLabel, BorderLayout.NORTH);

        JPanel buttonsGrid = new JPanel(new GridLayout(3, 2, 6, 6));
        buttonsGrid.setOpaque(false);

        ModernButton overheatBtn = new ModernButton("🔥 M02 : Surchauffe (98°C)", ModernButton.ButtonVariant.SECONDARY);
        overheatBtn.setCustomAccentColor(UITheme.STATUS_WARNING);
        overheatBtn.setFont(UITheme.FONT_SMALL);

        ModernButton vibBtn = new ModernButton("⚡ M01 : Vibration (9.5 mm/s)", ModernButton.ButtonVariant.SECONDARY);
        vibBtn.setCustomAccentColor(UITheme.STATUS_WARNING);
        vibBtn.setFont(UITheme.FONT_SMALL);

        ModernButton stopHbBtn = new ModernButton("🔌 M03 : Couper Heartbeat", ModernButton.ButtonVariant.SECONDARY);
        stopHbBtn.setCustomAccentColor(UITheme.STATUS_CRITICAL);
        stopHbBtn.setFont(UITheme.FONT_SMALL);

        ModernButton restoreHbBtn = new ModernButton("🔄 M03 : Rétablir Heartbeat", ModernButton.ButtonVariant.GHOST);
        restoreHbBtn.setFont(UITheme.FONT_SMALL);

        ModernButton stopPressBtn = new ModernButton("🛑 Arrêt Urgence M01", ModernButton.ButtonVariant.DANGER);
        stopPressBtn.setFont(UITheme.FONT_SMALL);

        ModernButton resetAllBtn = new ModernButton("✅ Rétablir Tout", ModernButton.ButtonVariant.PRIMARY);
        resetAllBtn.setFont(UITheme.FONT_SMALL);

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
        statusLabel.setFont(UITheme.FONT_TINY);
        statusLabel.setForeground(UITheme.TEXT_MUTED);
        statusLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 0));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void setStatus(String text) {
        statusLabel.setText("Dernière action : " + text);
    }
}
