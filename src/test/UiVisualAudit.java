package test;

import industrial.IndustrialSupervisor;
import industrial.MachineSimulator;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.IndustrialEvent;
import monitoring.NetworkStatistics;
import persistence.PersistenceService;
import ui.industrial.AlarmPanel;
import ui.industrial.DatabaseHistoryPanel;
import ui.industrial.IndustrialDashboard;
import ui.industrial.NetworkHealthPanel;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Utilitaire d'audit visuel automatisé pour inspecter le rendu pixel-perfect
 * des composants Swing Dark Slate d'InduTwin.
 */
public class UiVisualAudit {

    private static final String ARTIFACT_DIR = "C:/Users/pc/.gemini/antigravity-ide/brain/a3da1f8f-4d87-4fdc-8472-c45321a24f53/";

    public static void main(String[] args) throws Exception {
        System.out.println("[UiVisualAudit] Démarrage de l'audit visuel des composants de l'Étape B...");

        // 1. Audit visuel de AlarmPanel
        auditAlarmPanel();

        // 2. Audit visuel de NetworkHealthPanel
        auditNetworkHealthPanel();

        // 3. Audit visuel de DatabaseHistoryPanel
        auditDatabaseHistoryPanel();

        // 4. Audit visuel du Dashboard complet (Tabs 0 et 1)
        auditIndustrialDashboard();

        System.out.println("[UiVisualAudit] Tous les rendus ont été générés avec succès dans : " + ARTIFACT_DIR);
    }

    private static void renderComponent(JComponent component, int width, int height, String fileName) throws Exception {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setContentPane(component);
        frame.setSize(width, height);
        component.setSize(width, height);
        component.setPreferredSize(new Dimension(width, height));
        frame.addNotify();
        frame.validate();
        layoutRecursive(frame);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Appel direct à paint(g2d) qui peint le composant, sa bordure et tous ses enfants récursivement
        component.paint(g2d);
        g2d.dispose();

        File outputFile = new File(ARTIFACT_DIR, fileName);
        ImageIO.write(image, "PNG", outputFile);
        frame.dispose();
        System.out.println("  -> Image enregistrée : " + outputFile.getAbsolutePath() + " (" + width + "x" + height + ")");
    }

    private static void layoutRecursive(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layoutRecursive((Container) child);
            }
        }
    }

    private static void auditAlarmPanel() throws Exception {
        System.out.println("[UiVisualAudit] 1. Rendu de AlarmPanel...");
        AlarmPanel panel = new AlarmPanel(null);

        // Insertion d'événements de test variés (CRITICAL, WARNING, INFO)
        long now = System.currentTimeMillis();
        panel.addEvent(new IndustrialEvent(EventType.THRESHOLD_EXCEEDED, "M01", AlarmLevel.WARNING, "Température huile élevée (82.5°C)", now - 12000));
        panel.addEvent(new IndustrialEvent(EventType.MACHINE_FAILURE, "M01", AlarmLevel.CRITICAL, "Vibrations anormales pompe (9.8 mm/s)", now - 8000));
        panel.addEvent(new IndustrialEvent(EventType.MACHINE_OFFLINE, "M03", AlarmLevel.CRITICAL, "Perte Heartbeat (OFFLINE)", now - 4000));
        panel.addEvent(new IndustrialEvent(EventType.MACHINE_STARTED, "M02", AlarmLevel.INFO, "Four industriel démarré", now - 2000));

        renderComponent(panel, 750, 260, "audit_alarm_panel.png");
    }

    private static void auditNetworkHealthPanel() throws Exception {
        System.out.println("[UiVisualAudit] 2. Rendu de NetworkHealthPanel...");
        NetworkHealthPanel panel = new NetworkHealthPanel();

        // 2.a État initial / en attente
        renderComponent(panel, 600, 260, "audit_network_health_initial.png");

        // 2.b État avec métriques réelles actives
        Map<String, Long> types = new HashMap<>();
        types.put("TELEMETRY", 128L);
        types.put("HEARTBEAT", 64L);
        types.put("ALARM", 3L);
        types.put("SHAPE", 0L);

        Map<String, Long> machines = new HashMap<>();
        machines.put("M01", 64L);
        machines.put("M02", 64L);
        machines.put("M03", 64L);

        NetworkStatistics stats = new NetworkStatistics(
                4.2, 0.85, 195, 39420, 0, 0,
                1.4, 0.8, 3.2, types, machines, "HEALTHY", System.currentTimeMillis()
        );
        panel.updateStats(stats);
        renderComponent(panel, 600, 260, "audit_network_health_active.png");
    }

    private static void auditDatabaseHistoryPanel() throws Exception {
        System.out.println("[UiVisualAudit] 3. Rendu de DatabaseHistoryPanel...");
        PersistenceService ps = new PersistenceService();
        ps.start();
        DatabaseHistoryPanel panel = new DatabaseHistoryPanel(ps);

        // Attente chargement asynchrone SQLite et exécution sur l'EDT
        Thread.sleep(800);
        javax.swing.SwingUtilities.invokeAndWait(() -> {});

        renderComponent(panel, 1200, 280, "audit_database_history.png");
        ps.stop();
    }

    private static void auditIndustrialDashboard() throws Exception {
        System.out.println("[UiVisualAudit] 4. Rendu de IndustrialDashboard complet...");
        IndustrialSupervisor supervisor = new IndustrialSupervisor();
        MachineSimulator simulator = new MachineSimulator();

        IndustrialDashboard dashboard = new IndustrialDashboard(supervisor, simulator);
        dashboard.setSize(1240, 780);
        dashboard.addNotify();
        dashboard.validate();
        layoutRecursive(dashboard);

        // Chercher le JTabbedPane du bas
        JTabbedPane bottomTabs = findTabbedPane(dashboard);

        // Rendu Tab 0 (Surveillance Temps Réel & Réseau)
        if (bottomTabs != null) {
            bottomTabs.setSelectedIndex(0);
            dashboard.validate();
            layoutRecursive(dashboard);
        }
        renderFrameContent(dashboard, 1240, 780, "audit_dashboard_tab0.png");

        // Rendu Tab 1 (Historique & Audit Persistant)
        if (bottomTabs != null && bottomTabs.getTabCount() > 1) {
            bottomTabs.setSelectedIndex(1);
            dashboard.validate();
            layoutRecursive(dashboard);
        }
        renderFrameContent(dashboard, 1240, 780, "audit_dashboard_tab1.png");

        dashboard.dispose();
        supervisor.stop();
        simulator.stop();
    }

    private static JTabbedPane findTabbedPane(Container container) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTabbedPane) {
                return (JTabbedPane) c;
            }
            if (c instanceof Container) {
                JTabbedPane found = findTabbedPane((Container) c);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void renderFrameContent(JFrame frame, int width, int height, String fileName) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        frame.getContentPane().paint(g2d);
        g2d.dispose();

        File outputFile = new File(ARTIFACT_DIR, fileName);
        ImageIO.write(image, "PNG", outputFile);
        System.out.println("  -> Image enregistrée : " + outputFile.getAbsolutePath() + " (" + width + "x" + height + ")");
    }
}
