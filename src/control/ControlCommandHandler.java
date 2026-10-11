package control;

/**
 * Interface de traitement des commandes industrielles reçues par le serveur TCP.
 */
public interface ControlCommandHandler {

    /**
     * Traite et valide une commande industrielle, exécute les transitions d'état
     * et retourne l'accusé de réception (ACK/NACK).
     *
     * @param command La commande reçue
     * @return La réponse formalisée
     */
    ControlResponse handleCommand(ControlCommand command);
}
