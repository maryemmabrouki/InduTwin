package model.industrial;

import java.util.Random;

/**
 * Capteur industriel avec simulation temporelle réaliste (bruit gaussien + inertie physique)
 * et prise en charge de l'injection de pannes (surchauffe, vibrations anormales).
 */
public class Sensor {

    private final SensorType type;
    private final String unit;
    private final double nominalValue;
    private final double minAllowed;
    private final double maxAllowed;
    private final double stepNoise;
    private final Threshold threshold;

    private double currentValue;
    private Double injectedFaultTarget = null;
    private final Random random = new Random();

    public Sensor(SensorType type, double nominalValue, double minAllowed, double maxAllowed,
                  double stepNoise, Threshold threshold) {
        this.type = type;
        this.unit = type.getDefaultUnit();
        this.nominalValue = nominalValue;
        this.currentValue = nominalValue;
        this.minAllowed = minAllowed;
        this.maxAllowed = maxAllowed;
        this.stepNoise = stepNoise;
        this.threshold = threshold;
    }

    /**
     * Fait évoluer la valeur du capteur dans le temps (inertie + dérive naturelle).
     */
    public synchronized SensorReading tick() {
        if (injectedFaultTarget != null) {
            // Dérive progressive vers la cible de panne (ex: surchauffe continue)
            double diff = injectedFaultTarget - currentValue;
            currentValue += Math.signum(diff) * Math.min(Math.abs(diff), stepNoise * 2.0);
            currentValue += (random.nextDouble() - 0.5) * (stepNoise * 0.3);
        } else {
            // Comportement nominal : léger bruit aléatoire centré avec force de rappel vers la valeur nominale
            double noise = (random.nextDouble() - 0.5) * stepNoise;
            double restoringForce = (nominalValue - currentValue) * 0.1;
            currentValue += noise + restoringForce;
        }

        // Clampage dans les limites physiques
        currentValue = Math.max(minAllowed, Math.min(maxAllowed, currentValue));

        // Arrondi à une décimale
        double rounded = Math.round(currentValue * 10.0) / 10.0;
        return new SensorReading(type, rounded, unit, System.currentTimeMillis());
    }

    /**
     * Active une anomalie en forçant la valeur à dériver vers une cible anormale.
     */
    public synchronized void injectFault(double targetValue) {
        this.injectedFaultTarget = targetValue;
    }

    /**
     * Rétablit le fonctionnement nominal du capteur.
     */
    public synchronized void clearFault() {
        this.injectedFaultTarget = null;
    }

    public synchronized boolean hasActiveFault() {
        return injectedFaultTarget != null;
    }

    public synchronized double getCurrentValue() {
        return Math.round(currentValue * 10.0) / 10.0;
    }

    public SensorType getType() {
        return type;
    }

    public String getUnit() {
        return unit;
    }

    public Threshold getThreshold() {
        return threshold;
    }

    public AlarmLevel evaluateThreshold() {
        if (threshold == null) {
            return AlarmLevel.NONE;
        }
        return threshold.evaluate(getCurrentValue());
    }
}
