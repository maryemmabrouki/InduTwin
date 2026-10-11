package model;

/**
 * Contrat d'abstraction pour tous les messages transitant sur le bus réseau.
 * Prépare l'arrivée future des messages de télémétrie, heartbeat et alarmes industrielles
 * sans couplage prématuré.
 */
public interface NetworkMessage {

    /**
     * Type de message (ex: SHAPE, et plus tard TELEMETRY, HEARTBEAT, ALARM).
     */
    String getMessageType();

    /**
     * Horodatage de création du message en millisecondes.
     */
    long getTimestamp();

    /**
     * Sérialise le message sous forme de chaîne de caractères prête à être injectée dans un DatagramPacket.
     */
    String serialize();
}
