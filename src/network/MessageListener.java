package network;

import model.NetworkMessage;
import java.net.InetAddress;

/**
 * Interface d'écoute pour les événements réseau Multicast.
 * Permet d'inverser le contrôle (IoC / Observer) pour que la couche réseau
 * ignore totalement l'existence de l'IHM (Swing) ou d'autres consommateurs.
 */
public interface MessageListener {

    /**
     * Invoqué lorsqu'un message réseau valide a été décodé.
     *
     * @param message       Le message métier reçu (ex: ShapeMessage)
     * @param senderAddress L'adresse IP de la machine émettrice
     */
    void onMessageReceived(NetworkMessage message, InetAddress senderAddress);

    /**
     * Surcharge fournissant également la taille exacte en octets du paquet UDP reçu.
     */
    default void onMessageReceived(NetworkMessage message, InetAddress senderAddress, int byteLength) {
        onMessageReceived(message, senderAddress);
    }

    /**
     * Invoqué lorsqu'une erreur réseau survient.
     *
     * @param e L'exception capturée
     */
    void onNetworkError(Exception e);
}
