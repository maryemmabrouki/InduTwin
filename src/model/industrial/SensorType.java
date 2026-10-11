package model.industrial;

/**
 * Types de capteurs industriels standards avec leur unité physique par défaut.
 */
public enum SensorType {
    TEMPERATURE("C"),
    PRESSURE("bar"),
    VIBRATION("mm/s"),
    SPEED("rpm"),
    ENERGY("kW"),
    LOAD("%");

    private final String defaultUnit;

    SensorType(String defaultUnit) {
        this.defaultUnit = defaultUnit;
    }

    public String getDefaultUnit() {
        return defaultUnit;
    }
}
