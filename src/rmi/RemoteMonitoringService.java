package rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Interface distante Java RMI pour la supervision industrielle en lecture seule.
 * Permet l'interrogation à distance des états machines, alarmes et métriques réseau.
 */
public interface RemoteMonitoringService extends Remote {

    /**
     * Retourne l'état opérationnel actuel d'une machine spécifique (ex: "RUNNING", "FAILURE", "STOPPED").
     *
     * @param machineId L'identifiant de la machine (ex: "M01")
     * @return Le nom de l'état ou "UNKNOWN" si non trouvée
     */
    String getMachineStatus(String machineId) throws RemoteException;

    /**
     * Retourne un dictionnaire associant chaque identifiant de machine à son état actuel.
     *
     * @return Map clé: machineId -> valeur: état
     */
    Map<String, String> getAllMachineStatuses() throws RemoteException;

    /**
     * Retourne la liste des alarmes actives ou critiques enregistrées.
     *
     * @return Liste textuelle descriptive des alarmes
     */
    List<String> getActiveAlarms() throws RemoteException;

    /**
     * Retourne une synthèse des métriques réseau (débit, paquets/s, pertes de séquence, santé).
     *
     * @return Map des métriques réseau
     */
    Map<String, Object> getNetworkStatistics() throws RemoteException;

    /**
     * Retourne les N derniers événements industriels journalisés.
     *
     * @param limit Nombre maximal d'événements à retourner
     * @return Liste ordonnée des événements récents
     */
    List<String> getRecentEvents(int limit) throws RemoteException;

    /**
     * Retourne l'historique des alarmes persistées dans SQLite.
     *
     * @param limit Nombre maximal d'alarmes historiques
     * @return Liste des alarmes persistées
     */
    List<String> getHistoricAlarms(int limit) throws RemoteException;

    /**
     * Retourne l'historique d'audit des commandes TCP persistées dans SQLite.
     *
     * @param limit Nombre maximal de commandes historiques
     * @return Liste descriptive des commandes exécutées
     */
    List<String> getRecentCommands(int limit) throws RemoteException;

    /**
     * Reconstruit la chronologie d'une machine à partir des données historiques SQLite.
     *
     * @param machineId Identifiant machine (ex: "M02")
     * @param limit Nombre maximal d'entrées
     * @return Liste ordonnée de la chronologie machine
     */
    List<String> getMachineHistory(String machineId, int limit) throws RemoteException;

    /**
     * Retourne la synthèse des compteurs stockés dans SQLite (télémétrie, alarmes, commandes).
     *
     * @return Map des statistiques SQLite
     */
    Map<String, Object> getDatabaseStatistics() throws RemoteException;
}
