package monitoring;

import java.util.Collections;
import java.util.Map;

/**
 * Instantané immuable des métriques de supervision réseau (QoS, débit, pertes, latence).
 */
public class NetworkStatistics {

    private final double packetsPerSecond;
    private final double throughputKbPerSec;
    private final long totalPacketsReceived;
    private final long totalBytesReceived;
    private final long sequenceGapsCount;
    private final long potentialPacketLoss;
    private final double averageLatencyMs;
    private final double minLatencyMs;
    private final double maxLatencyMs;
    private final Map<String, Long> packetsByMessageType;
    private final Map<String, Long> packetsByMachine;
    private final String healthStatus;
    private final long timestamp;

    public NetworkStatistics(double packetsPerSecond, double throughputKbPerSec, long totalPacketsReceived,
                             long totalBytesReceived, long sequenceGapsCount, long potentialPacketLoss,
                             double averageLatencyMs, double minLatencyMs, double maxLatencyMs,
                             Map<String, Long> packetsByMessageType, Map<String, Long> packetsByMachine,
                             String healthStatus, long timestamp) {
        this.packetsPerSecond = packetsPerSecond;
        this.throughputKbPerSec = throughputKbPerSec;
        this.totalPacketsReceived = totalPacketsReceived;
        this.totalBytesReceived = totalBytesReceived;
        this.sequenceGapsCount = sequenceGapsCount;
        this.potentialPacketLoss = potentialPacketLoss;
        this.averageLatencyMs = averageLatencyMs;
        this.minLatencyMs = minLatencyMs;
        this.maxLatencyMs = maxLatencyMs;
        this.packetsByMessageType = packetsByMessageType != null ? Collections.unmodifiableMap(packetsByMessageType) : Collections.emptyMap();
        this.packetsByMachine = packetsByMachine != null ? Collections.unmodifiableMap(packetsByMachine) : Collections.emptyMap();
        this.healthStatus = healthStatus != null ? healthStatus : "HEALTHY";
        this.timestamp = timestamp;
    }

    public double getPacketsPerSecond() {
        return packetsPerSecond;
    }

    public double getThroughputKbPerSec() {
        return throughputKbPerSec;
    }

    public long getTotalPacketsReceived() {
        return totalPacketsReceived;
    }

    public long getTotalBytesReceived() {
        return totalBytesReceived;
    }

    public long getSequenceGapsCount() {
        return sequenceGapsCount;
    }

    public long getPotentialPacketLoss() {
        return potentialPacketLoss;
    }

    public double getAverageLatencyMs() {
        return averageLatencyMs;
    }

    public double getMinLatencyMs() {
        return minLatencyMs;
    }

    public double getMaxLatencyMs() {
        return maxLatencyMs;
    }

    public Map<String, Long> getPacketsByMessageType() {
        return packetsByMessageType;
    }

    public Map<String, Long> getPacketsByMachine() {
        return packetsByMachine;
    }

    public String getHealthStatus() {
        return healthStatus;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("[NetworkStats %s] %.1f pkt/s | %.2f Ko/s | Pertes potentielles: %d | Latence moy: %.1f ms",
                healthStatus, packetsPerSecond, throughputKbPerSec, potentialPacketLoss, averageLatencyMs);
    }
}
