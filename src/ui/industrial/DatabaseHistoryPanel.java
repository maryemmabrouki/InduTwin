package ui.industrial;

import persistence.PersistenceService;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
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
 * Permet la consultation des alarmes passées, des commandes TCP auditées et de la chronologie machine.
 */
public class DatabaseHistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private final PersistenceService persistenceService;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final JLabel statusLabel;
    private final JLabel statsSummaryLabel;
    private final JButton btnRefresh;

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
        setBackground(Color.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(203, 213, 225), 1),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        // 1. Barre d'outils supérieure
        JPanel topBar = new JPanel(new BorderLayout(8, 4));
        topBar.setOpaque(false);

        JPanel titlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titlePanel.setOpaque(false);

        JLabel titleLabel = new JLabel("HISTORIQUE & AUDIT INDUSTRIEL (SQLITE JDBC)");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 12));
        titleLabel.setForeground(new Color(30, 41, 59));

        statusLabel = new JLabel("● SQLite : Initialisé");
        statusLabel.setFont(new Font("SansSerif", Font.BOLD, 11));
        statusLabel.setForeground(new Color(16, 185, 129));

        titlePanel.add(titleLabel);
        titlePanel.add(statusLabel);

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        controlsPanel.setOpaque(false);

        statsSummaryLabel = new JLabel("Télémétrie: - | Alarmes: - | Commandes: -");
        statsSummaryLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));
        statsSummaryLabel.setForeground(new Color(100, 116, 139));

        btnRefresh = new JButton("🔄 Rafraîchir SQLite");
        btnRefresh.setFont(new Font("SansSerif", Font.BOLD, 11));
        btnRefresh.setFocusPainted(false);
        btnRefresh.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btnRefresh.addActionListener(e -> refreshHistoryAsync());

        controlsPanel.add(statsSummaryLabel);
        controlsPanel.add(btnRefresh);

        topBar.add(titlePanel, BorderLayout.WEST);
        topBar.add(controlsPanel, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // 2. Onglets de consultation
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("SansSerif", Font.PLAIN, 12));

        // Onglet 1 : Alarmes historisées
        String[] alarmCols = {"ID", "Horodatage", "Machine", "Niveau", "Événement", "Capteur", "Valeur", "Description"};
        alarmsTableModel = new DefaultTableModel(alarmCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable alarmsTable = new JTable(alarmsTableModel);
        alarmsTable.setFont(new Font("SansSerif", Font.PLAIN, 11));
        alarmsTable.setRowHeight(20);
        alarmsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        tabbedPane.addTab("🚨 Alarmes Historiques", new JScrollPane(alarmsTable));

        // Onglet 2 : Commandes TCP auditées
        String[] cmdCols = {"ID", "Horodatage", "Command ID", "Machine", "Commande", "Statut", "Message retourné"};
        commandsTableModel = new DefaultTableModel(cmdCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable commandsTable = new JTable(commandsTableModel);
        commandsTable.setFont(new Font("SansSerif", Font.PLAIN, 11));
        commandsTable.setRowHeight(20);
        commandsTable.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        tabbedPane.addTab("🕹️ Commandes Auditées (TCP)", new JScrollPane(commandsTable));

        // Onglet 3 : Chronologie par Machine
        JPanel machineHistoryPanel = new JPanel(new BorderLayout(4, 4));
        machineHistoryPanel.setOpaque(false);

        JPanel selectorPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        selectorPanel.setOpaque(false);
        selectorPanel.add(new JLabel("Machine ciblée :"));
        machineSelector = new JComboBox<>(new String[]{"M01", "M02", "M03"});
        machineSelector.addActionListener(e -> refreshHistoryAsync());
        selectorPanel.add(machineSelector);

        machineHistoryPanel.add(selectorPanel, BorderLayout.NORTH);

        String[] histCols = {"Chronologie & Événements Reconstitués"};
        machineHistoryTableModel = new DefaultTableModel(histCols, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable historyTable = new JTable(machineHistoryTableModel);
        historyTable.setFont(new Font("SansSerif", Font.PLAIN, 11));
        historyTable.setRowHeight(20);
        machineHistoryPanel.add(new JScrollPane(historyTable), BorderLayout.CENTER);

        tabbedPane.addTab("🏭 Chronologie Machine", machineHistoryPanel);

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
                    updateTables(stats, alarms, commands, machineHistory);
                    btnRefresh.setEnabled(true);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("● SQLite: Erreur lecture");
                    statusLabel.setForeground(new Color(239, 68, 68));
                    btnRefresh.setEnabled(true);
                });
            }
        });
    }

    private void updateTables(Map<String, Object> stats,
                              List<Map<String, Object>> alarms,
                              List<Map<String, Object>> commands,
                              List<String> machineHistory) {
        // Mise à jour résumé
        if (stats != null) {
            statsSummaryLabel.setText(String.format("Télémétries: %s | Alarmes: %s | Commandes: %s",
                    stats.get("totalTelemetry"), stats.get("totalAlarms"), stats.get("totalCommands")));
        }

        // 1. Table Alarmes
        alarmsTableModel.setRowCount(0);
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

        // 2. Table Commandes
        commandsTableModel.setRowCount(0);
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

        // 3. Chronologie Machine
        machineHistoryTableModel.setRowCount(0);
        for (String line : machineHistory) {
            machineHistoryTableModel.addRow(new Object[]{line});
        }
    }

    public void shutdown() {
        queryExecutor.shutdownNow();
    }
}
