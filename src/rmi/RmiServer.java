package rmi;

import config.NetworkConfig;
import industrial.IndustrialSupervisor;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

/**
 * Serveur Java RMI publiant le service de supervision distante RemoteMonitoringService.
 * Gère le cycle de vie du registre RMI local et l'enregistrement/désenregistrement propre.
 */
public class RmiServer {

    private final IndustrialSupervisor supervisor;
    private final int port;
    private final String serviceName;

    private Registry registry;
    private RemoteMonitoringServiceImpl serviceImpl;
    private boolean createdRegistry = false;
    private volatile boolean running = false;
    private boolean consoleLogging = true;

    public RmiServer(IndustrialSupervisor supervisor) {
        this(supervisor, NetworkConfig.RMI_PORT, NetworkConfig.RMI_SERVICE_NAME);
    }

    public RmiServer(IndustrialSupervisor supervisor, int port, String serviceName) {
        this.supervisor = supervisor;
        this.port = port;
        this.serviceName = serviceName;
    }

    public synchronized void start() throws RemoteException {
        if (running) {
            return;
        }

        try {
            // Tentative de création du registre RMI local
            registry = LocateRegistry.createRegistry(port);
            createdRegistry = true;
        } catch (RemoteException e) {
            // Le registre existe déjà sur ce port (par exemple dans le même processus)
            registry = LocateRegistry.getRegistry(port);
            createdRegistry = false;
        }

        serviceImpl = new RemoteMonitoringServiceImpl(supervisor);
        registry.rebind(serviceName, serviceImpl);
        running = true;

        if (consoleLogging) {
            System.out.printf("[RMI-SERVER] Service '%s' publié avec succès sur le port %d%n", serviceName, port);
        }
    }

    public synchronized void stop() {
        if (!running) {
            return;
        }

        try {
            if (registry != null) {
                try {
                    registry.unbind(serviceName);
                } catch (Exception ignored) {}
            }

            if (serviceImpl != null) {
                try {
                    UnicastRemoteObject.unexportObject(serviceImpl, true);
                } catch (Exception ignored) {}
            }

            if (createdRegistry && registry != null) {
                try {
                    UnicastRemoteObject.unexportObject(registry, true);
                } catch (Exception ignored) {}
            }
        } finally {
            registry = null;
            serviceImpl = null;
            createdRegistry = false;
            running = false;
            if (consoleLogging) {
                System.out.println("[RMI-SERVER] Serveur RMI arrêté et service délié.");
            }
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setConsoleLogging(boolean consoleLogging) {
        this.consoleLogging = consoleLogging;
    }
}
