package persistence;

import model.AlarmMessage;
import model.TelemetryMessage;
import model.industrial.IndustrialEvent;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Façade de persistance industrielle asynchrone (SQLite via JDBC).
 * Fournit une file d'écriture non bloquante pour préserver la fluidité de la réception UDP
 * et du Dashboard SCADA, tout en garantissant l'intégrité de l'audit et de l'historique.
 */
public class PersistenceService {

    private final DatabaseManager dbManager;
    private final MachineRepository machineRepo;
    private final TelemetryRepository telemetryRepo;
    private final AlarmRepository alarmRepo;
    private final CommandRepository commandRepo;
    private final EventRepository eventRepo;

    private final BlockingQueue<Runnable> writeQueue = new LinkedBlockingQueue<>(20000);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread workerThread;
    private volatile boolean dbAvailable = false;
    private boolean consoleLogging = true;

    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");

    public PersistenceService() {
        this(new DatabaseManager());
    }

    public PersistenceService(DatabaseManager dbManager) {
        this.dbManager = dbManager;
        this.machineRepo = new MachineRepository(dbManager);
        this.telemetryRepo = new TelemetryRepository(dbManager);
        this.alarmRepo = new AlarmRepository(dbManager);
        this.commandRepo = new CommandRepository(dbManager);
        this.eventRepo = new EventRepository(dbManager);
    }

