package model;

import java.awt.Color;

/**
 * Modèle de données représentant une forme géométrique à afficher sur le canevas Swing.
 * Classe indépendante de toute logique réseau.
 */
public class ShapeData {

    private final String type;
    private final int x;
    private final int y;
    private final Color color;

    public ShapeData(String type, int x, int y, Color color) {
        this.type = type != null ? type.toLowerCase() : "circle";
        this.x = x;
        this.y = y;
        this.color = color != null ? color : Color.BLACK;
    }

    public String getType() {
        return type;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public Color getColor() {
        return color;
    }

    @Override
    public String toString() {
        return "ShapeData{" +
                "type='" + type + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", color=" + color +
                '}';
    }
}
