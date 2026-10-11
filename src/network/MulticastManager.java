package network;

import config.NetworkConfig;
import model.NetworkMessage;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Couche d'accès réseau UDP Multicast découplée de l'interface graphique.
 * Responsable de la gestion du cycle de vie du MulticastSocket, de l'écoute threadée
 * et de la notification des observateurs via l'interface MessageListener.
 */
public class MulticastManager {

    private final String groupAddressStr;
    private final int port;
    private NetworkInterface networkInterface;

    private MulticastSocket socket;
    private InetSocketAddress groupSocketAddress;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread receiverThread;

    private final List<MessageListener> listeners = new CopyOnWriteArrayList<>();

    public MulticastManager() {
        this(NetworkConfig.MULTICAST_GROUP, NetworkConfig.PORT, NetworkConfig.getPreferredNetworkInterface());
    }

    public MulticastManager(String groupAddress, int port, NetworkInterface networkInterface) {
        this.groupAddressStr = groupAddress;
        this.port = port;
        this.networkInterface = networkInterface;
    }

    public void addListener(MessageListener listener) {
        if (listener != null && !listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public void removeListener(MessageListener listener) {
        listeners.remove(listener);
    }

    /**
     * Initialise le socket, rejoint le groupe multicast et démarre le thread d'écoute.
     */
    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        InetAddress mcastAddr = InetAddress.getByName(groupAddressStr);
        this.groupSocketAddress = new InetSocketAddress(mcastAddr, port);

        this.socket = new MulticastSocket(port);
        this.socket.setReuseAddress(true);
        this.socket.setTimeToLive(NetworkConfig.MULTICAST_TTL);

        if (this.networkInterface == null) {
            this.networkInterface = NetworkConfig.getPreferredNetworkInterface();
        }

        // Utilisation de l'API moderne Java (remplace l'API dépréciée joinGroup(InetAddress))
        if (this.networkInterface != null) {
            this.socket.joinGroup(this.groupSocketAddress, this.networkInterface);
        } else {
            // Si aucune interface spécifique n'a pu être résolue, on cherche la première interface réseau valide
            NetworkInterface fallbackIf = NetworkConfig.getPreferredNetworkInterface();
            if (fallbackIf != null) {
                this.networkInterface = fallbackIf;
                this.socket.joinGroup(this.groupSocketAddress, fallbackIf);
            } else {
                throw new IOException("Aucune interface réseau active supportant le Multicast n'a été détectée.");
            }
        }

        running.set(true);

        receiverThread = new Thread(this::listenLoop, "Multicast-Receiver-Thread");
        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    /**
     * Boucle d'écoute bloquante exécutée dans un thread d'arrière-plan dédié.
     */
    private void listenLoop() {
        byte[] buffer = new byte[NetworkConfig.BUFFER_SIZE];

        while (running.get() && socket != null && !socket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                String raw = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8);
                NetworkMessage message = MessageParser.decode(raw);

                if (message != null) {
                    notifyMessageReceived(message, packet.getAddress(), packet.getLength());
                }
            } catch (IOException e) {
                // Si l'arrêt a été demandé volontairement, l'exception de fermeture est attendue
                if (running.get()) {
                    notifyNetworkError(e);
                }
            }
        }
    }

    /**
     * Émet un objet NetworkMessage sérialisé vers le groupe Multicast.
     */
    public void send(NetworkMessage message) throws IOException {
        if (message == null) {
            return;
        }
        send(MessageParser.encode(message));
    }

    /**
     * Émet une chaîne brute vers le groupe Multicast.
     */
    public void send(String rawPayload) throws IOException {
        if (socket == null || socket.isClosed()) {
            throw new IOException("Socket Multicast fermé ou non démarré.");
        }

        byte[] data = rawPayload.getBytes(StandardCharsets.UTF_8);
        DatagramPacket packet = new DatagramPacket(
                data,
                data.length,
                groupSocketAddress.getAddress(),
                groupSocketAddress.getPort()
        );
        socket.send(packet);
    }

    /**
     * Arrêt propre du thread d'écoute, désabonnement du groupe et fermeture du socket.
     */
    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (socket != null && !socket.isClosed()) {
            try {
                if (networkInterface != null && groupSocketAddress != null) {
                    socket.leaveGroup(groupSocketAddress, networkInterface);
                }
            } catch (Exception ignored) {
                // Ignorer les erreurs résiduelles lors du désabonnement en fermeture
            } finally {
                socket.close();
            }
        }

        if (receiverThread != null && receiverThread.isAlive()) {
            receiverThread.interrupt();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public String getGroupAddress() {
        return groupAddressStr;
    }

    public int getPort() {
        return port;
    }

    private void notifyMessageReceived(NetworkMessage message, InetAddress sender, int byteLength) {
        for (MessageListener listener : listeners) {
            try {
                listener.onMessageReceived(message, sender, byteLength);
            } catch (Exception e) {
                System.err.println("[MulticastManager] Erreur dans un listener: " + e.getMessage());
            }
        }
    }

    private void notifyNetworkError(Exception e) {
        for (MessageListener listener : listeners) {
            try {
                listener.onNetworkError(e);
            } catch (Exception ex) {
                System.err.println("[MulticastManager] Erreur dans notification d'erreur: " + ex.getMessage());
            }
        }
    }
}
