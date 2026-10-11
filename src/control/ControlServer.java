package control;

import config.NetworkConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serveur TCP dédié au contrôle industriel fiable point-à-point.
 * Écoute les commandes textuelles (CMD,...), les transmet au ControlCommandHandler,
 * et renvoie un accusé de réception formel (ACK,...).
 */
public class ControlServer {

    private final int port;
    private final ControlCommandHandler commandHandler;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private ServerSocket serverSocket;
    private Thread acceptorThread;
    private ExecutorService clientPool;
    private final Set<Socket> activeClientSockets = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private boolean consoleLogging = true;

    public ControlServer(ControlCommandHandler commandHandler) {
        this(NetworkConfig.TCP_CONTROL_PORT, commandHandler);
    }

    public ControlServer(int port, ControlCommandHandler commandHandler) {
        this.port = port;
        this.commandHandler = commandHandler;
    }

    public synchronized void start() throws IOException {
        if (running.get()) {
            return;
        }

        serverSocket = new ServerSocket(port);
        running.set(true);

        clientPool = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "TCP-Control-Worker");
            t.setDaemon(true);
            return t;
        });

        acceptorThread = new Thread(this::acceptLoop, "TCP-Control-Acceptor");
        acceptorThread.setDaemon(true);
        acceptorThread.start();

        if (consoleLogging) {
            System.out.println("[TCP-CONTROL] Serveur de contrôle démarré sur le port " + port);
        }
    }

    private void acceptLoop() {
        while (running.get()) {
            try {
                Socket clientSocket = serverSocket.accept();
                activeClientSockets.add(clientSocket);
                clientPool.submit(() -> handleClient(clientSocket));
            } catch (SocketException e) {
                // Arrêt normal lors de serverSocket.close()
                if (!running.get()) {
                    break;
                }
                if (consoleLogging) {
                    System.err.println("[TCP-CONTROL] SocketException serveur: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running.get() && consoleLogging) {
                    System.err.println("[TCP-CONTROL] Erreur acceptation client: " + e.getMessage());
                }
            }
        }
    }

    private void handleClient(Socket socket) {
        try (
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)
        ) {
            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                ControlCommand command = ControlCommand.parse(line);
                ControlResponse response;

                if (command == null) {
                    response = ControlResponse.invalid("cmd-err", "COMMANDE_INVALIDE_OU_NON_CONFORME");
                } else if (commandHandler != null) {
                    response = commandHandler.handleCommand(command);
                } else {
                    response = ControlResponse.rejected(command, "GESTIONNAIRE_NON_DISPONIBLE");
                }

                writer.println(response.serialize());
            }
        } catch (SocketException ignored) {
            // Déconnexion normale du client
        } catch (IOException e) {
            if (running.get() && consoleLogging) {
                System.err.println("[TCP-CONTROL] Erreur lecture client: " + e.getMessage());
            }
        } finally {
            activeClientSockets.remove(socket);
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {}

        // Fermeture de tous les sockets clients actifs
        for (Socket socket : activeClientSockets) {
            try {
                if (!socket.isClosed()) {
                    socket.close();
                }
            } catch (IOException ignored) {}
        }
        activeClientSockets.clear();

        if (clientPool != null) {
            clientPool.shutdownNow();
            try {
                clientPool.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }

        if (consoleLogging) {
            System.out.println("[TCP-CONTROL] Serveur de contrôle arrêté.");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public int getPort() {
        return port;
    }

    public void setConsoleLogging(boolean consoleLogging) {
        this.consoleLogging = consoleLogging;
    }
}
