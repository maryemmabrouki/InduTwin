package industrial;

import control.ControlServer;
import control.IndustrialCommandHandler;
import persistence.PersistenceService;
import rmi.RmiServer;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serveur autonome orchestrant le cycle de vie de l'ensemble des services industriels d'InduTwin :
 * 1. Service de persistance SQLite asynchrone (mode WAL)
 * 2. Superviseur industriel UDP Multicast (224.1.1.1:5555)
 * 3. Simulateur d'atelier industriel (machines M01, M02, M03)
 * 4. Serveur de commande TCP point-à-point fiable (port 5556)
 * 5. Serveur de supervision distante Java RMI (port 1099)
 *
 * Découple intégralement l'infrastructure backend de l'interface graphique Swing.
 */
public class InduTwinServer {

    private final IndustrialSupervisor supervisor;
    private final MachineSimulator simulator;
    private final PersistenceService persistenceService;
    private final ControlServer controlServer;
    private final RmiServer rmiServer;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private boolean consoleLogging = true;

    /**
     * Initialise une instance du serveur avec la configuration nominale par défaut.
     */
    public InduTwinServer() {
        this(new IndustrialSupervisor(), new MachineSimulator(), new PersistenceService());
    }

    /**
     * Initialise le serveur avec un superviseur et un simulateur existants.
     */
    public InduTwinServer(IndustrialSupervisor supervisor, MachineSimulator simulator) {
        this(supervisor, simulator, supervisor != null && supervisor.getPersistenceService() != null
                ? supervisor.getPersistenceService()
                : new PersistenceService());
    }

    /**
     * Constructeur complet injectant les composants d'infrastructure.
     */
    public InduTwinServer(IndustrialSupervisor supervisor, MachineSimulator simulator, PersistenceService persistenceService) {
        this.supervisor = supervisor;
        this.simulator = simulator;
        this.persistenceService = persistenceService;

        // Liaison bidirectionnelle de la persistance au superviseur
        if (this.supervisor != null && this.supervisor.getPersistenceService() == null) {
            this.supervisor.setPersistenceService(this.persistenceService);
        }

        // Configuration du serveur TCP avec validation de machine d'état et audit persistant
        IndustrialCommandHandler commandHandler = new IndustrialCommandHandler(this.simulator, this.persistenceService);
        this.controlServer = new ControlServer(commandHandler);

        // Configuration du serveur RMI distant (port 1099)
        this.rmiServer = new RmiServer(this.supervisor);
    }

