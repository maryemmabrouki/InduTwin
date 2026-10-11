package industrial;

import config.NetworkConfig;
import model.AlarmMessage;
import model.HeartbeatMessage;
import model.TelemetryMessage;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.Machine;
import model.industrial.MachineState;
import model.industrial.Sensor;
import model.industrial.SensorReading;
import model.industrial.SensorType;
import model.industrial.Threshold;
import network.MulticastManager;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulateur d'infrastructure industrielle multi-machines.
 * Génère et diffuse de manière concurrente la télémétrie, les alarmes et les battements de cœur
 * sur le bus réseau UDP Multicast via MulticastManager.
 */
public class MachineSimulator {

    private final Map<String, Machine> machines = new LinkedHashMap<>();
    private final MulticastManager multicastManager;
    private final FaultInjector faultInjector = new FaultInjector();

    private final AtomicLong telemetrySequence = new AtomicLong(1);
    private final AtomicLong heartbeatSequence = new AtomicLong(1);
    private final AtomicLong alarmSequence = new AtomicLong(1);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledExecutorService scheduler;

    public MachineSimulator() {
        this(new MulticastManager());
    }

    public MachineSimulator(MulticastManager multicastManager) {
        this.multicastManager = multicastManager;
        initDefaultMachines();
    }

    /**
     * Initialise les 3 machines virtuelles prévues au cahier des charges.
     */
    private void initDefaultMachines() {
        // M01 : Presse hydraulique
        Machine m01 = new Machine("M01", "Presse hydraulique", MachineState.RUNNING);
        m01.addSensor(new Sensor(SensorType.TEMPERATURE, 55.0, 20.0, 120.0, 0.8, new Threshold(75.0, 90.0)));
        m01.addSensor(new Sensor(SensorType.PRESSURE, 160.0, 50.0, 300.0, 2.5, new Threshold(220.0, 260.0)));
        m01.addSensor(new Sensor(SensorType.VIBRATION, 2.2, 0.5, 25.0, 0.15, new Threshold(5.0, 8.0)));
        m01.addSensor(new Sensor(SensorType.SPEED, 1200.0, 0.0, 2000.0, 15.0, new Threshold(1500.0, 1800.0)));
        machines.put("M01", m01);

        // M02 : Four industriel
        Machine m02 = new Machine("M02", "Four industriel", MachineState.RUNNING);
        m02.addSensor(new Sensor(SensorType.TEMPERATURE, 72.0, 20.0, 150.0, 0.6, new Threshold(80.0, 95.0)));
        m02.addSensor(new Sensor(SensorType.PRESSURE, 1.8, 0.5, 10.0, 0.05, new Threshold(3.5, 5.0)));
        m02.addSensor(new Sensor(SensorType.ENERGY, 45.0, 10.0, 100.0, 1.2, new Threshold(70.0, 85.0)));
        machines.put("M02", m02);

        // M03 : Robot industriel
        Machine m03 = new Machine("M03", "Robot industriel", MachineState.RUNNING);
        m03.addSensor(new Sensor(SensorType.TEMPERATURE, 45.0, 15.0, 90.0, 0.5, new Threshold(65.0, 80.0)));
        m03.addSensor(new Sensor(SensorType.VIBRATION, 1.8, 0.2, 15.0, 0.1, new Threshold(4.5, 7.0)));
        m03.addSensor(new Sensor(SensorType.SPEED, 180.0, 0.0, 300.0, 3.0, new Threshold(240.0, 280.0)));
        m03.addSensor(new Sensor(SensorType.LOAD, 65.0, 0.0, 100.0, 1.5, new Threshold(85.0, 95.0)));
        machines.put("M03", m03);
    }

