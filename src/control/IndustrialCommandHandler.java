package control;

import industrial.MachineSimulator;
import model.AlarmMessage;
import model.HeartbeatMessage;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.Machine;
import model.industrial.MachineState;
import network.MulticastManager;
import persistence.PersistenceService;

import java.io.IOException;

/**
 * Implémentation du gestionnaire de commandes industrielles TCP.
 * Centralise les règles de validation d'état (state machine), exécute les transitions,
 * persiste l'audit des commandes dans SQLite et déclenche l'arrêt d'urgence avec diffusion d'alarme UDP Multicast.
 */
public class IndustrialCommandHandler implements ControlCommandHandler {

    private final MachineSimulator simulator;
    private PersistenceService persistenceService;

    public IndustrialCommandHandler(MachineSimulator simulator) {
        this(simulator, null);
    }

    public IndustrialCommandHandler(MachineSimulator simulator, PersistenceService persistenceService) {
        this.simulator = simulator;
        this.persistenceService = persistenceService;
    }

    public PersistenceService getPersistenceService() {
        return persistenceService;
    }

    public void setPersistenceService(PersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @Override
    public ControlResponse handleCommand(ControlCommand command) {
        ControlResponse response = processCommand(command);

        // Audit persistant de chaque commande exécutée ou rejetée
        if (persistenceService != null && command != null && response != null) {
            persistenceService.saveCommand(
                    command.getCommandId(),
                    command.getTimestamp(),
                    command.getMachineId(),
                    command.getCommandType() != null ? command.getCommandType().name() : "UNKNOWN",
                    response.getStatus() != null ? response.getStatus().name() : "UNKNOWN",
                    response.getMessage()
            );
        }

        return response;
    }

    private ControlResponse processCommand(ControlCommand command) {
        if (command == null || command.getCommandType() == null) {
            return ControlResponse.invalid(
                    command != null ? command.getCommandId() : "cmd-null",
                    "Commande nulle ou malformée"
            );
        }

        if (simulator == null) {
            return ControlResponse.rejected(command, "SIMULATOR_UNAVAILABLE");
        }

        String machineId = command.getMachineId();
        Machine machine = simulator.getMachine(machineId);

        if (machine == null) {
            return ControlResponse.rejected(command, "MACHINE_NOT_FOUND: " + machineId);
        }

        ControlCommandType type = command.getCommandType();
        MachineState currentState = machine.getState();

        switch (type) {
            case START:
                return handleStart(command, machine, currentState);

            case STOP:
                return handleStop(command, machine, currentState);

            case EMERGENCY_STOP:
                return handleEmergencyStop(command, machine, currentState);

            case RESET:
                return handleReset(command, machine, currentState);

            case MAINTENANCE:
                return handleMaintenance(command, machine, currentState);

            case RESUME:
                return handleResume(command, machine, currentState);

            default:
                return ControlResponse.invalid(command.getCommandId(), "COMMAND_NOT_SUPPORTED");
        }
    }

    private ControlResponse handleStart(ControlCommand command, Machine machine, MachineState currentState) {
        if (currentState == MachineState.STOPPED) {
            boolean ok = machine.transitionTo(MachineState.RUNNING);
            if (ok) {
                broadcastStateUpdate(machine);
                return ControlResponse.accepted(command, "MACHINE_STARTED");
            } else {
                return ControlResponse.rejected(command, "TRANSITION_FAILED");
            }
        } else if (currentState == MachineState.RUNNING || currentState == MachineState.STARTING
                || currentState == MachineState.WARNING) {
            return ControlResponse.rejected(command, "MACHINE_ALREADY_RUNNING (" + currentState + ")");
        } else if (currentState == MachineState.FAILURE || currentState == MachineState.CRITICAL) {
            return ControlResponse.rejected(command, "MACHINE_IN_FAILURE (" + currentState + ")");
        } else if (currentState == MachineState.MAINTENANCE) {
            return ControlResponse.rejected(command, "MACHINE_IN_MAINTENANCE");
        } else if (currentState == MachineState.OFFLINE) {
            return ControlResponse.rejected(command, "MACHINE_OFFLINE");
        }
        return ControlResponse.rejected(command, "INCOMPATIBLE_STATE_FOR_START: " + currentState);
    }

    private ControlResponse handleStop(ControlCommand command, Machine machine, MachineState currentState) {
        if (currentState == MachineState.RUNNING || currentState == MachineState.WARNING
                || currentState == MachineState.STARTING || currentState == MachineState.CRITICAL) {
            boolean ok = machine.transitionTo(MachineState.STOPPED);
            if (ok) {
                broadcastStateUpdate(machine);
                return ControlResponse.accepted(command, "MACHINE_STOPPED");
            } else {
                return ControlResponse.rejected(command, "TRANSITION_FAILED");
            }
        } else if (currentState == MachineState.STOPPED) {
            return ControlResponse.rejected(command, "MACHINE_ALREADY_STOPPED");
        } else if (currentState == MachineState.MAINTENANCE) {
            return ControlResponse.rejected(command, "MACHINE_IN_MAINTENANCE");
        } else if (currentState == MachineState.OFFLINE) {
            return ControlResponse.rejected(command, "MACHINE_OFFLINE");
        }
        return ControlResponse.rejected(command, "INCOMPATIBLE_STATE_FOR_STOP: " + currentState);
    }

    private ControlResponse handleEmergencyStop(ControlCommand command, Machine machine, MachineState currentState) {
        // L'arrêt d'urgence force immédiatement la machine en état FAILURE
        machine.transitionTo(MachineState.FAILURE);
        broadcastStateUpdate(machine);

        // Émission simultanée d'une alarme critique sur le bus UDP Multicast
        MulticastManager multicast = simulator.getMulticastManager();
        if (multicast != null && multicast.isRunning()) {
            AlarmMessage alarm = new AlarmMessage(
                    machine.getId(),
                    AlarmLevel.CRITICAL,
                    EventType.EMERGENCY_STOP,
                    null,
                    0.0,
                    "ARRET D'URGENCE DECLENCHE VIA TCP",
                    System.currentTimeMillis(),
                    System.currentTimeMillis()
            );
            try {
                multicast.send(alarm);
            } catch (IOException e) {
                System.err.println("[IndustrialCommandHandler] Erreur émission alarme d'urgence UDP: " + e.getMessage());
            }
        }

        return ControlResponse.accepted(command, "EMERGENCY_STOP_TRIGGERED");
    }

    private ControlResponse handleReset(ControlCommand command, Machine machine, MachineState currentState) {
        if (currentState == MachineState.FAILURE || currentState == MachineState.CRITICAL
                || currentState == MachineState.STOPPED) {
            // Rétablissement des capteurs et levée des pannes
            if (simulator.getFaultInjector() != null) {
                simulator.getFaultInjector().restoreNormalOperation(machine);
            }
            machine.transitionTo(MachineState.STOPPED);
            broadcastStateUpdate(machine);
            return ControlResponse.accepted(command, "MACHINE_RESET_OK");
        } else if (currentState == MachineState.RUNNING || currentState == MachineState.WARNING) {
            return ControlResponse.rejected(command, "CANNOT_RESET_RUNNING_MACHINE");
        } else if (currentState == MachineState.MAINTENANCE) {
            return ControlResponse.rejected(command, "MACHINE_IN_MAINTENANCE");
        } else if (currentState == MachineState.OFFLINE) {
            return ControlResponse.rejected(command, "MACHINE_OFFLINE");
        }
        return ControlResponse.rejected(command, "INCOMPATIBLE_STATE_FOR_RESET: " + currentState);
    }

    private ControlResponse handleMaintenance(ControlCommand command, Machine machine, MachineState currentState) {
        if (currentState == MachineState.STOPPED || currentState == MachineState.FAILURE) {
            boolean ok = machine.transitionTo(MachineState.MAINTENANCE);
            if (ok) {
                broadcastStateUpdate(machine);
                return ControlResponse.accepted(command, "MAINTENANCE_MODE_ACTIVATED");
            } else {
                return ControlResponse.rejected(command, "TRANSITION_FAILED");
            }
        } else if (currentState == MachineState.MAINTENANCE) {
            return ControlResponse.rejected(command, "ALREADY_IN_MAINTENANCE");
        } else if (currentState == MachineState.RUNNING || currentState == MachineState.WARNING
                || currentState == MachineState.CRITICAL) {
            return ControlResponse.rejected(command, "MUST_STOP_MACHINE_BEFORE_MAINTENANCE");
        } else if (currentState == MachineState.OFFLINE) {
            return ControlResponse.rejected(command, "MACHINE_OFFLINE");
        }
        return ControlResponse.rejected(command, "INCOMPATIBLE_STATE_FOR_MAINTENANCE: " + currentState);
    }

    private ControlResponse handleResume(ControlCommand command, Machine machine, MachineState currentState) {
        if (currentState == MachineState.MAINTENANCE) {
            boolean ok = machine.transitionTo(MachineState.STOPPED);
            if (ok) {
                broadcastStateUpdate(machine);
                return ControlResponse.accepted(command, "MAINTENANCE_COMPLETED");
            } else {
                return ControlResponse.rejected(command, "TRANSITION_FAILED");
            }
        }
        return ControlResponse.rejected(command, "MACHINE_NOT_IN_MAINTENANCE (" + currentState + ")");
    }

    private void broadcastStateUpdate(Machine machine) {
        if (simulator != null && simulator.getMulticastManager() != null && simulator.getMulticastManager().isRunning()) {
            HeartbeatMessage hb = new HeartbeatMessage(
                    machine.getId(),
                    machine.getState(),
                    System.currentTimeMillis()
            );
            try {
                simulator.getMulticastManager().send(hb);
            } catch (IOException e) {
                System.err.println("[IndustrialCommandHandler] Erreur émission heartbeat suite à transition: " + e.getMessage());
            }
        }
    }
}
