package ui.industrial;

import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlStatus;
import industrial.IndustrialSupervisor;
import model.industrial.MachineState;
import ui.industrial.components.ModernBadge;
import ui.industrial.components.ModernButton;
import ui.industrial.theme.UITheme;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Panneau de contrôle industriel Swing pour l'émission de commandes TCP point-à-point fiables.
 * Toutes les requêtes réseau sont exécutées en tâche de fond hors de l'EDT Swing.
 */
public class MachineControlPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private String currentMachineId = "M01";
    private MachineState currentMachineState = MachineState.STOPPED;

    private final JLabel titleLabel;
    private final ModernBadge stateBadge;
    private final JLabel lastCmdLabel;
    private final JLabel lastResponseLabel;

    private final ModernButton btnStart;
    private final ModernButton btnStop;
    private final ModernButton btnMaint;
    private final ModernButton btnReset;
    private final ModernButton btnEmergencyStop;

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TCP-Control-UI-Worker");
        t.setDaemon(true);
        return t;
    });

    public MachineControlPanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(UITheme.SURFACE);
        setBorder(UITheme.createCardBorder());

        // 1. En-tête : Titre et état actuel
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setOpaque(false);

        titleLabel = new JLabel("CONTRÔLE COMMANDE TCP — " + currentMachineId);
        titleLabel.setFont(UITheme.FONT_SUBTITLE);
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);

        stateBadge = new ModernBadge(currentMachineState);

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(stateBadge, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // 2. Grille de boutons de commande
        JPanel buttonsContainer = new JPanel();
        buttonsContainer.setLayout(new BoxLayout(buttonsContainer, BoxLayout.Y_AXIS));
        buttonsContainer.setOpaque(false);

        JPanel standardButtons = new JPanel(new GridLayout(2, 2, 6, 6));
        standardButtons.setOpaque(false);

        btnStart = new ModernButton("▶ START", ModernButton.ButtonVariant.PRIMARY);
        btnStart.addActionListener(e -> sendCommandAsync(ControlCommandType.START));

        btnStop = new ModernButton("⏹ STOP", ModernButton.ButtonVariant.SECONDARY);
        btnStop.setCustomAccentColor(UITheme.STATUS_WARNING);
        btnStop.addActionListener(e -> sendCommandAsync(ControlCommandType.STOP));

        btnMaint = new ModernButton("🛠 MAINTENANCE", ModernButton.ButtonVariant.SECONDARY);
        btnMaint.setCustomAccentColor(UITheme.STATUS_MAINTENANCE);
        btnMaint.addActionListener(e -> sendCommandAsync(ControlCommandType.MAINTENANCE));

        btnReset = new ModernButton("🔄 RESET", ModernButton.ButtonVariant.GHOST);
        btnReset.addActionListener(e -> sendCommandAsync(ControlCommandType.RESET));

        standardButtons.add(btnStart);
        standardButtons.add(btnStop);
        standardButtons.add(btnMaint);
        standardButtons.add(btnReset);

        buttonsContainer.add(standardButtons);
        buttonsContainer.add(Box.createRigidArea(new Dimension(0, 8)));

        // Bouton EMERGENCY STOP proéminent et rouge
        btnEmergencyStop = new ModernButton("🛑 ARRÊT D'URGENCE (EMERGENCY STOP)", ModernButton.ButtonVariant.DANGER);
        btnEmergencyStop.setPreferredSize(new Dimension(0, 36));
        btnEmergencyStop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        btnEmergencyStop.addActionListener(e -> sendCommandAsync(ControlCommandType.EMERGENCY_STOP));

        buttonsContainer.add(btnEmergencyStop);
        add(buttonsContainer, BorderLayout.CENTER);

        // 3. Pied : Affichage de l'accusé de réception (ACK/NACK)
        JPanel statusFooter = new JPanel(new GridLayout(2, 1, 2, 2));
        statusFooter.setOpaque(false);
        statusFooter.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        lastCmdLabel = new JLabel("Dernière commande : Aucune");
        lastCmdLabel.setFont(UITheme.FONT_SMALL);
        lastCmdLabel.setForeground(UITheme.TEXT_SECONDARY);

        lastResponseLabel = new JLabel("Réponse TCP : En attente");
        lastResponseLabel.setFont(UITheme.FONT_SMALL);
        lastResponseLabel.setForeground(UITheme.TEXT_MUTED);

        statusFooter.add(lastCmdLabel);
        statusFooter.add(lastResponseLabel);
        add(statusFooter, BorderLayout.SOUTH);
    }

    public void setMachine(String machineId, MachineState state) {
        this.currentMachineId = machineId != null ? machineId : "M01";
        if (state != null) {
            this.currentMachineState = state;
        }
        titleLabel.setText("CONTRÔLE COMMANDE TCP — " + this.currentMachineId);
        stateBadge.setState(this.currentMachineState);
    }

    public void updateState(IndustrialSupervisor supervisor) {
        if (supervisor == null) return;
        IndustrialSupervisor.MachineSupervisionInfo info = supervisor.getMachineInfo(currentMachineId);
        if (info != null && info.getState() != null) {
            this.currentMachineState = info.getState();
            stateBadge.setState(this.currentMachineState);
        }
    }

    private void sendCommandAsync(ControlCommandType type) {
        String machine = this.currentMachineId;
        String cmdId = "cmd-" + (System.currentTimeMillis() % 100000);
        ControlCommand cmd = new ControlCommand(cmdId, machine, type);

        lastCmdLabel.setText(String.format("Dernière commande : %s sur %s (%s)", type, machine, cmdId));
        lastResponseLabel.setText("Envoi TCP en cours...");
        lastResponseLabel.setForeground(UITheme.STATUS_STARTING);

        // Exécution strictly hors de l'EDT Swing
        networkExecutor.submit(() -> {
            try (ControlClient client = new ControlClient()) {
                ControlResponse response = client.sendCommand(cmd);
                SwingUtilities.invokeLater(() -> displayResponse(response));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lastResponseLabel.setText("Erreur TCP: " + e.getMessage());
                    lastResponseLabel.setForeground(UITheme.STATUS_CRITICAL);
                });
            }
        });
    }

    private void displayResponse(ControlResponse resp) {
        if (resp == null) {
            lastResponseLabel.setText("Réponse : Nulle / Timeout");
            lastResponseLabel.setForeground(UITheme.STATUS_CRITICAL);
            return;
        }

        String text = String.format("Réponse : ACK [%s] - %s", resp.getStatus(), resp.getMessage());
        lastResponseLabel.setText(text);

        if (resp.getStatus() == ControlStatus.ACCEPTED) {
            lastResponseLabel.setForeground(UITheme.STATUS_RUNNING);
        } else if (resp.getStatus() == ControlStatus.REJECTED) {
            lastResponseLabel.setForeground(UITheme.STATUS_WARNING);
        } else {
            lastResponseLabel.setForeground(UITheme.STATUS_CRITICAL);
        }
    }

    public void shutdown() {
        networkExecutor.shutdownNow();
    }
}
