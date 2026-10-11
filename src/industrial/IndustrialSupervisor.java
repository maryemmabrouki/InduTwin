package industrial;

import model.AlarmMessage;
import model.HeartbeatMessage;
import model.NetworkMessage;
import model.TelemetryMessage;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.IndustrialEvent;
import model.industrial.MachineState;
import model.industrial.SensorType;
import monitoring.NetworkMonitor;
import network.MessageListener;
import network.MulticastManager;
import persistence.PersistenceService;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service de supervision industrielle étendu pour la Phase 3.
 * Écoute le flux Multicast UDP, trace la télémétrie, maintient l'historique des alarmes (max 500),
 * alimente le NetworkMonitor et notifie les interfaces d'observation (UI / Dashboard).
 */
public class IndustrialSupervisor implements MessageListener, HeartbeatMonitor.HeartbeatListener {

    public interface SupervisorListener {
        void onTelemetryUpdated(String machineId, TelemetryMessage msg);
        void onMachineStateChanged(String machineId, MachineState state);
        void onEventLogged(IndustrialEvent event);
    }

    /**
     * DTO de supervision représentant l'état consolidé d'une machine vue par le superviseur.
     */
    public static class MachineSupervisionInfo {
        private final String id;
        private volatile MachineState state;
        private volatile long lastSeenTimestamp;
        private final Map<SensorType, Double> latestReadings = new ConcurrentHashMap<>();
        private int totalAlarms = 0;

        public MachineSupervisionInfo(String id, MachineState state) {
            this.id = id;
            this.state = state;
            this.lastSeenTimestamp = System.currentTimeMillis();
        }

        public String getId() {
            return id;
        }

        public MachineState getState() {
            return state;
        }

        public void setState(MachineState state) {
            this.state = state;
        }

        public long getLastSeenTimestamp() {
            return lastSeenTimestamp;
        }

        public void setLastSeenTimestamp(long lastSeenTimestamp) {
            this.lastSeenTimestamp = lastSeenTimestamp;
        }

        public Map<SensorType, Double> getLatestReadings() {
            return Collections.unmodifiableMap(latestReadings);
        }

        public void updateReading(SensorType type, double value) {
            latestReadings.put(type, value);
        }

        public int getTotalAlarms() {
            return totalAlarms;
        }

        public void incrementAlarms() {
            totalAlarms++;
        }
    }

    private final MulticastManager multicastManager;
    private final HeartbeatMonitor heartbeatMonitor;
    private final NetworkMonitor networkMonitor = new NetworkMonitor();

    private final Map<String, MachineSupervisionInfo> machines = new LinkedHashMap<>();
    private final List<IndustrialEvent> eventHistory = new CopyOnWriteArrayList<>();
    private static final int MAX_EVENT_HISTORY = 500;

    private final List<SupervisorListener> supervisorListeners = new CopyOnWriteArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService timeoutExecutor;
    private boolean consoleLogging = true;
    private PersistenceService persistenceService;

    public IndustrialSupervisor() {
        this(new MulticastManager(), new HeartbeatMonitor());
    }

    public IndustrialSupervisor(MulticastManager multicastManager, HeartbeatMonitor heartbeatMonitor) {
        this.multicastManager = multicastManager;
        this.heartbeatMonitor = heartbeatMonitor;

        this.multicastManager.addListener(this);
        this.heartbeatMonitor.addListener(this);

        // Enregistrement par défaut des machines attendues
        machines.put("M01", new MachineSupervisionInfo("M01", MachineState.STOPPED));
        machines.put("M02", new MachineSupervisionInfo("M02", MachineState.STOPPED));
        machines.put("M03", new MachineSupervisionInfo("M03", MachineState.STOPPED));
    }

    public void addSupervisorListener(SupervisorListener listener) {
        if (listener != null && !supervisorListeners.contains(listener)) {
            supervisorListeners.add(listener);
        }
    }

    public void removeSupervisorListener(SupervisorListener listener) {
        supervisorListeners.remove(listener);
    }

