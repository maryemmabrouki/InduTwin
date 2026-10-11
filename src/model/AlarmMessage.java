package model;

import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.SensorType;

/**
 * Message réseau diffusant une alarme ou un événement de sécurité industrielle.
 * Format de sérialisation : ALARM,machineId,alarmLevel,eventType,sensorType,value,message,sequenceNumber,timestamp
 */
public class AlarmMessage implements NetworkMessage {

    private final String machineId;
    private final AlarmLevel alarmLevel;
    private final EventType eventType;
    private final SensorType sensorType;
    private final double value;
    private final String description;
    private final long sequenceNumber;
    private final long timestamp;

    public AlarmMessage(String machineId, AlarmLevel alarmLevel, EventType eventType, SensorType sensorType,
                        double value, String description, long sequenceNumber, long timestamp) {
        this.machineId = machineId;
        this.alarmLevel = alarmLevel != null ? alarmLevel : AlarmLevel.WARNING;
        this.eventType = eventType != null ? eventType : EventType.THRESHOLD_EXCEEDED;
        this.sensorType = sensorType != null ? sensorType : SensorType.TEMPERATURE;
        this.value = value;
        this.description = description != null ? description.replace(",", ";") : "";
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
    }

    public AlarmMessage(String machineId, AlarmLevel alarmLevel, EventType eventType, SensorType sensorType,
                        double value, String description, long sequenceNumber) {
        this(machineId, alarmLevel, eventType, sensorType, value, description, sequenceNumber, System.currentTimeMillis());
    }

    @Override
    public String getMessageType() {
        return "ALARM";
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String getMachineId() {
        return machineId;
    }

    public AlarmLevel getAlarmLevel() {
        return alarmLevel;
    }

    public EventType getEventType() {
        return eventType;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public double getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String serialize() {
        return "ALARM," +
                machineId + "," +
                alarmLevel.name() + "," +
                eventType.name() + "," +
                sensorType.name() + "," +
                value + "," +
                description + "," +
                sequenceNumber + "," +
                timestamp;
    }

    @Override
    public String toString() {
        return String.format("[ALARM #%d] [%s] %s (%s) - %s = %.1f : %s",
                sequenceNumber, alarmLevel, eventType, machineId, sensorType, value, description);
    }
}
