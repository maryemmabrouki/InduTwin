package industrial;

import model.industrial.Machine;
import model.industrial.Sensor;
import model.industrial.SensorType;

/**
 * Module d'injection d'anomalies industrielles pour les scénarios de test et de démonstration.
 * Permet de provoquer des pannes contrôlées (surchauffe, vibrations excessives, perte de heartbeat).
 */
public class FaultInjector {

    /**
     * Provoque une surchauffe progressive sur une machine en forçant la dérive de son capteur de température.
     *
     * @param machine    La machine ciblée
     * @param targetTemp Température cible (ex: 85°C pour WARNING, 100°C pour CRITICAL)
     */
    public void injectOverheating(Machine machine, double targetTemp) {
        if (machine == null) return;
        Sensor tempSensor = machine.getSensor(SensorType.TEMPERATURE);
        if (tempSensor != null) {
            tempSensor.injectFault(targetTemp);
            System.out.printf("[FAULT_INJECTOR] Surchauffe injectée sur %s (Cible: %.1f °C)%n",
                    machine.getId(), targetTemp);
        }
    }

    /**
     * Provoque des vibrations mécaniques excessives sur une machine.
     *
     * @param machine   La machine ciblée
     * @param targetVib Niveau de vibration cible (ex: 9.0 mm/s)
     */
    public void injectExcessiveVibration(Machine machine, double targetVib) {
        if (machine == null) return;
        Sensor vibSensor = machine.getSensor(SensorType.VIBRATION);
        if (vibSensor != null) {
            vibSensor.injectFault(targetVib);
            System.out.printf("[FAULT_INJECTOR] Vibration excessive injectée sur %s (Cible: %.1f mm/s)%n",
                    machine.getId(), targetVib);
        }
    }

    /**
     * Simule la coupure de la liaison ou un plantage réseau en désactivant le Heartbeat d'une machine.
     */
    public void injectHeartbeatLoss(Machine machine) {
        if (machine == null) return;
        machine.setHeartbeatEnabled(false);
        System.out.printf("[FAULT_INJECTOR] Heartbeat coupé pour %s (Simulation perte réseau / crash)%n",
                machine.getId());
    }

    /**
     * Rétablit le fonctionnement nominal d'une machine (réactivation capteurs et heartbeat).
     */
    public void restoreNormalOperation(Machine machine) {
        if (machine == null) return;
        for (Sensor sensor : machine.getSensors().values()) {
            sensor.clearFault();
        }
        machine.setHeartbeatEnabled(true);
        System.out.printf("[FAULT_INJECTOR] Fonctionnement nominal rétabli pour %s%n", machine.getId());
    }
}
