package ui.industrial;

import model.industrial.IndustrialEvent;

import ui.industrial.components.ModernButton;
import ui.industrial.theme.UITheme;

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
        setBackground(UITheme.SURFACE);
        setBorder(UITheme.createCardBorder());

        // Barre d'outils et filtres supérieure
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);

        JLabel titleLabel = new JLabel("JOURNAL DES ÉVÉNEMENTS & ALARMES");
        titleLabel.setFont(UITheme.FONT_SUBTITLE);
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);
        toolbar.add(titleLabel, BorderLayout.WEST);

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        filterPanel.setOpaque(false);

        JLabel lblLevel = new JLabel("Niveau :");
        lblLevel.setFont(UITheme.FONT_SMALL);
        lblLevel.setForeground(UITheme.TEXT_SECONDARY);
        filterPanel.add(lblLevel);

        levelFilterCombo = new JComboBox<>(new String[]{"Tous", "CRITICAL", "WARNING", "INFO"});
        UITheme.styleComboBox(levelFilterCombo);
        levelFilterCombo.addActionListener(e -> applyFilter());
        filterPanel.add(levelFilterCombo);

        JLabel lblMachine = new JLabel("Machine :");
        lblMachine.setFont(UITheme.FONT_SMALL);
        lblMachine.setForeground(UITheme.TEXT_SECONDARY);
        filterPanel.add(lblMachine);

        machineFilterCombo = new JComboBox<>(new String[]{"Toutes", "M01", "M02", "M03"});
        UITheme.styleComboBox(machineFilterCombo);
        machineFilterCombo.addActionListener(e -> applyFilter());
        filterPanel.add(machineFilterCombo);

        ModernButton clearBtn = new ModernButton("Effacer", ModernButton.ButtonVariant.GHOST);
        clearBtn.setFont(UITheme.FONT_SMALL);
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
        UITheme.styleTable(table);

        // Largeurs de colonnes optimisées
        table.getColumnModel().getColumn(0).setPreferredWidth(70);
        table.getColumnModel().getColumn(1).setPreferredWidth(85);
        table.getColumnModel().getColumn(2).setPreferredWidth(65);
        table.getColumnModel().getColumn(3).setPreferredWidth(160);
        table.getColumnModel().getColumn(4).setPreferredWidth(260);

        // Rendu stylisé Dark Slate par gravité
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                String levelStr = String.valueOf(tbl.getValueAt(row, 1));

                if (isSelected) {
                    c.setBackground(UITheme.SURFACE_HOVER);
                    c.setForeground(Color.WHITE);
                } else {
                    // Lignes alternées sombres
                    c.setBackground(row % 2 == 0 ? UITheme.SURFACE : UITheme.SURFACE_ALT);

                    if (col == 1) {
                        // Colonne Niveau d'alarme
                        if ("CRITICAL".equalsIgnoreCase(levelStr)) {
                            c.setForeground(UITheme.STATUS_CRITICAL);
                            c.setFont(c.getFont().deriveFont(Font.BOLD));
                        } else if ("WARNING".equalsIgnoreCase(levelStr)) {
                            c.setForeground(UITheme.STATUS_WARNING);
                            c.setFont(c.getFont().deriveFont(Font.BOLD));
                        } else if ("INFO".equalsIgnoreCase(levelStr)) {
                            c.setForeground(UITheme.STATUS_MAINTENANCE);
                            c.setFont(c.getFont().deriveFont(Font.PLAIN));
                        } else {
                            c.setForeground(UITheme.STATUS_RUNNING);
                            c.setFont(c.getFont().deriveFont(Font.PLAIN));
                        }
                    } else if (col == 0) {
                        // Heure atténuée
                        c.setForeground(UITheme.TEXT_MUTED);
                    } else if (col == 2) {
                        // Machine en cyan
                        c.setForeground(UITheme.BORDER_ACTIVE);
                    } else {
                        c.setForeground(UITheme.TEXT_PRIMARY);
                    }
                }
                return c;
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        UITheme.styleScrollPane(scrollPane);
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
