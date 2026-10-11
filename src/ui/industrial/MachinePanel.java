package ui.industrial;

import industrial.IndustrialSupervisor;
import model.industrial.MachineState;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Panneau listant les machines industrielles avec leur statut temps réel.
 * Permet à l'opérateur de sélectionner une machine pour inspecter ses capteurs.
 */
public class MachinePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public static class MachineCard extends JPanel {
        private static final long serialVersionUID = 1L;

        private final String id;
        private final String name;
        private final MachineStatusIndicator statusIndicator;
        private final JLabel lastSeenLabel;
        private boolean isSelected = false;

        public MachineCard(String id, String name, String subtitle, Consumer<String> onSelect) {
            this.id = id;
            this.name = name;

            setLayout(new BorderLayout(8, 4));
            setBackground(Color.WHITE);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)
            ));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            JPanel textPanel = new JPanel(new GridLayout(2, 1, 2, 2));
            textPanel.setOpaque(false);

            JLabel titleLabel = new JLabel(id + " — " + name);
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 13));

            lastSeenLabel = new JLabel("Dernier battement : En attente...");
            lastSeenLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
            lastSeenLabel.setForeground(new Color(100, 116, 139));

            textPanel.add(titleLabel);
            textPanel.add(lastSeenLabel);

            statusIndicator = new MachineStatusIndicator(MachineState.STOPPED);

            add(textPanel, BorderLayout.CENTER);
            add(statusIndicator, BorderLayout.EAST);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (onSelect != null) {
                        onSelect.accept(id);
                    }
                }
            });
        }

        public void setSelected(boolean selected) {
            this.isSelected = selected;
            if (selected) {
                setBackground(new Color(240, 249, 255));
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(59, 130, 246), 2),
                        BorderFactory.createEmptyBorder(7, 9, 7, 9)
                ));
            } else {
                setBackground(Color.WHITE);
                setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10)
                ));
            }
            repaint();
        }

        public void updateState(MachineState state, long lastSeen) {
            statusIndicator.updateState(state);
            long elapsed = (System.currentTimeMillis() - lastSeen) / 1000;
            if (lastSeen <= 0) {
                lastSeenLabel.setText("Dernier battement : Aucun");
            } else if (elapsed <= 1) {
                lastSeenLabel.setText("Dernier battement : Instantané");
            } else {
                lastSeenLabel.setText("Dernier battement : il y a " + elapsed + " s");
            }
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    private final Map<String, MachineCard> cards = new LinkedHashMap<>();
    private String selectedMachineId = "M01";
    private Consumer<String> selectionListener;

    public MachinePanel(Consumer<String> onSelectMachine) {
        this.selectionListener = onSelectMachine;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(false);

        // Header du panneau
        JLabel headerLabel = new JLabel("PARC DES MACHINES");
        headerLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        headerLabel.setForeground(new Color(71, 85, 105));
        headerLabel.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 0));
        add(headerLabel);

        // Création des cartes pour M01, M02, M03
        addMachineCard("M01", "Presse hydraulique", "4 capteurs");
        addMachineCard("M02", "Four industriel", "3 capteurs");
        addMachineCard("M03", "Robot industriel", "4 capteurs");

        // Sélection par défaut de M01
        selectMachine("M01");
    }

    private void addMachineCard(String id, String name, String subtitle) {
        MachineCard card = new MachineCard(id, name, subtitle, this::selectMachine);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 65));
        cards.put(id, card);
        add(card);
        add(Box.createRigidArea(new Dimension(0, 8)));
    }

    public void selectMachine(String machineId) {
        this.selectedMachineId = machineId;
        for (MachineCard card : cards.values()) {
            card.setSelected(card.getId().equals(machineId));
        }
        if (selectionListener != null) {
            selectionListener.accept(machineId);
        }
    }

    public void updateMachines(IndustrialSupervisor supervisor) {
        if (supervisor == null) return;

        for (Map.Entry<String, IndustrialSupervisor.MachineSupervisionInfo> entry : supervisor.getMachines().entrySet()) {
            MachineCard card = cards.get(entry.getKey());
            if (card != null) {
                card.updateState(entry.getValue().getState(), entry.getValue().getLastSeenTimestamp());
            }
        }
    }

    public String getSelectedMachineId() {
        return selectedMachineId;
    }
}
