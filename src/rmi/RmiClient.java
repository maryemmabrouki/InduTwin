package rmi;

import config.NetworkConfig;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;

/**
 * Client Java RMI permettant d'interroger à distance le service de supervision.
 */
public class RmiClient {

    private final String host;
    private final int port;
    private final String serviceName;

    private RemoteMonitoringService service;

    public RmiClient() {
        this("127.0.0.1", NetworkConfig.RMI_PORT, NetworkConfig.RMI_SERVICE_NAME);
    }

    public RmiClient(String host, int port) {
        this(host, port, NetworkConfig.RMI_SERVICE_NAME);
    }

    public RmiClient(String host, int port, String serviceName) {
        this.host = (host != null && !host.isBlank()) ? host : "127.0.0.1";
        this.port = port;
        this.serviceName = serviceName;
    }

    public synchronized void connect() throws RemoteException, NotBoundException {
        Registry registry = LocateRegistry.getRegistry(host, port);
        service = (RemoteMonitoringService) registry.lookup(serviceName);
    }

    public synchronized boolean isConnected() {
        return service != null;
    }

    public synchronized void disconnect() {
        service = null;
    }

    private void ensureConnected() throws RemoteException {
        if (service == null) {
            try {
                connect();
            } catch (NotBoundException e) {
                throw new RemoteException("Service RMI non trouvé dans le registre: " + serviceName, e);
            }
        }
    }

    public synchronized String getMachineStatus(String machineId) throws RemoteException {
        ensureConnected();
        return service.getMachineStatus(machineId);
    }

    public synchronized Map<String, String> getAllMachineStatuses() throws RemoteException {
        ensureConnected();
        return service.getAllMachineStatuses();
    }

    public synchronized List<String> getActiveAlarms() throws RemoteException {
        ensureConnected();
        return service.getActiveAlarms();
    }

    public synchronized Map<String, Object> getNetworkStatistics() throws RemoteException {
        ensureConnected();
        return service.getNetworkStatistics();
    }

    public synchronized List<String> getRecentEvents(int limit) throws RemoteException {
        ensureConnected();
        return service.getRecentEvents(limit);
    }

    public synchronized List<String> getHistoricAlarms(int limit) throws RemoteException {
        ensureConnected();
        return service.getHistoricAlarms(limit);
    }

    public synchronized List<String> getRecentCommands(int limit) throws RemoteException {
        ensureConnected();
        return service.getRecentCommands(limit);
    }

    public synchronized List<String> getMachineHistory(String machineId, int limit) throws RemoteException {
        ensureConnected();
        return service.getMachineHistory(machineId, limit);
    }

    public synchronized Map<String, Object> getDatabaseStatistics() throws RemoteException {
        ensureConnected();
        return service.getDatabaseStatistics();
    }

    public RemoteMonitoringService getRemoteService() {
        return service;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getServiceName() {
        return serviceName;
    }
}