    /**
     * Démarre la couche réseau et les tâches périodiques de télémétrie et heartbeat.
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        if (!multicastManager.isRunning()) {
            multicastManager.start();
        }

        running.set(true);

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Machine-Simulator-Task");
            t.setDaemon(true);
            return t;
        });

        // 1. Tâche périodique de télémétrie (toutes les secondes)
        scheduler.scheduleAtFixedRate(this::tickTelemetryAndAlarms, 0,
                NetworkConfig.TELEMETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);

        // 2. Tâche périodique de Heartbeat (toutes les 2 secondes)
        scheduler.scheduleAtFixedRate(this::tickHeartbeats, 500,
                NetworkConfig.HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        System.out.println("[MachineSimulator] Simulateur démarré avec 3 machines (M01, M02, M03).");
    }

    /**
     * Fait évoluer les capteurs de chaque machine, génère la télémétrie et émet les alarmes en Multicast.
     */
    private void tickTelemetryAndAlarms() {
        if (!running.get()) return;

        for (Machine machine : machines.values()) {
            List<SensorReading> readings = machine.tick();

            for (SensorReading reading : readings) {
                long seq = telemetrySequence.getAndIncrement();
                TelemetryMessage telemMsg = new TelemetryMessage(
                        machine.getId(),
                        reading.getSensorType(),
                        reading.getValue(),
                        reading.getUnit(),
                        seq,
                        reading.getTimestamp()
                );

                try {
                    if (running.get() && multicastManager.isRunning()) {
                        multicastManager.send(telemMsg);
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("[MachineSimulator] Erreur émission télémétrie: " + e.getMessage());
                    }
                }

                // Vérification du seuil pour émission d'alarme
                Sensor sensor = machine.getSensor(reading.getSensorType());
                if (sensor != null) {
                    AlarmLevel level = sensor.evaluateThreshold();
                    if (level != AlarmLevel.NONE) {
                        EventType eventType = (level == AlarmLevel.CRITICAL)
                                ? EventType.MACHINE_FAILURE : EventType.THRESHOLD_EXCEEDED;
                        String desc = String.format("Seuil %s dépassé : %.1f %s",
                                level, reading.getValue(), reading.getUnit());

                        AlarmMessage alarmMsg = new AlarmMessage(
                                machine.getId(),
                                level,
                                eventType,
                                reading.getSensorType(),
                                reading.getValue(),
                                desc,
                                alarmSequence.getAndIncrement(),
                                System.currentTimeMillis()
                        );

                        try {
                            if (running.get() && multicastManager.isRunning()) {
                                multicastManager.send(alarmMsg);
                            }
                        } catch (IOException e) {
                            if (running.get()) {
                                System.err.println("[MachineSimulator] Erreur émission alarme: " + e.getMessage());
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Émet un paquet de présence Heartbeat pour chaque machine active dont le heartbeat est activé.
     */
    private void tickHeartbeats() {
        if (!running.get()) return;

        for (Machine machine : machines.values()) {
            if (machine.isHeartbeatEnabled()) {
                long seq = heartbeatSequence.getAndIncrement();
                HeartbeatMessage hbMsg = new HeartbeatMessage(
                        machine.getId(),
                        machine.getState(),
                        seq,
                        System.currentTimeMillis()
                );

                try {
                    if (running.get() && multicastManager.isRunning()) {
                        multicastManager.send(hbMsg);
                    }
                } catch (IOException e) {
                    if (running.get()) {
                        System.err.println("[MachineSimulator] Erreur émission heartbeat: " + e.getMessage());
                    }
                }
            }
        }
    }

    /**
     * Arrêt propre du simulateur et du pool de threads.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (scheduler != null) {
            scheduler.shutdownNow();
        }

        if (multicastManager != null && multicastManager.isRunning()) {
            multicastManager.stop();
        }

        System.out.println("[MachineSimulator] Simulateur arrêté.");
    }

    public boolean isRunning() {
        return running.get();
    }

    public Machine getMachine(String id) {
        return machines.get(id);
    }

    public Map<String, Machine> getMachines() {
        return Collections.unmodifiableMap(machines);
    }

    public FaultInjector getFaultInjector() {
        return faultInjector;
    }

    public MulticastManager getMulticastManager() {
        return multicastManager;
    }
}
