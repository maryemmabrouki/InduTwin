package model.industrial;

/**
 * Relevé instantané d'un capteur avec valeur, unité et horodatage.
 */
public class SensorReading {

    private final SensorType sensorType;
    private final double value;
    private final String unit;
    private final long timestamp;

    public SensorReading(SensorType sensorType, double value, String unit, long timestamp) {
        this.sensorType = sensorType;
        this.value = value;
        this.unit = unit != null ? unit : (sensorType != null ? sensorType.getDefaultUnit() : "");
        this.timestamp = timestamp;
    }

    public SensorReading(SensorType sensorType, double value) {
        this(sensorType, value, sensorType != null ? sensorType.getDefaultUnit() : "", System.currentTimeMillis());
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

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return String.format("%.2f %s (%s)", value, unit, sensorType);
    }
}
