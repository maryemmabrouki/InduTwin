package control;

/**
 * Réponse d'accusé de réception (ACK/NACK) retournée par le serveur TCP.
 * Format de trame textuelle : ACK,commandId,machineId,commandType,status,message,timestamp
 */
public class ControlResponse {

    private final String commandId;
    private final String machineId;
    private final ControlCommandType commandType;
    private final ControlStatus status;
    private final String message;
    private final long timestamp;

    public ControlResponse(String commandId, String machineId, ControlCommandType commandType,
                           ControlStatus status, String message, long timestamp) {
        this.commandId = commandId != null ? commandId.trim() : "";
        this.machineId = machineId != null ? machineId.trim() : "";
        this.commandType = commandType;
        this.status = status != null ? status : ControlStatus.INVALID;
        this.message = message != null ? message.replace(",", ";") : "";
        this.timestamp = timestamp;
    }

    public static ControlResponse accepted(ControlCommand cmd, String message) {
        return new ControlResponse(
                cmd.getCommandId(),
                cmd.getMachineId(),
                cmd.getCommandType(),
                ControlStatus.ACCEPTED,
                message,
                System.currentTimeMillis()
        );
    }

    public static ControlResponse rejected(ControlCommand cmd, String reason) {
        return new ControlResponse(
                cmd.getCommandId(),
                cmd.getMachineId(),
                cmd.getCommandType(),
                ControlStatus.REJECTED,
                reason,
                System.currentTimeMillis()
        );
    }

    public static ControlResponse invalid(String commandId, String message) {
        return new ControlResponse(
                commandId,
                "",
                null,
                ControlStatus.INVALID,
                message,
                System.currentTimeMillis()
        );
    }

    public static ControlResponse timeout(ControlCommand cmd) {
        return new ControlResponse(
                cmd != null ? cmd.getCommandId() : "",
                cmd != null ? cmd.getMachineId() : "",
                cmd != null ? cmd.getCommandType() : null,
                ControlStatus.TIMEOUT,
                "Délai d'attente dépassé (TCP Socket Timeout)",
                System.currentTimeMillis()
        );
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

    public ControlStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String serialize() {
        String typeStr = commandType != null ? commandType.name() : "NONE";
        return "ACK," +
                commandId + "," +
                machineId + "," +
                typeStr + "," +
                status.name() + "," +
                message + "," +
                timestamp;
    }

    public static ControlResponse parse(String rawLine) {
        if (rawLine == null || rawLine.isBlank()) {
            return null;
        }

        String[] parts = rawLine.trim().split(",");
        if (parts.length >= 7 && "ACK".equalsIgnoreCase(parts[0].trim())) {
            try {
                String cmdId = parts[1].trim();
                String machineId = parts[2].trim();
                String typeStr = parts[3].trim();
                ControlCommandType type = "NONE".equalsIgnoreCase(typeStr) ? null : ControlCommandType.valueOf(typeStr.toUpperCase());
                ControlStatus status = ControlStatus.valueOf(parts[4].trim().toUpperCase());
                String msg = parts[5].trim();
                long ts = Long.parseLong(parts[6].trim());
                return new ControlResponse(cmdId, machineId, type, status, msg, ts);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("ControlResponse[%s | %s %s -> %s : %s]",
                commandId, machineId, commandType, status, message);
    }
}
