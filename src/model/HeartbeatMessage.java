package model;

import model.industrial.MachineState;

/**
 * Message réseau périodique de présence (Heartbeat / Battement de cœur).
 * Format de sérialisation : HEARTBEAT,machineId,machineState,sequenceNumber,timestamp
 */
public class HeartbeatMessage implements NetworkMessage {

    private final String machineId;
    private final MachineState state;
    private final long sequenceNumber;
    private final long timestamp;

    public HeartbeatMessage(String machineId, MachineState state, long sequenceNumber, long timestamp) {
        this.machineId = machineId;
        this.state = state != null ? state : MachineState.RUNNING;
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
    }

    public HeartbeatMessage(String machineId, MachineState state, long sequenceNumber) {
        this(machineId, state, sequenceNumber, System.currentTimeMillis());
    }

    @Override
    public String getMessageType() {
        return "HEARTBEAT";
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public String getMachineId() {
        return machineId;
    }

    public MachineState getState() {
        return state;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String serialize() {
        return "HEARTBEAT," +
                machineId + "," +
                state.name() + "," +
                sequenceNumber + "," +
                timestamp;
    }

    @Override
    public String toString() {
        return String.format("[HEARTBEAT #%d] [%s] Statut: %s", sequenceNumber, machineId, state);
    }
}
