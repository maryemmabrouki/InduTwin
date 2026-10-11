package rmi;

import industrial.IndustrialSupervisor;
import model.industrial.AlarmLevel;
import model.industrial.IndustrialEvent;
import monitoring.NetworkStatistics;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implémentation du service RMI de supervision distante.
 * Accède directement à l'état centralisé de l'IndustrialSupervisor sans duplication.
 */
public class RemoteMonitoringServiceImpl extends UnicastRemoteObject implements RemoteMonitoringService {

    private static final long serialVersionUID = 1L;

    private final IndustrialSupervisor supervisor;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    public RemoteMonitoringServiceImpl(IndustrialSupervisor supervisor) throws RemoteException {
        super(0); // Exporte sur un port anonyme disponible
        this.supervisor = supervisor;
    }

    @Override
    public String getMachineStatus(String machineId) throws RemoteException {
        if (machineId == null || supervisor == null) {
            return "UNKNOWN";
        }
        IndustrialSupervisor.MachineSupervisionInfo info = supervisor.getMachineInfo(machineId.trim().toUpperCase());
        if (info != null && info.getState() != null) {
            return info.getState().name();
        }
        return "UNKNOWN";
    }

    @Override
    public Map<String, String> getAllMachineStatuses() throws RemoteException {
        Map<String, String> result = new LinkedHashMap<>();
        if (supervisor != null) {
            for (Map.Entry<String, IndustrialSupervisor.MachineSupervisionInfo> entry : supervisor.getMachines().entrySet()) {
                String state = entry.getValue().getState() != null ? entry.getValue().getState().name() : "UNKNOWN";
                result.put(entry.getKey(), state);
            }
        }
        return result;
    }

    @Override
    public List<String> getActiveAlarms() throws RemoteException {
        List<String> alarms = new ArrayList<>();
        if (supervisor != null) {
            List<IndustrialEvent> events = supervisor.getEventHistory();
            for (IndustrialEvent event : events) {
                if (event.getLevel() == AlarmLevel.WARNING || event.getLevel() == AlarmLevel.CRITICAL) {
                    alarms.add(String.format("[%s] [%s] %s (%s)",
                            dateFormat.format(new Date(event.getTimestamp())),
                            event.getLevel(),
                            event.getMachineId(),
                            event.getMessage()));
                }
            }
        }
        return alarms;
    }

    @Override
    public Map<String, Object> getNetworkStatistics() throws RemoteException {
        Map<String, Object> statsMap = new LinkedHashMap<>();
        if (supervisor != null && supervisor.getNetworkMonitor() != null) {
            NetworkStatistics stats = supervisor.getNetworkMonitor().getSnapshot();
            statsMap.put("packetsPerSecond", stats.getPacketsPerSecond());
            statsMap.put("throughputKbPerSec", stats.getThroughputKbPerSec());
            statsMap.put("healthStatus", stats.getHealthStatus());
            statsMap.put("potentialPacketLoss", stats.getPotentialPacketLoss());
            statsMap.put("totalPacketsReceived", stats.getTotalPacketsReceived());
            statsMap.put("averageLatencyMs", stats.getAverageLatencyMs());
            statsMap.put("timestamp", stats.getTimestamp());
        }
        return statsMap;
    }

    @Override
    public List<String> getRecentEvents(int limit) throws RemoteException {
        List<String> result = new ArrayList<>();
        if (supervisor != null) {
            List<IndustrialEvent> events = supervisor.getEventHistory();
            int start = Math.max(0, events.size() - Math.max(1, limit));
            for (int i = start; i < events.size(); i++) {
                result.add(events.get(i).toString());
            }
        }
        return result;
    }

    @Override
    public List<String> getHistoricAlarms(int limit) throws RemoteException {
        List<String> result = new ArrayList<>();
        if (supervisor != null && supervisor.getPersistenceService() != null) {
            List<Map<String, Object>> alarms = supervisor.getPersistenceService().getRecentAlarms(limit);
            for (Map<String, Object> a : alarms) {
                long ts = (Long) a.get("timestamp");
                result.add(String.format("[%s] [%s] %s - %s (%s)",
                        dateFormat.format(new Date(ts)),
                        a.get("level"),
                        a.get("machine_id"),
                        a.get("event_type"),
                        a.get("description")));
            }
        }
        return result;
    }

    @Override
    public List<String> getRecentCommands(int limit) throws RemoteException {
        List<String> result = new ArrayList<>();
        if (supervisor != null && supervisor.getPersistenceService() != null) {
            List<Map<String, Object>> cmds = supervisor.getPersistenceService().getRecentCommands(limit);
            for (Map<String, Object> c : cmds) {
                long ts = (Long) c.get("timestamp");
                result.add(String.format("[%s] [%s] %s -> %s : %s (%s)",
                        dateFormat.format(new Date(ts)),
                        c.get("command_id"),
                        c.get("machine_id"),
                        c.get("command_type"),
                        c.get("status"),
                        c.get("message")));
            }
        }
        return result;
    }

    @Override
    public List<String> getMachineHistory(String machineId, int limit) throws RemoteException {
        if (supervisor != null && supervisor.getPersistenceService() != null) {
            return supervisor.getPersistenceService().getMachineHistory(machineId, limit);
        }
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> getDatabaseStatistics() throws RemoteException {
        if (supervisor != null && supervisor.getPersistenceService() != null) {
            return supervisor.getPersistenceService().getStatistics();
        }
        return Collections.emptyMap();
    }
}
