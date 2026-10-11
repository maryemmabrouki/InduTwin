package industrial;

import config.NetworkConfig;
import model.industrial.MachineState;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Moniteur de présence industrielle chargé de suivre les battements de cœur (Heartbeats)
 * et de détecter les machines hors ligne (timeout) ou rétablies.
 */
public class HeartbeatMonitor {

    public interface HeartbeatListener {
        void onMachineOffline(String machineId, long elapsedSeconds);
        void onMachineRecovered(String machineId, MachineState state);
        void onHeartbeatTick(String machineId, MachineState state, long sequenceNumber);
    }

    private final long timeoutMs;
    private final Map<String, Long> lastHeartbeatMap = new ConcurrentHashMap<>();
    private final Map<String, MachineState> lastStateMap = new ConcurrentHashMap<>();
    private final Map<String, Boolean> offlineMap = new ConcurrentHashMap<>();

    private final List<HeartbeatListener> listeners = new CopyOnWriteArrayList<>();

    public HeartbeatMonitor() {
        this(NetworkConfig.HEARTBEAT_TIMEOUT_MS);
    }

    public HeartbeatMonitor(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void addListener(HeartbeatListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(HeartbeatListener listener) {
        listeners.remove(listener);
    }

    /**
     * Traite un battement de cœur reçu pour une machine donnée.
     */
    public void recordHeartbeat(String machineId, MachineState state, long sequenceNumber) {
        if (machineId == null) return;

        long now = System.currentTimeMillis();
        lastHeartbeatMap.put(machineId, now);
        lastStateMap.put(machineId, state != null ? state : MachineState.RUNNING);

        Boolean wasOffline = offlineMap.put(machineId, false);
        if (Boolean.TRUE.equals(wasOffline)) {
            notifyMachineRecovered(machineId, state);
        }

        notifyHeartbeatTick(machineId, state, sequenceNumber);
    }

    /**
     * Vérifie les délais d'inactivité pour toutes les machines connues et signale les timeouts.
     */
    public void checkTimeouts() {
        long now = System.currentTimeMillis();

        for (Map.Entry<String, Long> entry : lastHeartbeatMap.entrySet()) {
            String machineId = entry.getKey();
            long lastSeen = entry.getValue();
            long elapsed = now - lastSeen;

            boolean currentlyOffline = Boolean.TRUE.equals(offlineMap.get(machineId));

            if (elapsed > timeoutMs && !currentlyOffline) {
                offlineMap.put(machineId, true);
                long elapsedSeconds = elapsed / 1000;
                notifyMachineOffline(machineId, elapsedSeconds);
            }
        }
    }

    public boolean isMachineOnline(String machineId) {
        if (!lastHeartbeatMap.containsKey(machineId)) {
            return false;
        }
        return !Boolean.TRUE.equals(offlineMap.get(machineId));
    }

    public Long getLastHeartbeatTime(String machineId) {
        return lastHeartbeatMap.get(machineId);
    }

    public Map<String, Long> getAllLastHeartbeats() {
        return Collections.unmodifiableMap(lastHeartbeatMap);
    }

    private void notifyMachineOffline(String machineId, long elapsedSeconds) {
        for (HeartbeatListener listener : listeners) {
            try {
                listener.onMachineOffline(machineId, elapsedSeconds);
            } catch (Exception e) {
                System.err.println("[HeartbeatMonitor] Erreur listener onMachineOffline: " + e.getMessage());
            }
        }
    }

    private void notifyMachineRecovered(String machineId, MachineState state) {
        for (HeartbeatListener listener : listeners) {
            try {
                listener.onMachineRecovered(machineId, state);
            } catch (Exception e) {
                System.err.println("[HeartbeatMonitor] Erreur listener onMachineRecovered: " + e.getMessage());
            }
        }
    }

    private void notifyHeartbeatTick(String machineId, MachineState state, long sequenceNumber) {
        for (HeartbeatListener listener : listeners) {
            try {
                listener.onHeartbeatTick(machineId, state, sequenceNumber);
            } catch (Exception e) {
                System.err.println("[HeartbeatMonitor] Erreur listener onHeartbeatTick: " + e.getMessage());
            }
        }
    }
}
