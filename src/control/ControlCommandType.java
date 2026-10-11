package control;

/**
 * Types de commandes industrielles exécutables sur les machines via le canal TCP point-à-point.
 */
public enum ControlCommandType {
    START,
    STOP,
    EMERGENCY_STOP,
    RESET,
    MAINTENANCE,
    RESUME
}
