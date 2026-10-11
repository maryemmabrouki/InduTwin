package model.industrial;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modèle d'équipement industriel doté de capteurs, d'une machine à états
 * et d'un mécanisme de battement de cœur (heartbeat).
 */
public class Machine {

    private final String id;
    private final String name;
    private MachineState state;
    private final Map<SensorType, Sensor> sensors = new LinkedHashMap<>();
    private volatile boolean heartbeatEnabled = true;
    private int criticalTicksCount = 0;

    public Machine(String id, String name, MachineState initialState) {
        this.id = id;
        this.name = name;
        this.state = initialState != null ? initialState : MachineState.STOPPED;
    }

    public void addSensor(Sensor sensor) {
        if (sensor != null) {
            sensors.put(sensor.getType(), sensor);
        }
    }

    public Sensor getSensor(SensorType type) {
        return sensors.get(type);
    }

    public Map<SensorType, Sensor> getSensors() {
        return Collections.unmodifiableMap(sensors);
    }

    /**
     * Fait évoluer les capteurs de la machine et réévalue son état opérationnel.
     */
    public synchronized List<SensorReading> tick() {
        List<SensorReading> readings = new ArrayList<>();
        if (state == MachineState.STOPPED || state == MachineState.FAILURE) {
            // Machine à l'arrêt ou en panne : valeurs statiques nominales/basses
            for (Sensor sensor : sensors.values()) {
                readings.add(new SensorReading(sensor.getType(), sensor.getCurrentValue()));
            }
            return readings;
        }

        AlarmLevel highestAlarm = AlarmLevel.NONE;

        for (Sensor sensor : sensors.values()) {
            SensorReading reading = sensor.tick();
            readings.add(reading);

            AlarmLevel level = sensor.evaluateThreshold();
            if (level.ordinal() > highestAlarm.ordinal()) {
                highestAlarm = level;
            }
        }

        // Gestion automatique de l'état selon la sévérité des capteurs
        if (highestAlarm == AlarmLevel.CRITICAL) {
            criticalTicksCount++;
            if (criticalTicksCount >= 3) {
                // Arrêt automatique d'urgence après 3 cycles consécutifs en critique
                transitionTo(MachineState.FAILURE);
            } else {
                transitionTo(MachineState.CRITICAL);
            }
        } else if (highestAlarm == AlarmLevel.WARNING) {
            criticalTicksCount = 0;
            transitionTo(MachineState.WARNING);
        } else {
            criticalTicksCount = 0;
            if (state == MachineState.WARNING || state == MachineState.CRITICAL || state == MachineState.STARTING) {
                transitionTo(MachineState.RUNNING);
            }
        }

        return readings;
    }

    /**
     * Effectue une transition d'état si autorisée par la machine à états.
     */
    public synchronized boolean transitionTo(MachineState targetState) {
        if (this.state == targetState) {
            return true;
        }

        if (this.state.canTransitionTo(targetState)) {
            this.state = targetState;
            return true;
        }
        return false;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public synchronized MachineState getState() {
        return state;
    }

    public boolean isHeartbeatEnabled() {
        return heartbeatEnabled;
    }

    public void setHeartbeatEnabled(boolean heartbeatEnabled) {
        this.heartbeatEnabled = heartbeatEnabled;
    }

    @Override
    public String toString() {
        return String.format("Machine[%s - %s : %s]", id, name, state);
    }
}
