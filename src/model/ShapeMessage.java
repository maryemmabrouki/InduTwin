package model;

import java.awt.Color;

/**
 * Message réseau concret transportant une forme géométrique.
 * Respecte le format textuel du protocole TP : shape,x,y,rgb
 */
public class ShapeMessage implements NetworkMessage {

    private final ShapeData shapeData;
    private final long timestamp;

    public ShapeMessage(ShapeData shapeData) {
        this.shapeData = shapeData;
        this.timestamp = System.currentTimeMillis();
    }

    public ShapeMessage(String type, int x, int y, Color color) {
        this(new ShapeData(type, x, y, color));
    }

    @Override
    public String getMessageType() {
        return "SHAPE";
    }

    @Override
    public long getTimestamp() {
        return timestamp;
    }

    public ShapeData getShapeData() {
        return shapeData;
    }

    @Override
    public String serialize() {
        return shapeData.getType() + "," +
                shapeData.getX() + "," +
                shapeData.getY() + "," +
                shapeData.getColor().getRGB();
    }

    @Override
    public String toString() {
        return "ShapeMessage{" +
                "shapeData=" + shapeData +
                ", timestamp=" + timestamp +
                '}';
    }
}
