package network;

import model.AlarmMessage;
import model.HeartbeatMessage;
import model.NetworkMessage;
import model.ShapeData;
import model.ShapeMessage;
import model.TelemetryMessage;
import model.industrial.AlarmLevel;
import model.industrial.EventType;
import model.industrial.MachineState;
import model.industrial.SensorType;

import java.awt.Color;

/**
 * Responsable de la sérialisation et de la désérialisation sécurisée des trames réseau.
 * Prend en charge à la fois les formes graphiques (Phase 1) et la télémétrie industrielle (Phase 2).
 */
public final class MessageParser {

    private MessageParser() {
        // Classe utilitaire
    }

    /**
     * Décode une chaîne brute reçue par UDP en un objet NetworkMessage.
     * Formats supportés :
     * 1. TELEMETRY,machineId,sensorType,value,unit,sequenceNumber,timestamp
     * 2. HEARTBEAT,machineId,state,sequenceNumber,timestamp
     * 3. ALARM,machineId,level,eventType,sensorType,value,message,sequenceNumber,timestamp
     * 4. shape,x,y,rgb (ou SHAPE,shape,x,y,rgb)
     *
     * @param rawPayload Le contenu textuel du paquet UDP
     * @return L'instance NetworkMessage décodée, ou null si la trame est invalide
     */
    public static NetworkMessage decode(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            return null;
        }

        String trimmed = rawPayload.trim();
        String[] parts = trimmed.split(",");

        String header = parts[0].trim().toUpperCase();

        try {
            switch (header) {
                case "TELEMETRY":
                    if (parts.length >= 7) {
                        String machineId = parts[1].trim();
                        SensorType sensorType = SensorType.valueOf(parts[2].trim().toUpperCase());
                        double value = Double.parseDouble(parts[3].trim());
                        String unit = parts[4].trim();
                        long seq = Long.parseLong(parts[5].trim());
                        long timestamp = Long.parseLong(parts[6].trim());
                        return new TelemetryMessage(machineId, sensorType, value, unit, seq, timestamp);
                    }
                    break;

                case "HEARTBEAT":
                    if (parts.length >= 5) {
                        String machineId = parts[1].trim();
                        MachineState state = MachineState.valueOf(parts[2].trim().toUpperCase());
                        long seq = Long.parseLong(parts[3].trim());
                        long timestamp = Long.parseLong(parts[4].trim());
                        return new HeartbeatMessage(machineId, state, seq, timestamp);
                    }
                    break;

                case "ALARM":
                    if (parts.length >= 9) {
                        String machineId = parts[1].trim();
                        AlarmLevel level = AlarmLevel.valueOf(parts[2].trim().toUpperCase());
                        EventType eventType = EventType.valueOf(parts[3].trim().toUpperCase());
                        SensorType sensorType = SensorType.valueOf(parts[4].trim().toUpperCase());
                        double value = Double.parseDouble(parts[5].trim());
                        String desc = parts[6].trim();
                        long seq = Long.parseLong(parts[7].trim());
                        long timestamp = Long.parseLong(parts[8].trim());
                        return new AlarmMessage(machineId, level, eventType, sensorType, value, desc, seq, timestamp);
                    }
                    break;

                case "SHAPE":
                    if (parts.length == 5) {
                        String shapeType = parts[1].trim().toLowerCase();
                        int x = Integer.parseInt(parts[2].trim());
                        int y = Integer.parseInt(parts[3].trim());
                        int rgb = Integer.parseInt(parts[4].trim());
                        return new ShapeMessage(new ShapeData(shapeType, x, y, new Color(rgb)));
                    }
                    break;

                default:
                    // Compatibilité avec le protocole TP Phase 1 : shape,x,y,rgb
                    if (parts.length == 4) {
                        String shapeType = parts[0].trim().toLowerCase();
                        if (isShapeType(shapeType)) {
                            int x = Integer.parseInt(parts[1].trim());
                            int y = Integer.parseInt(parts[2].trim());
                            int rgb = Integer.parseInt(parts[3].trim());
                            return new ShapeMessage(new ShapeData(shapeType, x, y, new Color(rgb)));
                        }
                    }
                    break;
            }
        } catch (IllegalArgumentException e) {
            System.err.println("[MessageParser] Valeur d'énumération ou format numérique invalide: " + trimmed);
            return null;
        } catch (Exception e) {
            System.err.println("[MessageParser] Erreur inattendue au décodage: " + trimmed + " (" + e.getMessage() + ")");
            return null;
        }

        System.err.println("[MessageParser] Format de message non reconnu: " + trimmed);
        return null;
    }

    private static boolean isShapeType(String type) {
        return "circle".equals(type) || "square".equals(type) || "triangle".equals(type) || "diamond".equals(type);
    }

    /**
     * Encode un objet NetworkMessage en chaîne de caractères.
     */
    public static String encode(NetworkMessage message) {
        if (message == null) {
            return "";
        }
        return message.serialize();
    }
}
