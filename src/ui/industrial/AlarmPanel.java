package ui.industrial;

import model.industrial.AlarmLevel;
import model.industrial.IndustrialEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Panneau d'historique des événements et alarmes industrielles avec filtrage par sévérité et par machine.
 */
public class AlarmPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JComboBox<String> levelFilterCombo;
    private final JComboBox<String> machineFilterCombo;

    private final List<IndustrialEvent> allEvents = new ArrayList<>();
    private static final int MAX_EVENTS = 500;
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    public AlarmPanel(Runnable onClearHistory) {
        setLayout(new BorderLayout(6, 6));
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(226, 232, 240), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        // Barre d'outils et filtres supérieure
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);

        JLabel titleLabel = new JLabel("JOURNAL DES ÉVÉNEMENTS & ALARMES");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(new Color(71, 85, 105));
        toolbar.add(titleLabel, BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filterPanel.setOpaque(false);

        filterPanel.add(new JLabel("Niveau :"));
        levelFilterCombo = new JComboBox<>(new String[]{"Tous", "CRITICAL", "WARNING", "INFO"});
        levelFilterCombo.addActionListener(e -> applyFilter());
        filterPanel.add(levelFilterCombo);

        filterPanel.add(new JLabel("Machine :"));
        machineFilterCombo = new JComboBox<>(new String[]{"Toutes", "M01", "M02", "M03"});
        machineFilterCombo.addActionListener(e -> applyFilter());
        filterPanel.add(machineFilterCombo);

        JButton clearBtn = new JButton("Effacer");
        clearBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        clearBtn.addActionListener(e -> {
            clearEvents();
            if (onClearHistory != null) onClearHistory.run();
        });
        filterPanel.add(clearBtn);

        toolbar.add(filterPanel, BorderLayout.EAST);
        add(toolbar, BorderLayout.NORTH);

        // Tableau des alarmes
        String[] columns = {"Heure", "Niveau", "Machine", "Événement", "Description"};
        tableModel = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(22);
        table.setFont(new Font("SansSerif", Font.PLAIN, 11));
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));

        // Largeurs de colonnes optimisées
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(80);
        table.getColumnModel().getColumn(2).setPreferredWidth(65);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.getColumnModel().getColumn(4).setPreferredWidth(260);

        // Rendu coloré par gravité
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                String levelStr = (String) tbl.getValueAt(row, 1);

                if (!isSelected) {
                    if ("CRITICAL".equalsIgnoreCase(levelStr)) {
                        c.setBackground(new Color(254, 226, 226)); // Rouge très clair
                        c.setForeground(new Color(153, 27, 27));   // Rouge foncé
                    } else if ("WARNING".equalsIgnoreCase(levelStr)) {
                        c.setBackground(new Color(254, 243, 199)); // Ambre très clair
                        c.setForeground(new Color(180, 83, 9));    // Ambre foncé
                    } else {
                        c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 250, 252));
                        c.setForeground(new Color(30, 41, 59));
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(226, 232, 240)));
        add(scrollPane, BorderLayout.CENTER);
    }

    public synchronized void addEvent(IndustrialEvent event) {
        if (event == null) return;

        if (allEvents.size() >= MAX_EVENTS) {
            allEvents.remove(0);
        }
        allEvents.add(event);

        if (matchesFilter(event)) {
            insertEventToTable(event);
        }
    }

    private void insertEventToTable(IndustrialEvent event) {
        String timeStr = TIME_FMT.format(new Date(event.getTimestamp()));
        // Ajout en première ligne pour afficher les plus récents en haut
        tableModel.insertRow(0, new Object[]{
                timeStr,
                event.getLevel().name(),
                event.getMachineId(),
                event.getEventType().name(),
                event.getMessage()
        });

        // Limite visuelle à 500 lignes
        if (tableModel.getRowCount() > MAX_EVENTS) {
            tableModel.removeRow(tableModel.getRowCount() - 1);
        }
    }

    private boolean matchesFilter(IndustrialEvent event) {
        String levelFilter = (String) levelFilterCombo.getSelectedItem();
        String machineFilter = (String) machineFilterCombo.getSelectedItem();

        if (!"Tous".equalsIgnoreCase(levelFilter) && !event.getLevel().name().equalsIgnoreCase(levelFilter)) {
            return false;
        }
        if (!"Toutes".equalsIgnoreCase(machineFilter) && !event.getMachineId().equalsIgnoreCase(machineFilter)) {
            return false;
        }
        return true;
    }

    private synchronized void applyFilter() {
        tableModel.setRowCount(0);
        for (IndustrialEvent ev : allEvents) {
            if (matchesFilter(ev)) {
                insertEventToTable(ev);
            }
        }
    }

    public synchronized void clearEvents() {
        allEvents.clear();
        tableModel.setRowCount(0);
    }
}