    /**
     * Démarre l'ensemble des services industriels dans un ordre strict et cohérent.
     * Ordre : 1. Persistance SQLite -> 2. Superviseur UDP -> 3. Simulateur -> 4. Contrôle TCP -> 5. RMI
     * En cas d'échec d'un service, effectue un rollback complet des services déjà démarrés sans masquer l'erreur initiale.
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        if (consoleLogging) {
            System.out.println("==================================================================");
            System.out.println("  DÉMARRAGE DU SERVEUR INDUSTRIEL INDUTWIN (CORE SERVICES)        ");
            System.out.println("==================================================================");
        }

        try {
            // 1. Démarrage de la persistance SQLite
            if (persistenceService != null && !persistenceService.isRunning()) {
                persistenceService.start();
            }

            // 2. Démarrage du superviseur UDP Multicast
            if (supervisor != null && !supervisor.isRunning()) {
                supervisor.start();
            }

            // 3. Démarrage du simulateur d'atelier
            if (simulator != null && !simulator.isRunning()) {
                simulator.start();
            }

            // 4. Démarrage du serveur TCP de contrôle
            if (controlServer != null && !controlServer.isRunning()) {
                controlServer.start();
            }

            // 5. Démarrage du serveur Java RMI
            if (rmiServer != null && !rmiServer.isRunning()) {
                try {
                    rmiServer.start();
                } catch (Exception e) {
                    if (consoleLogging) {
                        System.err.println("[InduTwinServer] Avertissement démarrage RMI: " + e.getMessage());
                    }
                }
            }

            running.set(true);

            if (consoleLogging) {
                System.out.println("[InduTwinServer] Tous les services industriels sont opérationnels.");
            }
        } catch (Throwable t) {
            if (consoleLogging) {
                System.err.println("[InduTwinServer] Échec au démarrage, rollback des services déjà démarrés...");
            }
            running.set(false);
            stopServicesInternal(t);
            if (t instanceof IOException) {
                throw (IOException) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else if (t instanceof Error) {
                throw (Error) t;
            } else {
                throw new IOException("Erreur au démarrage d'InduTwinServer: " + t.getMessage(), t);
            }
        }
    }

    /**
     * Arrêt propre de l'ensemble des services industriels dans l'ordre inverse du démarrage.
     * Ordre : 1. Contrôle TCP -> 2. RMI -> 3. Simulateur -> 4. Superviseur UDP -> 5. Persistance SQLite
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            // Sécurité : même si running était déjà false (ex. après un rollback d'échec partiel),
            // on s'assure qu'aucun service orphelin n'est actif si stop() est appelé
            stopServicesInternal(null);
            return;
        }

        if (consoleLogging) {
            System.out.println("[InduTwinServer] Arrêt des services industriels en cours...");
        }

        stopServicesInternal(null);

        if (consoleLogging) {
            System.out.println("[InduTwinServer] Tous les services industriels sont arrêtés proprement.");
        }
    }

    /**
     * Arrête tous les sous-services sans dépendre de l'indicateur running.
     * En cas d'erreur lors du nettoyage, attache l'exception comme suppressed à initialError.
     */
    private void stopServicesInternal(Throwable initialError) {
        stopServiceSafely("ControlServer", () -> {
            if (controlServer != null && controlServer.isRunning()) controlServer.stop();
        }, initialError);

        stopServiceSafely("RmiServer", () -> {
            if (rmiServer != null && rmiServer.isRunning()) rmiServer.stop();
        }, initialError);

        stopServiceSafely("MachineSimulator", () -> {
            if (simulator != null && simulator.isRunning()) simulator.stop();
        }, initialError);

        stopServiceSafely("IndustrialSupervisor", () -> {
            if (supervisor != null && supervisor.isRunning()) supervisor.stop();
        }, initialError);

        stopServiceSafely("PersistenceService", () -> {
            if (persistenceService != null && persistenceService.isRunning()) persistenceService.stop();
        }, initialError);
    }

    private void stopServiceSafely(String serviceName, Runnable stopAction, Throwable initialError) {
        if (stopAction == null) return;
        try {
            stopAction.run();
        } catch (Throwable t) {
            if (consoleLogging) {
                System.err.println("[InduTwinServer] Erreur lors de l'arrêt de " + serviceName + ": " + t.getMessage());
            }
            if (initialError != null) {
                initialError.addSuppressed(t);
            }
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public IndustrialSupervisor getSupervisor() {
        return supervisor;
    }

    public MachineSimulator getSimulator() {
        return simulator;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    public ControlServer getControlServer() {
        return controlServer;
    }

    public RmiServer getRmiServer() {
        return rmiServer;
    }

    public void setConsoleLogging(boolean consoleLogging) {
        this.consoleLogging = consoleLogging;
        if (supervisor != null) supervisor.setConsoleLogging(consoleLogging);
        if (controlServer != null) controlServer.setConsoleLogging(consoleLogging);
        if (rmiServer != null) rmiServer.setConsoleLogging(consoleLogging);
        if (persistenceService != null) persistenceService.setConsoleLogging(consoleLogging);
    }

    /**
     * Point d'entrée autonome (headless) permettant d'exécuter InduTwin en tant que service daemon backend.
     */
    public static void main(String[] args) {
        try {
            InduTwinServer server = new InduTwinServer();
            server.start();

            // Hook d'arrêt propre sur SIGINT / fermeture JVM (Ctrl+C)
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\n[InduTwinServer] Signal d'arrêt reçu...");
                server.stop();
            }));

            System.out.println("\n[InduTwinServer] Serveur industriel actif.");
            System.out.println("  • UDP Multicast : 224.1.1.1:5555");
            System.out.println("  • TCP Contrôle  : Port 5556");
            System.out.println("  • Java RMI      : Port 1099");
            System.out.println("  • SQLite WAL    : data/industrial_supervision.db");
            System.out.println("Appuyez sur [Entrée] ou Ctrl+C pour arrêter le serveur.");

            if (args.length > 0 && "--daemon".equalsIgnoreCase(args[0])) {
                Thread.currentThread().join();
            } else {
                try {
                    System.in.read();
                } catch (Exception ignored) {}
                server.stop();
            }
        } catch (Exception e) {
            System.err.println("[InduTwinServer] Erreur critique au démarrage: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
