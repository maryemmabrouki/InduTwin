package model.industrial;

/**
 * Définition des seuils d'alerte (Warning et Critique) pour un capteur.
 */
public class Threshold {

    private final double warningThreshold;
    private final double criticalThreshold;

    public Threshold(double warningThreshold, double criticalThreshold) {
        this.warningThreshold = warningThreshold;
        this.criticalThreshold = criticalThreshold;
    }

    /**
     * Évalue une valeur mesurée par rapport aux seuils configurés.
     */
    public AlarmLevel evaluate(double value) {
        if (value >= criticalThreshold) {
            return AlarmLevel.CRITICAL;
        }
        if (value >= warningThreshold) {
            return AlarmLevel.WARNING;
        }
        return AlarmLevel.NONE;
    }

    public double getWarningThreshold() {
        return warningThreshold;
    }

    public double getCriticalThreshold() {
        return criticalThreshold;
    }

    @Override
    public String toString() {
        return "Threshold{warning=" + warningThreshold + ", critical=" + criticalThreshold + '}';
    }
}
