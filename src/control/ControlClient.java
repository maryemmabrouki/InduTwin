package control;

import config.NetworkConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

/**
 * Client TCP dédié à l'envoi fiable de commandes industrielles et à la réception d'ACKs.
 * Conçu pour fonctionner hors du thread Swing EDT avec gestion complète des timeouts et déconnexions.
 */
public class ControlClient implements AutoCloseable {

    private final String host;
    private final int port;
    private final int timeoutMs;

    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;

    public ControlClient() {
        this("127.0.0.1", NetworkConfig.TCP_CONTROL_PORT, NetworkConfig.TCP_TIMEOUT_MS);
    }

    public ControlClient(String host, int port) {
        this(host, port, NetworkConfig.TCP_TIMEOUT_MS);
    }

    public ControlClient(String host, int port, int timeoutMs) {
        this.host = (host != null && !host.isBlank()) ? host : "127.0.0.1";
        this.port = port;
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : NetworkConfig.TCP_TIMEOUT_MS;
    }

    /**
     * Établit la connexion TCP vers le serveur de contrôle.
     */
    public synchronized void connect() throws IOException {
        if (isConnected()) {
            return;
        }

        socket = new Socket();
        socket.connect(new InetSocketAddress(host, port), timeoutMs);
        socket.setSoTimeout(timeoutMs);

        reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
    }

    /**
     * Vérifie si le client est actuellement connecté.
     */
    public synchronized boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Ferme proprement la connexion TCP.
     */
    public synchronized void disconnect() {
        try {
            if (reader != null) {
                reader.close();
            }
        } catch (IOException ignored) {}

        if (writer != null) {
            writer.close();
        }

        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {}

        socket = null;
        reader = null;
        writer = null;
    }

    /**
     * Envoie une commande de façon fiable et attend l'accusé de réception (ACK/NACK).
     * Reconnecte automatiquement si la liaison n'était pas active.
     */
    public synchronized ControlResponse sendCommand(ControlCommand command) {
        if (command == null) {
            return ControlResponse.invalid("cmd-err", "COMMANDE_NULLE");
        }

        try {
            if (!isConnected()) {
                connect();
            }

            // Émission de la commande formalisée
            writer.println(command.serialize());

            // Réception de l'ACK/NACK
            String responseLine = reader.readLine();
            if (responseLine == null) {
                disconnect();
                return new ControlResponse(
                        command.getCommandId(),
                        command.getMachineId(),
                        command.getCommandType(),
                        ControlStatus.TIMEOUT,
                        "Connexion fermée par le serveur avant réception de l'ACK",
                        System.currentTimeMillis()
                );
            }

            ControlResponse response = ControlResponse.parse(responseLine);
            if (response == null) {
                return ControlResponse.invalid(command.getCommandId(), "REPONSE_INVALIDE: " + responseLine);
            }

            return response;

        } catch (SocketTimeoutException e) {
            disconnect();
            return ControlResponse.timeout(command);
        } catch (IOException e) {
            disconnect();
            return new ControlResponse(
                    command.getCommandId(),
                    command.getMachineId(),
                    command.getCommandType(),
                    ControlStatus.TIMEOUT,
                    "Erreur réseau TCP (" + e.getClass().getSimpleName() + ": " + e.getMessage() + ")",
                    System.currentTimeMillis()
            );
        }
    }

    /**
     * Méthode utilitaire simplifiée pour envoyer une commande.
     */
    public ControlResponse sendCommand(String machineId, ControlCommandType type) {
        String cmdId = "cmd-" + System.nanoTime();
        ControlCommand cmd = new ControlCommand(cmdId, machineId, type);
        return sendCommand(cmd);
    }

    @Override
    public void close() {
        disconnect();
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }
}
