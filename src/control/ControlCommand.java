package control;

/**
 * Représentation d'une commande industrielle envoyée via le canal TCP point-à-point.
 * Format de trame textuelle : CMD,commandId,machineId,commandType,timestamp
 */
public class ControlCommand {

    private final String commandId;
    private final String machineId;
    private final ControlCommandType commandType;
    private final long timestamp;

    public ControlCommand(String commandId, String machineId, ControlCommandType commandType, long timestamp) {
        this.commandId = commandId != null ? commandId.trim() : "cmd-" + System.nanoTime();
        this.machineId = machineId != null ? machineId.trim().toUpperCase() : "";
        this.commandType = commandType;
        this.timestamp = timestamp;
    }

    public ControlCommand(String commandId, String machineId, ControlCommandType commandType) {
        this(commandId, machineId, commandType, System.currentTimeMillis());
    }

    public String getCommandId() {
        return commandId;
    }

    public String getMachineId() {
        return machineId;
    }

    public ControlCommandType getCommandType() {
        return commandType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String serialize() {
        return "CMD," +
                commandId + "," +
                machineId + "," +
                commandType.name() + "," +
                timestamp;
    }

    public static ControlCommand parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return null;
        }

        String[] parts = rawLine.trim().split(",");
        if (parts.length >= 5 && "CMD".equalsIgnoreCase(parts[0].trim())) {
            try {
                String cmdId = parts[1].trim();
                String machineId = parts[2].trim();
                ControlCommandType type = ControlCommandType.valueOf(parts[3].trim().toUpperCase());
                long ts = Long.parseLong(parts[4].trim());
                return new ControlCommand(cmdId, machineId, type, ts);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("ControlCommand[%s | %s -> %s]", commandId, machineId, commandType);
    }
}
