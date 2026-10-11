package monitoring;

import model.AlarmMessage;
import model.HeartbeatMessage;
import model.NetworkMessage;
import model.TelemetryMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Moteur de calcul et de suivi des métriques réseau applicatives en temps réel :
 * débit, cadence de paquets, détection de pertes potentielles, latence estimée et santé réseau.
 */
public class NetworkMonitor {

    private final SequenceTracker sequenceTracker = new SequenceTracker();

    private final AtomicLong totalPackets = new AtomicLong(0);
    private final AtomicLong totalBytes = new AtomicLong(0);

    // Compteurs de la fenêtre courante pour calcul de taux (par seconde)
    private long lastRateCalculationTime = System.currentTimeMillis();
    private final AtomicLong windowPackets = new AtomicLong(0);
    private final AtomicLong windowBytes = new AtomicLong(0);

    private volatile double currentPacketsPerSecond = 0.0;
    private volatile double currentThroughputKbPerSec = 0.0;

    private final Map<String, AtomicLong> packetsByType = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> packetsByMachine = new ConcurrentHashMap<>();

    // Statistiques de latence applicative approximative (en ms)
    private double sumLatency = 0.0;
    private long latencySampleCount = 0;
    private double minLatency = Double.MAX_VALUE;
    private double maxLatency = 0.0;

    private volatile long lastPacketReceivedTime = 0;

    public NetworkMonitor() {
        // Initialise les compteurs de types courants
        packetsByType.put("TELEMETRY", new AtomicLong(0));
        packetsByType.put("HEARTBEAT", new AtomicLong(0));
        packetsByType.put("ALARM", new AtomicLong(0));
        packetsByType.put("SHAPE", new AtomicLong(0));
    }

    /**
     * Enregistre un message reçu avec sa taille brute en octets.
     */
    public synchronized void recordMessage(NetworkMessage message, int byteSize) {
        if (message == null) return;

        long now = System.currentTimeMillis();
        lastPacketReceivedTime = now;

        totalPackets.incrementAndGet();
        totalBytes.addAndGet(byteSize);
        windowPackets.incrementAndGet();
        windowBytes.addAndGet(byteSize);

        // Mise à jour de la répartition par type
        String type = message.getMessageType();
        packetsByType.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();

        // Extraction et suivi des numéros de séquence et machines
        String machineId = null;
        long seq = -1;

        if (message instanceof TelemetryMessage) {
            TelemetryMessage tm = (TelemetryMessage) message;
            machineId = tm.getMachineId();
            seq = tm.getSequenceNumber();
        } else if (message instanceof HeartbeatMessage) {
            HeartbeatMessage hbm = (HeartbeatMessage) message;
            machineId = hbm.getMachineId();
            seq = hbm.getSequenceNumber();
        } else if (message instanceof AlarmMessage) {
            AlarmMessage am = (AlarmMessage) message;
            machineId = am.getMachineId();
            seq = am.getSequenceNumber();
        }

        if (machineId != null) {
            packetsByMachine.computeIfAbsent(machineId, k -> new AtomicLong(0)).incrementAndGet();
            if (seq > 0) {
                sequenceTracker.track(machineId, type, seq);
            }
        }

        // Calcul de latence applicative approximative
        long sentTimestamp = message.getTimestamp();
        if (sentTimestamp > 0) {
            long latency = now - sentTimestamp;
            // On conserve uniquement les valeurs plausibles (0 à 5000 ms)
            if (latency >= 0 && latency <= 5000) {
                sumLatency += latency;
                latencySampleCount++;
                if (latency < minLatency) minLatency = latency;
                if (latency > maxLatency) maxLatency = latency;
            }
        }

        // Mise à jour périodique des taux instantanés si au moins 1 seconde s'est écoulée
        updateRatesIfNeeded(now);
    }

    public void recordMessage(NetworkMessage message) {
        int estimatedSize = message != null ? message.serialize().length() : 0;
        recordMessage(message, estimatedSize);
    }

    /**
     * Calcule périodiquement les débits par seconde (packets/s et Ko/s).
     */
    public synchronized void updateRatesIfNeeded(long now) {
        long elapsed = now - lastRateCalculationTime;
        if (elapsed >= 1000) {
            double seconds = elapsed / 1000.0;
            currentPacketsPerSecond = windowPackets.getAndSet(0) / seconds;
            // Ko/s = octets / 1024 / secondes
            currentThroughputKbPerSec = (windowBytes.getAndSet(0) / 1024.0) / seconds;
            lastRateCalculationTime = now;
        }
    }

    /**
     * Génère un instantané immuable des métriques pour affichage dans le Dashboard.
     */
    public synchronized NetworkStatistics getSnapshot() {
        updateRatesIfNeeded(System.currentTimeMillis());

        double avgLatency = latencySampleCount > 0 ? (sumLatency / latencySampleCount) : 0.0;
        double currentMin = minLatency == Double.MAX_VALUE ? 0.0 : minLatency;

        long gaps = sequenceTracker.getTotalSequenceGaps();
        long loss = sequenceTracker.getPotentialPacketLoss();

        // Évaluation de la santé réseau
        String health;
        long timeSinceLastPacket = System.currentTimeMillis() - lastPacketReceivedTime;

        if (timeSinceLastPacket > 6000 && totalPackets.get() > 0) {
            health = "DEGRADED"; // Plus aucun paquet reçu depuis 6 secondes
        } else if (loss > 5 || avgLatency > 150.0) {
            health = "DEGRADED";
        } else if (loss > 0 || avgLatency >= 50.0) {
            health = "WARNING";
        } else {
            health = "HEALTHY";
        }

        Map<String, Long> typesCopy = new HashMap<>();
        for (Map.Entry<String, AtomicLong> e : packetsByType.entrySet()) {
            typesCopy.put(e.getKey(), e.getValue().get());
        }

        Map<String, Long> machinesCopy = new HashMap<>();
        for (Map.Entry<String, AtomicLong> e : packetsByMachine.entrySet()) {
            machinesCopy.put(e.getKey(), e.getValue().get());
        }

        return new NetworkStatistics(
                Math.round(currentPacketsPerSecond * 10.0) / 10.0,
                Math.round(currentThroughputKbPerSec * 100.0) / 100.0,
                totalPackets.get(),
                totalBytes.get(),
                gaps,
                loss,
                Math.round(avgLatency * 10.0) / 10.0,
                currentMin,
                maxLatency,
                typesCopy,
                machinesCopy,
                health,
                System.currentTimeMillis()
        );
    }

    public SequenceTracker getSequenceTracker() {
        return sequenceTracker;
    }

    public synchronized void reset() {
        totalPackets.set(0);
        totalBytes.set(0);
        windowPackets.set(0);
        windowBytes.set(0);
        currentPacketsPerSecond = 0;
        currentThroughputKbPerSec = 0;
        sumLatency = 0;
        latencySampleCount = 0;
        minLatency = Double.MAX_VALUE;
        maxLatency = 0;
        packetsByType.values().forEach(v -> v.set(0));
        packetsByMachine.clear();
        sequenceTracker.reset();
    }
}
