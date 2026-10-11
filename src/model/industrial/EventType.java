package model.industrial;

/**
 * Typologie des événements industriels tracés par le système de supervision.
 */
public enum EventType {
    MACHINE_STARTED,
    MACHINE_STOPPED,
    THRESHOLD_EXCEEDED,
    THRESHOLD_NORMALIZED,
    MACHINE_FAILURE,
    MACHINE_OFFLINE,
    MACHINE_RECOVERED,
    MAINTENANCE_REQUIRED,
    EMERGENCY_STOP
}