    public synchronized void start() throws IOException {
        if (running.get()) return;

        if (!multicastManager.isRunning()) {
            multicastManager.start();
        }

        running.set(true);

        timeoutExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Supervisor-Timeout-Checker");
            t.setDaemon(true);
            return t;
        });

        // Vérification des timeouts de heartbeat toutes les secondes
        timeoutExecutor.scheduleAtFixedRate(heartbeatMonitor::checkTimeouts, 1, 1, TimeUnit.SECONDS);

        if (consoleLogging) {
            System.out.printf("[SUPERVISOR] Superviseur industriel démarré sur %s:%d%n",
                    multicastManager.getGroupAddress(), multicastManager.getPort());
        }
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;

        if (timeoutExecutor != null) {
            timeoutExecutor.shutdownNow();
        }

        if (multicastManager != null && multicastManager.isRunning()) {
            multicastManager.stop();
        }

        if (persistenceService != null && persistenceService.isRunning()) {
            persistenceService.stop();
        }

        if (consoleLogging) {
            System.out.println("[SUPERVISOR] Superviseur industriel arrêté.");
        }
    }

    @Override
    public void onMessageReceived(NetworkMessage message, InetAddress senderAddress) {
        int estimatedSize = message != null ? message.serialize().length() : 0;
        onMessageReceived(message, senderAddress, estimatedSize);
    }

    @Override
    public void onMessageReceived(NetworkMessage message, InetAddress senderAddress, int byteLength) {
        // Enregistrement direct dans le moniteur de métriques réseau
        networkMonitor.recordMessage(message, byteLength);

        if (message instanceof TelemetryMessage) {
            handleTelemetry((TelemetryMessage) message, senderAddress);
        } else if (message instanceof HeartbeatMessage) {
            handleHeartbeat((HeartbeatMessage) message, senderAddress);
        } else if (message instanceof AlarmMessage) {
            handleAlarm((AlarmMessage) message, senderAddress);
        }
    }

    private void handleTelemetry(TelemetryMessage msg, InetAddress sender) {
        MachineSupervisionInfo info = machines.computeIfAbsent(msg.getMachineId(),
                id -> new MachineSupervisionInfo(id, MachineState.RUNNING));

        info.updateReading(msg.getSensorType(), msg.getValue());
        info.setLastSeenTimestamp(msg.getTimestamp());

        if (persistenceService != null) {
            persistenceService.saveTelemetry(msg);
        }

        for (SupervisorListener l : supervisorListeners) {
            try {
                l.onTelemetryUpdated(msg.getMachineId(), msg);
            } catch (Exception ignored) {}
        }

        if (consoleLogging) {
            System.out.printf("[TELEMETRY] [%s] %-12s = %6.1f %-4s (seq#%d)%n",
                    msg.getMachineId(), msg.getSensorType(), msg.getValue(), msg.getUnit(), msg.getSequenceNumber());
        }
    }

    private void handleHeartbeat(HeartbeatMessage msg, InetAddress sender) {
        MachineSupervisionInfo info = machines.computeIfAbsent(msg.getMachineId(),
                id -> new MachineSupervisionInfo(id, msg.getState()));

        MachineState oldState = info.getState();
        info.setState(msg.getState());
        info.setLastSeenTimestamp(msg.getTimestamp());

        if (persistenceService != null) {
            persistenceService.saveMachineState(msg.getMachineId(), null, msg.getState().name(), msg.getTimestamp());
        }

        heartbeatMonitor.recordHeartbeat(msg.getMachineId(), msg.getState(), msg.getSequenceNumber());

        if (oldState != msg.getState()) {
            for (SupervisorListener l : supervisorListeners) {
                try {
                    l.onMachineStateChanged(msg.getMachineId(), msg.getState());
                } catch (Exception ignored) {}
            }
        }
    }

    private void handleAlarm(AlarmMessage msg, InetAddress sender) {
        MachineSupervisionInfo info = machines.computeIfAbsent(msg.getMachineId(),
                id -> new MachineSupervisionInfo(id, MachineState.WARNING));

        info.incrementAlarms();
        MachineState oldState = info.getState();

        if (persistenceService != null) {
            persistenceService.saveAlarm(msg);
        }

        if (msg.getAlarmLevel() == AlarmLevel.CRITICAL) {
            info.setState(MachineState.CRITICAL);
        } else if (msg.getAlarmLevel() == AlarmLevel.WARNING && info.getState() != MachineState.CRITICAL) {
            info.setState(MachineState.WARNING);
        }

        // Journalisation de l'événement dans l'historique
        IndustrialEvent event = new IndustrialEvent(
                msg.getEventType(),
                msg.getMachineId(),
                msg.getAlarmLevel(),
                msg.getDescription(),
                msg.getTimestamp()
        );
        logEvent(event);

        if (oldState != info.getState()) {
            for (SupervisorListener l : supervisorListeners) {
                try {
                    l.onMachineStateChanged(msg.getMachineId(), info.getState());
                } catch (Exception ignored) {}
            }
        }

        if (consoleLogging) {
            System.err.printf("[ALARM-%s] [%s] %s : %s = %.1f (%s)%n",
                    msg.getAlarmLevel(), msg.getMachineId(), msg.getEventType(),
                    msg.getSensorType(), msg.getValue(), msg.getDescription());
        }
    }

    @Override
    public void onMachineOffline(String machineId, long elapsedSeconds) {
        MachineSupervisionInfo info = machines.get(machineId);
        if (info != null) {
            info.setState(MachineState.OFFLINE);
        }

        IndustrialEvent event = new IndustrialEvent(
                EventType.MACHINE_OFFLINE,
                machineId,
                AlarmLevel.CRITICAL,
                "Heartbeat absent depuis " + elapsedSeconds + " s (TIMEOUT)",
                System.currentTimeMillis()
        );
        logEvent(event);

        for (SupervisorListener l : supervisorListeners) {
            try {
                l.onMachineStateChanged(machineId, MachineState.OFFLINE);
            } catch (Exception ignored) {}
        }

        if (consoleLogging) {
            System.err.printf("🚨 [OFFLINE] [%s] Aucun Heartbeat depuis %d s ➔ MACHINE HORS LIGNE !%n",
                    machineId, elapsedSeconds);
        }
    }

    @Override
    public void onMachineRecovered(String machineId, MachineState state) {
        MachineSupervisionInfo info = machines.get(machineId);
        if (info != null) {
            info.setState(state != null ? state : MachineState.RUNNING);
        }

        IndustrialEvent event = new IndustrialEvent(
                EventType.MACHINE_RECOVERED,
                machineId,
                AlarmLevel.INFO,
                "Heartbeat rétabli - Machine en ligne",
                System.currentTimeMillis()
        );
        logEvent(event);

        for (SupervisorListener l : supervisorListeners) {
            try {
                l.onMachineStateChanged(machineId, info != null ? info.getState() : MachineState.RUNNING);
            } catch (Exception ignored) {}
        }

        if (consoleLogging) {
            System.out.printf("✅ [RECOVERED] [%s] Heartbeat rétabli ➔ MACHINE DE RETOUR EN LIGNE (État: %s)%n",
                    machineId, info != null ? info.getState() : "OK");
        }
    }

    @Override
    public void onHeartbeatTick(String machineId, MachineState state, long sequenceNumber) {
        // Optionnel : tick de battement
    }

    @Override
    public void onNetworkError(Exception e) {
        System.err.println("[SUPERVISOR] Erreur réseau capturée: " + e.getMessage());
    }

    private void logEvent(IndustrialEvent event) {
        if (event == null) return;

        // Limite à 500 événements en mémoire
        if (eventHistory.size() >= MAX_EVENT_HISTORY) {
            eventHistory.remove(0);
        }
        eventHistory.add(event);

        if (persistenceService != null) {
            persistenceService.saveEvent(event);
        }

        for (SupervisorListener l : supervisorListeners) {
            try {
                l.onEventLogged(event);
            } catch (Exception ignored) {}
        }
    }

    public List<IndustrialEvent> getEventHistory() {
        return Collections.unmodifiableList(new ArrayList<>(eventHistory));
    }

    public void clearEventHistory() {
        eventHistory.clear();
    }

    public boolean isRunning() {
        return running.get();
    }

    public Map<String, MachineSupervisionInfo> getMachines() {
        return Collections.unmodifiableMap(machines);
    }

    public MachineSupervisionInfo getMachineInfo(String id) {
        return machines.get(id);
    }

    public NetworkMonitor getNetworkMonitor() {
        return networkMonitor;
    }

    public HeartbeatMonitor getHeartbeatMonitor() {
        return heartbeatMonitor;
    }

    public void setConsoleLogging(boolean consoleLogging) {
        this.consoleLogging = consoleLogging;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }
}
