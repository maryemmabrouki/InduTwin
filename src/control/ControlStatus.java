package control;

/**
 * Statuts d'accusé de réception (ACK/NACK) retournés par le serveur de contrôle TCP.
 */
public enum ControlStatus {
    ACCEPTED,
    REJECTED,
    INVALID,
    TIMEOUT
}
