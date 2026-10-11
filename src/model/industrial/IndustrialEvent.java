package model.industrial;

/**
 * Événement métier généré lors d'un changement d'état ou d'un dépassement de seuil.
 */
public class IndustrialEvent {

    private final EventType eventType;
    private final String machineId;
    private final AlarmLevel level;
    private final String message;
    private final long timestamp;

    public IndustrialEvent(EventType eventType, String machineId, AlarmLevel level, String message, long timestamp) {
        this.eventType = eventType;
        this.machineId = machineId;
        this.level = level != null ? level : AlarmLevel.INFO;
        this.message = message != null ? message : "";
        this.timestamp = timestamp;
    }

    public IndustrialEvent(EventType eventType, String machineId, AlarmLevel level, String message) {
        this(eventType, machineId, level, message, System.currentTimeMillis());
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getMachineId() {
        return machineId;
    }

    public AlarmLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("[%s] [%s] %s : %s", level, machineId, eventType, message);
    }
}
