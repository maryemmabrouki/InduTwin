package ui.industrial;

import persistence.PersistenceService;
import ui.industrial.components.ModernBadge;
import ui.industrial.components.ModernButton;
import ui.industrial.theme.UITheme;

import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Panneau graphique SCADA Swing d'historique et d'audit industriel alimenté par SQLite JDBC.
 * Permet la consultation des alarmes passées, des commandes TCP auditées et de la chronologie machine dans le thème Dark Slate.
 */
public class DatabaseHistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final PersistenceService persistenceService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final ModernBadge statusBadge;
    private final JLabel statsSummaryLabel;
    private final ModernButton btnRefresh;

    private final DefaultTableModel alarmsTableModel;
    private final DefaultTableModel commandsTableModel;
    private final DefaultTableModel machineHistoryTableModel;
    private final JComboBox<String> machineSelector;

    private final ExecutorService queryExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SQLite-History-Query-Worker");
        t.setDaemon(true);
        return t;
    });

    public DatabaseHistoryPanel(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
        setLayout(new BorderLayout(8, 8));
        setBackground(UITheme.SURFACE);
        setBorder(UITheme.createCardBorder());

        // 1. Barre d'outils supérieure
        JPanel topBar = new JPanel(new BorderLayout(8, 4));
        topBar.setOpaque(false);

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("HISTORIQUE & AUDIT INDUSTRIEL (SQLITE JDBC)");
        titleLabel.setFont(UITheme.FONT_SUBTITLE);
        titleLabel.setForeground(UITheme.TEXT_PRIMARY);

        statusBadge = new ModernBadge("SQLite : Connecté", UITheme.STATUS_RUNNING);

        titlePanel.add(titleLabel);
        titlePanel.add(statusBadge);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlsPanel.setOpaque(false);

        statsSummaryLabel = new JLabel("Télémétrie: - | Alarmes: - | Commandes: -");
        statsSummaryLabel.setFont(UITheme.FONT_SMALL);
        statsSummaryLabel.setForeground(UITheme.TEXT_SECONDARY);

        btnRefresh = new ModernButton("Rafraîchir SQLite", ModernButton.ButtonVariant.PRIMARY);
        btnRefresh.setFont(UITheme.FONT_SMALL);
        btnRefresh.addActionListener(e -> refreshHistoryAsync());

        controlsPanel.add(statsSummaryLabel);
        controlsPanel.add(btnRefresh);

        topBar.add(titlePanel, BorderLayout.WEST);
        topBar.add(controlsPanel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 2. Onglets de consultation
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(UITheme.FONT_SUBTITLE);
        tabbedPane.setBackground(UITheme.SURFACE);
        tabbedPane.setForeground(UITheme.TEXT_PRIMARY);

        // Onglet 1 : Alarmes historisées
        String[] alarmCols = {"ID", "Horodatage", "Machine", "Niveau", "Événement", "Capteur", "Valeur", "Description"};
        alarmsTableModel = new DefaultTableModel(alarmCols, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable alarmsTable = new JTable(alarmsTableModel);
        UITheme.styleTable(alarmsTable);
        alarmsTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        alarmsTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        alarmsTable.getColumnModel().getColumn(2).setPreferredWidth(65);
        alarmsTable.getColumnModel().getColumn(3).setPreferredWidth(85);
        alarmsTable.getColumnModel().getColumn(4).setPreferredWidth(130);
        alarmsTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        alarmsTable.getColumnModel().getColumn(6).setPreferredWidth(65);
        alarmsTable.getColumnModel().getColumn(7).setPreferredWidth(250);

        alarmsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                String levelStr = String.valueOf(tbl.getValueAt(row, 3));

                if (isSelected) {
                    c.setBackground(UITheme.SURFACE_HOVER);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(row % 2 == 0 ? UITheme.SURFACE : UITheme.SURFACE_ALT);
                    if (col == 3) {
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
                    } else if (col == 1) {
                        c.setForeground(UITheme.TEXT_MUTED);
                    } else if (col == 2) {
                        c.setForeground(UITheme.BORDER_ACTIVE);
                    } else {
                        c.setForeground(UITheme.TEXT_PRIMARY);
                    }
                }
                return c;
            }
        });

        JScrollPane alarmsScroll = new JScrollPane(alarmsTable);
        UITheme.styleScrollPane(alarmsScroll);
        tabbedPane.addTab("Alarmes Historiques", alarmsScroll);

        // Onglet 2 : Commandes TCP auditées
        String[] cmdCols = {"ID", "Horodatage", "Command ID", "Machine", "Commande", "Statut", "Message retourné"};
        commandsTableModel = new DefaultTableModel(cmdCols, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable commandsTable = new JTable(commandsTableModel);
        UITheme.styleTable(commandsTable);
        commandsTable.getColumnModel().getColumn(0).setPreferredWidth(45);
        commandsTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        commandsTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        commandsTable.getColumnModel().getColumn(3).setPreferredWidth(65);
        commandsTable.getColumnModel().getColumn(4).setPreferredWidth(100);
        commandsTable.getColumnModel().getColumn(5).setPreferredWidth(90);
        commandsTable.getColumnModel().getColumn(6).setPreferredWidth(250);

        commandsTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                String statusStr = String.valueOf(tbl.getValueAt(row, 5));

                if (isSelected) {
                    c.setBackground(UITheme.SURFACE_HOVER);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(row % 2 == 0 ? UITheme.SURFACE : UITheme.SURFACE_ALT);
                    if (col == 5) {
                        if ("SUCCESS".equalsIgnoreCase(statusStr) || "EXECUTED".equalsIgnoreCase(statusStr) || "ACK".equalsIgnoreCase(statusStr)) {
                            c.setForeground(UITheme.STATUS_RUNNING);
                            c.setFont(c.getFont().deriveFont(Font.BOLD));
                        } else if ("REJECTED".equalsIgnoreCase(statusStr) || "FAILED".equalsIgnoreCase(statusStr) || "NACK".equalsIgnoreCase(statusStr)) {
                            c.setForeground(UITheme.STATUS_CRITICAL);
                            c.setFont(c.getFont().deriveFont(Font.BOLD));
                        } else {
                            c.setForeground(UITheme.STATUS_WARNING);
                            c.setFont(c.getFont().deriveFont(Font.PLAIN));
                        }
                    } else if (col == 1) {
                        c.setForeground(UITheme.TEXT_MUTED);
                    } else if (col == 3) {
                        c.setForeground(UITheme.BORDER_ACTIVE);
                    } else {
                        c.setForeground(UITheme.TEXT_PRIMARY);
                    }
                }
                return c;
            }
        });

        JScrollPane commandsScroll = new JScrollPane(commandsTable);
        UITheme.styleScrollPane(commandsScroll);
        tabbedPane.addTab("Commandes Auditées (TCP)", commandsScroll);

        // Onglet 3 : Chronologie par Machine
        JPanel machineHistoryPanel = new JPanel(new BorderLayout(4, 6));
        machineHistoryPanel.setBackground(UITheme.SURFACE);
        machineHistoryPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        selectorPanel.setOpaque(false);

        JLabel lblTarget = new JLabel("Machine ciblée :");
        lblTarget.setFont(UITheme.FONT_SMALL);
        lblTarget.setForeground(UITheme.TEXT_SECONDARY);
        selectorPanel.add(lblTarget);

        machineSelector = new JComboBox<>(new String[]{"M01", "M02", "M03"});
        UITheme.styleComboBox(machineSelector);
        machineSelector.addActionListener(e -> refreshHistoryAsync());
        selectorPanel.add(machineSelector);

        machineHistoryPanel.add(selectorPanel, BorderLayout.NORTH);

        String[] histCols = {"Chronologie & Événements Reconstitués"};
        machineHistoryTableModel = new DefaultTableModel(histCols, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable historyTable = new JTable(machineHistoryTableModel);
        UITheme.styleTable(historyTable);
        historyTable.setFont(UITheme.FONT_VALUE_SMALL);

        historyTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable tbl, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(tbl, value, isSelected, hasFocus, row, col);
                if (isSelected) {
                    c.setBackground(UITheme.SURFACE_HOVER);
                    c.setForeground(Color.WHITE);
                } else {
                    c.setBackground(row % 2 == 0 ? UITheme.SURFACE : UITheme.SURFACE_ALT);
                    c.setForeground(UITheme.TEXT_PRIMARY);
                }
                c.setFont(UITheme.FONT_VALUE_SMALL);
                return c;
            }
        });

        JScrollPane historyScroll = new JScrollPane(historyTable);
        UITheme.styleScrollPane(historyScroll);
        machineHistoryPanel.add(historyScroll, BorderLayout.CENTER);

        tabbedPane.addTab("Chronologie Machine", machineHistoryPanel);

        add(tabbedPane, BorderLayout.CENTER);

        // Chargement initial
        refreshHistoryAsync();
    }

    /**
     * Interroge SQLite de façon strictement asynchrone hors de l'EDT Swing.
     */
    public void refreshHistoryAsync() {
        if (persistenceService == null || !persistenceService.isRunning()) return;

        btnRefresh.setEnabled(false);
        String selectedMachine = (String) machineSelector.getSelectedItem();

        queryExecutor.submit(() -> {
            try {
                // 1. Statistiques
                Map<String, Object> stats = persistenceService.getStatistics();
                // 2. Alarmes récentes
                List<Map<String, Object>> alarms = persistenceService.getRecentAlarms(50);
                // 3. Commandes récentes
                List<Map<String, Object>> commands = persistenceService.getRecentCommands(50);
                // 4. Chronologie de la machine sélectionnée
                List<String> machineHistory = persistenceService.getMachineHistory(selectedMachine, 50);

                SwingUtilities.invokeLater(() -> {
                    updateTables(stats, alarms, commands, machineHistory, selectedMachine);
                    btnRefresh.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusBadge.setCustom("SQLite : Erreur", UITheme.STATUS_CRITICAL);
                    btnRefresh.setEnabled(true);
                });
            }
        });
    }

    private void updateTables(Map<String, Object> stats,
                              List<Map<String, Object>> alarms,
                              List<Map<String, Object>> commands,
                              List<String> machineHistory,
                              String selectedMachine) {
        // Mise à jour résumé
        if (stats != null) {
            statsSummaryLabel.setText(String.format("Télémétries: %s | Alarmes: %s | Commandes: %s",
                    stats.get("totalTelemetry"), stats.get("totalAlarms"), stats.get("totalCommands")));
            statusBadge.setCustom("SQLite : Connecté (" + stats.get("totalTelemetry") + " msg)", UITheme.STATUS_RUNNING);
        }

        // 1. Table Alarmes
        alarmsTableModel.setRowCount(0);
        if (alarms == null || alarms.isEmpty()) {
            alarmsTableModel.addRow(new Object[]{"--", "--", "--", "AUCUNE ALARME", "--", "--", "--", "Aucune alarme historisée en base SQLite."});
        } else {
            for (Map<String, Object> al : alarms) {
                long ts = (Long) al.get("timestamp");
                alarmsTableModel.addRow(new Object[]{
                        al.get("id"),
                        dateFormat.format(new Date(ts)),
                        al.get("machine_id"),
                        al.get("level"),
                        al.get("event_type"),
                        al.get("sensor_type") != null ? al.get("sensor_type") : "-",
                        al.get("value") != null ? al.get("value") : "-",
                        al.get("description")
                });
            }
        }

        // 2. Table Commandes
        commandsTableModel.setRowCount(0);
        if (commands == null || commands.isEmpty()) {
            commandsTableModel.addRow(new Object[]{"--", "--", "--", "--", "--", "AUCUNE COMMANDE", "Aucune commande TCP auditée dans la base."});
        } else {
            for (Map<String, Object> cmd : commands) {
                long ts = (Long) cmd.get("timestamp");
                commandsTableModel.addRow(new Object[]{
                        cmd.get("id"),
                        dateFormat.format(new Date(ts)),
                        cmd.get("command_id"),
                        cmd.get("machine_id"),
                        cmd.get("command_type"),
                        cmd.get("status"),
                        cmd.get("message")
                });
            }
        }

        // 3. Chronologie Machine
        machineHistoryTableModel.setRowCount(0);
        if (machineHistory == null || machineHistory.isEmpty()) {
            machineHistoryTableModel.addRow(new Object[]{"Aucun événement historisé pour " + selectedMachine + "."});
        } else {
            for (String line : machineHistory) {
                machineHistoryTableModel.addRow(new Object[]{line});
            }
        }
    }

    public void shutdown() {
        queryExecutor.shutdownNow();
    }
}
