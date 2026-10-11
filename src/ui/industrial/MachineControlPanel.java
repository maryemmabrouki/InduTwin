package ui.industrial;

import control.ControlClient;
import control.ControlCommand;
import control.ControlCommandType;
import control.ControlResponse;
import control.ControlStatus;
import industrial.IndustrialSupervisor;
import model.industrial.MachineState;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
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
    private final JLabel stateBadge;
    private final JLabel lastCmdLabel;
    private final JLabel lastResponseLabel;

    private final JButton btnStart;
    private final JButton btnStop;
    private final JButton btnMaint;
    private final JButton btnReset;
    private final JButton btnEmergencyStop;

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "TCP-Control-UI-Worker");
        t.setDaemon(true);
        return t;
    });

    public MachineControlPanel() {
        setLayout(new BorderLayout(8, 8));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));

        // 1. En-tête : Titre et état actuel
        JPanel headerPanel = new JPanel(new BorderLayout(8, 4));
        headerPanel.setOpaque(false);

        titleLabel = new JLabel("CONTRÔLE COMMANDE TCP — " + currentMachineId);
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(new Color(30, 41, 59));

        stateBadge = new JLabel("ÉTAT : " + currentMachineState);
        stateBadge.setFont(new Font("SansSerif", Font.BOLD, 11));
        stateBadge.setForeground(new Color(100, 116, 139));

        headerPanel.add(titleLabel, BorderLayout.WEST);
        headerPanel.add(stateBadge, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // 2. Grille de boutons de commande
        JPanel buttonsContainer = new JPanel();
        buttonsContainer.setLayout(new BoxLayout(buttonsContainer, BoxLayout.Y_AXIS));
        buttonsContainer.setOpaque(false);

        JPanel standardButtons = new JPanel(new GridLayout(2, 2, 6, 6));
        standardButtons.setOpaque(false);

        btnStart = createActionButton("▶ START", new Color(16, 185, 129), e -> sendCommandAsync(ControlCommandType.START));
        btnStop = createActionButton("⏹ STOP", new Color(245, 158, 11), e -> sendCommandAsync(ControlCommandType.STOP));
        btnMaint = createActionButton("🛠 MAINTENANCE", new Color(59, 130, 246), e -> sendCommandAsync(ControlCommandType.MAINTENANCE));
        btnReset = createActionButton("🔄 RESET", new Color(100, 116, 139), e -> sendCommandAsync(ControlCommandType.RESET));

        standardButtons.add(btnStart);
        standardButtons.add(btnStop);
        standardButtons.add(btnMaint);
        standardButtons.add(btnReset);

        buttonsContainer.add(standardButtons);
        buttonsContainer.add(Box.createRigidArea(new Dimension(0, 8)));

        // Bouton EMERGENCY STOP proéminent et rouge
        btnEmergencyStop = new JButton("🛑 ARRÊT D'URGENCE (EMERGENCY STOP)");
        btnEmergencyStop.setFont(new Font("SansSerif", Font.BOLD, 12));
        btnEmergencyStop.setForeground(Color.WHITE);
        btnEmergencyStop.setBackground(new Color(220, 38, 38));
        btnEmergencyStop.setFocusPainted(false);
        btnEmergencyStop.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnEmergencyStop.setPreferredSize(new Dimension(0, 34));
        btnEmergencyStop.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        btnEmergencyStop.addActionListener(e -> sendCommandAsync(ControlCommandType.EMERGENCY_STOP));

        buttonsContainer.add(btnEmergencyStop);
        add(buttonsContainer, BorderLayout.CENTER);

        // 3. Pied : Affichage de l'accusé de réception (ACK/NACK)
        JPanel statusFooter = new JPanel(new GridLayout(2, 1, 2, 2));
        statusFooter.setOpaque(false);
        statusFooter.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        lastCmdLabel = new JLabel("Dernière commande : Aucune");
        lastCmdLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        lastCmdLabel.setForeground(new Color(71, 85, 105));

        lastResponseLabel = new JLabel("Réponse TCP : En attente");
        lastResponseLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        lastResponseLabel.setForeground(new Color(148, 163, 184));

        statusFooter.add(lastCmdLabel);
        statusFooter.add(lastResponseLabel);
        add(statusFooter, BorderLayout.SOUTH);
    }

    private JButton createActionButton(String text, Color accentColor, java.awt.event.ActionListener listener) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("SansSerif", Font.BOLD, 11));
        btn.setForeground(new Color(30, 41, 59));
        btn.setBackground(new Color(248, 250, 252));
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(listener);
        return btn;
    }

    public void setMachine(String machineId, MachineState state) {
        this.currentMachineId = machineId != null ? machineId : "M01";
        if (state != null) {
            this.currentMachineState = state;
        }
        titleLabel.setText("CONTRÔLE COMMANDE TCP — " + this.currentMachineId);
        stateBadge.setText("ÉTAT : " + this.currentMachineState);
        updateStateBadgeColor(this.currentMachineState);
    }

    public void updateState(IndustrialSupervisor supervisor) {
        if (supervisor == null) return;
        IndustrialSupervisor.MachineSupervisionInfo info = supervisor.getMachineInfo(currentMachineId);
        if (info != null && info.getState() != null) {
            this.currentMachineState = info.getState();
            stateBadge.setText("ÉTAT : " + this.currentMachineState);
            updateStateBadgeColor(this.currentMachineState);
        }
    }

    private void updateStateBadgeColor(MachineState state) {
        if (state == MachineState.RUNNING) {
            stateBadge.setForeground(new Color(22, 163, 74));
        } else if (state == MachineState.WARNING) {
            stateBadge.setForeground(new Color(217, 119, 6));
        } else if (state == MachineState.CRITICAL || state == MachineState.FAILURE) {
            stateBadge.setForeground(new Color(220, 38, 38));
        } else if (state == MachineState.MAINTENANCE) {
            stateBadge.setForeground(new Color(37, 99, 235));
        } else {
            stateBadge.setForeground(new Color(100, 116, 139));
        }
    }

    private void sendCommandAsync(ControlCommandType type) {
        String machine = this.currentMachineId;
        String cmdId = "cmd-" + (System.currentTimeMillis() % 100000);
        ControlCommand cmd = new ControlCommand(cmdId, machine, type);

        lastCmdLabel.setText(String.format("Dernière commande : %s sur %s (%s)", type, machine, cmdId));
        lastResponseLabel.setText("Envoi TCP en cours...");
        lastResponseLabel.setForeground(new Color(59, 130, 246));

        // Exécution strictly hors de l'EDT Swing
        networkExecutor.submit(() -> {
            try (ControlClient client = new ControlClient()) {
                ControlResponse response = client.sendCommand(cmd);
                SwingUtilities.invokeLater(() -> displayResponse(response));
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    lastResponseLabel.setText("Erreur TCP: " + e.getMessage());
                    lastResponseLabel.setForeground(new Color(220, 38, 38));
                });
            }
        });
    }

    private void displayResponse(ControlResponse resp) {
        if (resp == null) {
            lastResponseLabel.setText("Réponse : Nulle / Timeout");
            lastResponseLabel.setForeground(new Color(220, 38, 38));
            return;
        }

        String text = String.format("Réponse : ACK [%s] - %s", resp.getStatus(), resp.getMessage());
        lastResponseLabel.setText(text);

        if (resp.getStatus() == ControlStatus.ACCEPTED) {
            lastResponseLabel.setForeground(new Color(22, 163, 74));
        } else if (resp.getStatus() == ControlStatus.REJECTED) {
            lastResponseLabel.setForeground(new Color(217, 119, 6));
        } else {
            lastResponseLabel.setForeground(new Color(220, 38, 38));
        }
    }

    public void shutdown() {
        networkExecutor.shutdownNow();
    }
}
