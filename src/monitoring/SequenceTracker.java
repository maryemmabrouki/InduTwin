package monitoring;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Suivi des numéros de séquence pour détecter les sauts (sequence gaps)
 * et estimer les pertes potentielles de paquets UDP Multicast.
 */
public class SequenceTracker {

    private final Map<String, Long> lastSequenceMap = new ConcurrentHashMap<>();
    private final AtomicLong totalSequenceGaps = new AtomicLong(0);
    private final AtomicLong potentialPacketLoss = new AtomicLong(0);

    /**
     * Traite un numéro de séquence reçu pour une machine et un type de message donnés.
     *
     * @param machineId      L'identifiant de la machine (ex: M01)
     * @param messageType    Le type de message (ex: TELEMETRY, HEARTBEAT)
     * @param sequenceNumber Le numéro de séquence transporté par la trame
     * @return Le nombre de paquets manquants détectés lors de ce saut (0 si nominal)
     */
    public long track(String machineId, String messageType, long sequenceNumber) {
        if (machineId == null || messageType == null) {
            return 0;
        }

        String key = machineId + ":" + messageType;
        Long previous = lastSequenceMap.put(key, sequenceNumber);

        if (previous != null) {
            long expected = previous + 1;
            if (sequenceNumber > expected) {
                long gap = sequenceNumber - expected;
                totalSequenceGaps.incrementAndGet();
                potentialPacketLoss.addAndGet(gap);
                return gap;
            }
        }
        return 0;
    }

    public long getTotalSequenceGaps() {
        return totalSequenceGaps.get();
    }

    public long getPotentialPacketLoss() {
        return potentialPacketLoss.get();
    }

    public Map<String, Long> getLastSequences() {
        return Collections.unmodifiableMap(lastSequenceMap);
    }

    public void reset() {
        lastSequenceMap.clear();
        totalSequenceGaps.set(0);
        potentialPacketLoss.set(0);
    }
}
