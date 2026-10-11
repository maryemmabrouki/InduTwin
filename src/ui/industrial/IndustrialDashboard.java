package ui.industrial;

import control.ControlServer;
import control.IndustrialCommandHandler;
import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.industrial.AlarmLevel;
import model.industrial.IndustrialEvent;
import model.industrial.MachineState;
import monitoring.NetworkStatistics;
import persistence.PersistenceService;
import rmi.RmiServer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;

/**
 * Tableau de bord principal de supervision industrielle (SCADA Swing).
 * Intègre la surveillance visuelle des machines, des capteurs, le journal d'alarmes,
 * le panneau de commande TCP fiable, le monitoring réseau UDP et l'audit persistant SQLite.
 */
public class IndustrialDashboard extends JFrame {

    private static final long serialVersionUID = 1L;

    private final IndustrialSupervisor supervisor;
    private final MachineSimulator simulator;
    private PersistenceService persistenceService;
    private ControlServer controlServer;
    private RmiServer rmiServer;

    private final JLabel headerTitleLabel;
    private final JLabel summaryOnlineLabel;
    private final JLabel summaryAlarmsLabel;
    private final JLabel summaryNetworkLabel;

    private final MachinePanel machinePanel;
    private final MachineControlPanel machineControlPanel;
    private final SensorPanel sensorPanel;
    private final AlarmPanel alarmPanel;
    private final NetworkHealthPanel networkHealthPanel;
    private final DatabaseHistoryPanel databaseHistoryPanel;
    private final FaultInjectionPanel faultInjectionPanel;

    private final Timer refreshTimer;

    public IndustrialDashboard() throws IOException {
        this(new IndustrialSupervisor(), new MachineSimulator());
    }

