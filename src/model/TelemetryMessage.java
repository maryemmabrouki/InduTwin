package model;

import model.industrial.SensorType;

/**
 * Message réseau diffusant la valeur instantanée d'un capteur pour une machine donnée.
 * Format de sérialisation : TELEMETRY,machineId,sensorType,value,unit,sequenceNumber,timestamp
 */
public class TelemetryMessage implements NetworkMessage {

    private final String machineId;
    private final SensorType sensorType;
    private final double value;
    private final String unit;
    private final long sequenceNumber;
    private final long timestamp;

    public TelemetryMessage(String machineId, SensorType sensorType, double value, String unit,
                            long sequenceNumber, long timestamp) {
        this.machineId = machineId;
        this.sensorType = sensorType;
        this.value = value;
        this.unit = unit != null ? unit : (sensorType != null ? sensorType.getDefaultUnit() : "");
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
    }

    public TelemetryMessage(String machineId, SensorType sensorType, double value, String unit, long sequenceNumber) {
        this(machineId, sensorType, value, unit, sequenceNumber, System.currentTimeMillis());
    }

    @Override
    public String getMessageType() {
        return "TELEMETRY";
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String getMachineId() {
        return machineId;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public double getValue() {
        return value;
    }

    public String getUnit() {
        return unit;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String serialize() {
        return "TELEMETRY," +
                machineId + "," +
                sensorType.name() + "," +
                value + "," +
                unit + "," +
                sequenceNumber + "," +
                timestamp;
    }

    @Override
    public String toString() {
        return String.format("[TELEMETRY #%d] [%s] %s = %.1f %s",
                sequenceNumber, machineId, sensorType, value, unit);
    }
}