    /**
     * Démarre le service : initialise les tables SQLite idempotentes et lance le worker asynchrone.
     */
    public synchronized void start() {
        if (running.get()) {
            return;
        }

        try {
            DatabaseInitializer initializer = new DatabaseInitializer(dbManager);
            initializer.initialize();
            dbAvailable = true;
            if (consoleLogging) {
                System.out.println("[PERSISTENCE] Base SQLite initialisée avec succès : " + dbManager.getJdbcUrl());
            }
        } catch (SQLException e) {
            dbAvailable = false;
            System.err.println("[PERSISTENCE] Échec initialisation base SQLite: " + e.getMessage());
        }

        running.set(true);
        workerThread = new Thread(this::processQueue, "Persistence-Worker-Thread");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    /**
     * Boucle d'arrière-plan dépilant les écritures sans bloquer les flux temps réel.
     */
    private void processQueue() {
        while (running.get() || !writeQueue.isEmpty()) {
            try {
                Runnable task = writeQueue.poll(200, TimeUnit.MILLISECONDS);
                if (task != null) {
                    try {
                        task.run();
                    } catch (Throwable t) {
                        if (consoleLogging) {
                            System.err.println("[PERSISTENCE] Erreur tâche d'écriture SQLite: " + t.getMessage());
                        }
                    }
                }
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Enfile l'enregistrement d'une télémétrie reçue via UDP Multicast.
     */
    public void saveTelemetry(TelemetryMessage msg) {
        if (msg == null) return;
        saveTelemetry(msg.getTimestamp(), msg.getMachineId(),
                msg.getSensorType() != null ? msg.getSensorType().name() : "UNKNOWN",
                msg.getValue(), msg.getUnit(), msg.getSequenceNumber());
    }

    public void saveTelemetry(long ts, String machineId, String sensorType, double value, String unit, long seq) {
        enqueue(() -> {
            try {
                telemetryRepo.saveTelemetry(ts, machineId, sensorType, value, unit, seq);
            } catch (SQLException e) {
                handleDbError("saveTelemetry", e);
            }
        });
    }

    /**
     * Enfile l'enregistrement d'une alarme reçue via UDP Multicast.
     */
    public void saveAlarm(AlarmMessage alarm) {
        if (alarm == null) return;
        saveAlarm(alarm.getTimestamp(), alarm.getMachineId(),
                alarm.getAlarmLevel() != null ? alarm.getAlarmLevel().name() : "WARNING",
                alarm.getEventType() != null ? alarm.getEventType().name() : "ALARM",
                alarm.getSensorType() != null ? alarm.getSensorType().name() : null,
                alarm.getValue(),
                alarm.getDescription());
    }

    public void saveAlarm(long ts, String machineId, String level, String eventType,
                          String sensorType, Double value, String desc) {
        enqueue(() -> {
            try {
                alarmRepo.saveAlarm(ts, machineId, level, eventType, sensorType, value, desc);
            } catch (SQLException e) {
                handleDbError("saveAlarm", e);
            }
        });
    }

    /**
     * Enfile l'enregistrement d'une commande TCP auditée.
     */
    public void saveCommand(String cmdId, long ts, String machineId, String cmdType, String status, String message) {
        enqueue(() -> {
            try {
                commandRepo.saveCommand(cmdId, ts, machineId, cmdType, status, message);
            } catch (SQLException e) {
                handleDbError("saveCommand", e);
            }
        });
    }

    /**
     * Enfile l'enregistrement d'un événement système.
     */
    public void saveEvent(IndustrialEvent event) {
        if (event == null) return;
        saveEvent(event.getTimestamp(), event.getMachineId(),
                event.getEventType() != null ? event.getEventType().name() : "EVENT",
                event.getLevel() != null ? event.getLevel().name() : "INFO",
                event.getMessage());
    }

    public void saveEvent(long ts, String machineId, String eventType, String level, String desc) {
        enqueue(() -> {
            try {
                eventRepo.saveEvent(ts, machineId, eventType, level, desc);
            } catch (SQLException e) {
                handleDbError("saveEvent", e);
            }
        });
    }

    /**
     * Enfile la mise à jour de l'état d'une machine.
     */
    public void saveMachineState(String machineId, String name, String state, long lastSeen) {
        enqueue(() -> {
            try {
                machineRepo.upsertMachine(machineId, name, "MACHINE", state, lastSeen);
            } catch (SQLException e) {
                handleDbError("saveMachineState", e);
            }
        });
    }

    private void enqueue(Runnable task) {
        if (!running.get() && !dbAvailable) return;
        boolean accepted = writeQueue.offer(task);
        if (!accepted && consoleLogging) {
            System.err.println("[PERSISTENCE] File d'attente d'écriture SQLite saturée !");
        }
    }

    private void handleDbError(String operation, SQLException e) {
        dbAvailable = false;
        if (consoleLogging) {
            System.err.println("[PERSISTENCE] Erreur SQLite (" + operation + "): " + e.getMessage());
        }
    }

    // =========================================================================
    // MÉTHODES DE CONSULTATION (SYNCHRONES EN LECTURE SEULE)
    // =========================================================================

    public List<Map<String, Object>> getRecentTelemetry(String machineId, int limit) {
        try {
            return telemetryRepo.getRecentTelemetry(machineId, limit);
        } catch (SQLException e) {
            handleDbError("getRecentTelemetry", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getRecentAlarms(int limit) {
        try {
            return alarmRepo.getRecentAlarms(limit);
        } catch (SQLException e) {
            handleDbError("getRecentAlarms", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getAlarmsByMachine(String machineId, int limit) {
        try {
            return alarmRepo.getAlarmsByMachine(machineId, limit);
        } catch (SQLException e) {
            handleDbError("getAlarmsByMachine", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getRecentCommands(int limit) {
        try {
            return commandRepo.getRecentCommands(limit);
        } catch (SQLException e) {
            handleDbError("getRecentCommands", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getCommandsByMachine(String machineId, int limit) {
        try {
            return commandRepo.getCommandsByMachine(machineId, limit);
        } catch (SQLException e) {
            handleDbError("getCommandsByMachine", e);
            return Collections.emptyList();
        }
    }

    public List<Map<String, Object>> getRecentEvents(int limit) {
        try {
            return eventRepo.getRecentEvents(limit);
        } catch (SQLException e) {
            handleDbError("getRecentEvents", e);
            return Collections.emptyList();
        }
    }

    /**
     * Reconstruit la chronologie complète d'une machine (commandes, alarmes et événements d'état).
     */
    public List<String> getMachineHistory(String machineId, int limit) {
        List<String> history = new ArrayList<>();
        try {
            List<Map<String, Object>> events = eventRepo.getEventsByMachine(machineId, limit);
            for (Map<String, Object> ev : events) {
                long ts = (Long) ev.get("timestamp");
                String dateStr = timeFormat.format(new Date(ts));
                history.add(String.format("[%s] EVENT: %s - %s", dateStr, ev.get("event_type"), ev.get("description")));
            }

            List<Map<String, Object>> cmds = commandRepo.getCommandsByMachine(machineId, limit);
            for (Map<String, Object> cmd : cmds) {
                long ts = (Long) cmd.get("timestamp");
                String dateStr = timeFormat.format(new Date(ts));
                history.add(String.format("[%s] COMMANDE: %s -> %s (%s)", dateStr, cmd.get("command_type"), cmd.get("status"), cmd.get("message")));
            }

            List<Map<String, Object>> alarms = alarmRepo.getAlarmsByMachine(machineId, limit);
            for (Map<String, Object> al : alarms) {
                long ts = (Long) al.get("timestamp");
                String dateStr = timeFormat.format(new Date(ts));
                history.add(String.format("[%s] ALARME [%s]: %s (%s)", dateStr, al.get("level"), al.get("event_type"), al.get("description")));
            }

            // Tri par chaîne d'horodatage inverse (plus récent en premier)
            Collections.sort(history, Collections.reverseOrder());
            if (history.size() > limit) {
                return history.subList(0, limit);
            }
        } catch (SQLException e) {
            handleDbError("getMachineHistory", e);
        }
        return history;
    }

    /**
     * Retourne une synthèse des compteurs et métriques stockées dans SQLite.
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        try {
            stats.put("totalTelemetry", telemetryRepo.getTotalCount());
            stats.put("totalAlarms", alarmRepo.getTotalCount());
            stats.put("alarmCountsByLevel", alarmRepo.getAlarmCountsByLevel());
            stats.put("totalCommands", commandRepo.getTotalCount());
            stats.put("commandCountsByStatus", commandRepo.getCommandCountsByStatus());
            stats.put("totalEvents", eventRepo.getTotalCount());
        } catch (SQLException e) {
            handleDbError("getStatistics", e);
        }
        return stats;
    }

    /**
     * Bloque jusqu'à ce que toutes les opérations en attente dans la file soient écrites.
     */
    public boolean flush(long timeoutMs) {
        if (workerThread == null || !workerThread.isAlive()) {
            return true;
        }

        CountDownLatch latch = new CountDownLatch(1);
        enqueue(latch::countDown);

        try {
            return latch.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public synchronized void stop() {
        if (!running.get()) {
            return;
        }

        // On vide la file avant d'arrêter le worker
        flush(3000);

        running.set(false);

        if (workerThread != null) {
            workerThread.interrupt();
            try {
                workerThread.join(2000);
            } catch (InterruptedException ignored) {}
        }

        if (dbManager != null) {
            dbManager.close();
        }

        if (consoleLogging) {
            System.out.println("[PERSISTENCE] Service de persistance SQLite arrêté.");
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isAvailable() {
        return dbAvailable;
    }

    public DatabaseManager getDbManager() {
        return dbManager;
    }

    public void setConsoleLogging(boolean consoleLogging) {
        this.consoleLogging = consoleLogging;
    }
}