    public IndustrialDashboard(IndustrialSupervisor supervisor, MachineSimulator simulator) {
        super("SUPERVISION INDUSTRIELLE DISTRIBUÉE (UDP MULTICAST + TCP CONTRÔLE + RMI)");

        this.supervisor = supervisor;
        this.simulator = simulator;

        // Configuration générale de la fenêtre
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1240, 880);
        setMinimumSize(new Dimension(1020, 720));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());
        getContentPane().setBackground(new Color(241, 245, 249)); // Slate 100

        // 1. En-tête de supervision
        JPanel headerPanel = new JPanel(new BorderLayout(10, 10));
        headerPanel.setBackground(new Color(15, 23, 42)); // Slate 900
        headerPanel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        headerTitleLabel = new JLabel("PLATEFORME DE SUPERVISION INDUSTRIELLE 4.0");
        headerTitleLabel.setFont(new Font("SansSerif", Font.BOLD, 17));
        headerTitleLabel.setForeground(Color.WHITE);

        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        summaryPanel.setOpaque(false);

        summaryOnlineLabel = createSummaryBadge("● En ligne : 3/3", new Color(34, 197, 94));
        summaryAlarmsLabel = createSummaryBadge("⚠️ Alarmes : 0", new Color(245, 158, 11));
        summaryNetworkLabel = createSummaryBadge("🌐 Réseau : HEALTHY", new Color(59, 130, 246));

        summaryPanel.add(summaryOnlineLabel);
        summaryPanel.add(summaryAlarmsLabel);
        summaryPanel.add(summaryNetworkLabel);

        headerPanel.add(headerTitleLabel, BorderLayout.WEST);
        headerPanel.add(summaryPanel, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // 2. Composants centraux et latéraux
        sensorPanel = new SensorPanel();
        machineControlPanel = new MachineControlPanel();

        machinePanel = new MachinePanel(id -> {
            sensorPanel.setMachine(id);
            MachineState st = MachineState.STOPPED;
            if (this.supervisor != null && this.supervisor.getMachineInfo(id) != null) {
                st = this.supervisor.getMachineInfo(id).getState();
            }
            machineControlPanel.setMachine(id, st);
        });

        faultInjectionPanel = new FaultInjectionPanel(simulator);

        // Panneau gauche combinant la liste des machines, le panneau de contrôle TCP et l'injection de pannes
        JPanel leftPanel = new JPanel();
        leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
        leftPanel.setOpaque(false);
        leftPanel.setPreferredSize(new Dimension(340, 0));

        leftPanel.add(machinePanel);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        leftPanel.add(machineControlPanel);
        leftPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        leftPanel.add(faultInjectionPanel);

        // Split supérieur (Machines/Contrôle + Capteurs)
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, sensorPanel);
        topSplit.setDividerLocation(345);
        topSplit.setContinuousLayout(true);
        topSplit.setOpaque(false);
        topSplit.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));

        // 3. Panneaux inférieurs (Surveillance Temps Réel & Audit Persistant SQLite)
        alarmPanel = new AlarmPanel(supervisor::clearEventHistory);
        networkHealthPanel = new NetworkHealthPanel();

        JSplitPane bottomLiveSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, alarmPanel, networkHealthPanel);
        bottomLiveSplit.setDividerLocation(680);
        bottomLiveSplit.setContinuousLayout(true);
        bottomLiveSplit.setOpaque(false);
        bottomLiveSplit.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        if (this.supervisor.getPersistenceService() == null) {
            this.persistenceService = new PersistenceService();
            this.supervisor.setPersistenceService(this.persistenceService);
        } else {
            this.persistenceService = this.supervisor.getPersistenceService();
        }

        databaseHistoryPanel = new DatabaseHistoryPanel(this.persistenceService);

        JTabbedPane bottomTabs = new JTabbedPane();
        bottomTabs.setFont(new Font("SansSerif", Font.BOLD, 12));
        bottomTabs.addTab("🚨 Surveillance Temps Réel & Réseau", bottomLiveSplit);
        bottomTabs.addTab("💾 Historique & Audit Persistant (SQLite)", databaseHistoryPanel);
        bottomTabs.setPreferredSize(new Dimension(0, 270));

        // Assemblage vertical
        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, bottomTabs);
        mainSplit.setDividerLocation(470);
        mainSplit.setContinuousLayout(true);
        mainSplit.setOpaque(false);
        mainSplit.setBorder(null);

        add(mainSplit, BorderLayout.CENTER);

        // Écouteur pour insertion immédiate des alarmes dans l'EDT Swing
        supervisor.addSupervisorListener(new IndustrialSupervisor.SupervisorListener() {
            @Override
            public void onTelemetryUpdated(String machineId, model.TelemetryMessage msg) {
                // Télémétrie rafraîchie par le timer périodique pour éviter le scintillement
            }

            @Override
            public void onMachineStateChanged(String machineId, MachineState state) {
                SwingUtilities.invokeLater(() -> machinePanel.updateMachines(supervisor));
            }

            @Override
            public void onEventLogged(IndustrialEvent event) {
                SwingUtilities.invokeLater(() -> alarmPanel.addEvent(event));
            }
        });

        // Timer de rafraîchissement fluide (cadencé à 500 ms sur l'EDT)
        refreshTimer = new Timer(500, e -> refreshUI());

        // Démarrage réseau et persistance
        initServices();

        // Nettoyage complet à la fermeture de la fenêtre
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                dispose();
            }
        });
    }

    @Override
    public void dispose() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            refreshTimer.stop();
        }
        if (controlServer != null && controlServer.isRunning()) {
            controlServer.stop();
        }
        if (rmiServer != null && rmiServer.isRunning()) {
            rmiServer.stop();
        }
        if (databaseHistoryPanel != null) {
            databaseHistoryPanel.shutdown();
        }
        if (machineControlPanel != null) {
            machineControlPanel.shutdown();
        }
        if (persistenceService != null && persistenceService.isRunning()) {
            persistenceService.stop();
        }
        if (simulator != null && simulator.isRunning()) {
            simulator.stop();
        }
        if (supervisor != null && supervisor.isRunning()) {
            supervisor.stop();
        }
        super.dispose();
    }

    private JLabel createSummaryBadge(String text, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 12));
        label.setForeground(color);
        label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        return label;
    }

    private void initServices() {
        try {
            // Démarrage du service de persistance SQLite
            if (persistenceService != null && !persistenceService.isRunning()) {
                persistenceService.start();
                if (databaseHistoryPanel != null) {
                    databaseHistoryPanel.refreshHistoryAsync();
                }
            }

            if (supervisor != null && !supervisor.isRunning()) {
                supervisor.start();
            }
            if (simulator != null && !simulator.isRunning()) {
                simulator.start();
            }

            // Démarrage du serveur TCP de contrôle (avec audit persistant SQLite)
            if (controlServer == null && simulator != null) {
                IndustrialCommandHandler handler = new IndustrialCommandHandler(simulator, persistenceService);
                controlServer = new ControlServer(handler);
                controlServer.start();
            }

            // Démarrage du serveur de supervision distante RMI
            if (rmiServer == null && supervisor != null) {
                rmiServer = new RmiServer(supervisor);
                try {
                    rmiServer.start();
                } catch (Exception e) {
                    System.err.println("[Dashboard] Avertissement démarrage RMI: " + e.getMessage());
                }
            }

            refreshTimer.start();
        } catch (IOException e) {
            System.err.println("[Dashboard] Erreur initialisation services: " + e.getMessage());
        }
    }

    /**
     * Rafraîchissement périodique des composants sans recréation d'objets graphiques (anti-freeze).
     */
    private void refreshUI() {
        machinePanel.updateMachines(supervisor);
        machineControlPanel.updateState(supervisor);
        sensorPanel.updateData(supervisor);

        NetworkStatistics stats = supervisor.getNetworkMonitor().getSnapshot();
        networkHealthPanel.updateStats(stats);

        // Mise à jour des compteurs du bandeau supérieur
        int onlineCount = 0;
        int alarmCount = 0;

        for (IndustrialSupervisor.MachineSupervisionInfo info : supervisor.getMachines().values()) {
            if (info.getState() != MachineState.OFFLINE && info.getState() != MachineState.STOPPED) {
                onlineCount++;
            }
            alarmCount += info.getTotalAlarms();
        }

        int totalMachines = supervisor.getMachines().size();
        summaryOnlineLabel.setText(String.format("● En ligne : %d/%d", onlineCount, totalMachines));
        if (onlineCount < totalMachines) {
            summaryOnlineLabel.setForeground(new Color(239, 68, 68));
        } else {
            summaryOnlineLabel.setForeground(new Color(34, 197, 94));
        }

        summaryAlarmsLabel.setText("⚠️ Alarmes : " + alarmCount);
        if (alarmCount > 0) {
            summaryAlarmsLabel.setForeground(new Color(245, 158, 11));
        } else {
            summaryAlarmsLabel.setForeground(new Color(148, 163, 184));
        }

        String health = stats.getHealthStatus();
        summaryNetworkLabel.setText("🌐 Réseau : " + health);
        if ("HEALTHY".equalsIgnoreCase(health)) {
            summaryNetworkLabel.setForeground(new Color(34, 197, 94));
        } else if ("WARNING".equalsIgnoreCase(health)) {
            summaryNetworkLabel.setForeground(new Color(245, 158, 11));
        } else {
            summaryNetworkLabel.setForeground(new Color(239, 68, 68));
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                IndustrialDashboard dashboard = new IndustrialDashboard();
                dashboard.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
