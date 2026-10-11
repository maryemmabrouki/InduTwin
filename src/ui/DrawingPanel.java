package ui;

import model.ShapeData;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/**
 * Panneau de dessin Swing autonome et thread-safe.
 * Totalement découplé de la couche réseau (ignore UDP, sockets et protocoles).
 */
public class DrawingPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private final List<ShapeData> shapes = new ArrayList<>();

    public DrawingPanel() {
        setBackground(Color.WHITE);
    }

    /**
     * Ajoute une forme et déclenche le réaffichage.
     */
    public synchronized void addShape(ShapeData s) {
        if (s != null) {
            shapes.add(s);
            repaint();
        }
    }

    /**
     * Réinitialise le canevas de dessin.
     */
    public synchronized void clearShapes() {
        shapes.clear();
        repaint();
    }

    /**
     * Retourne le nombre total de formes actuellement sur le canevas.
     */
    public synchronized int getShapesCount() {
        return shapes.size();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        synchronized (this) {
            for (ShapeData s : shapes) {
                g2d.setColor(s.getColor());

                switch (s.getType()) {
                    case "circle":
                        g2d.fillOval(s.getX(), s.getY(), 50, 50);
                        break;
                    case "square":
                        g2d.fillRect(s.getX(), s.getY(), 50, 50);
                        break;
                    case "triangle":
                        int[] xTri = {s.getX() + 25, s.getX(), s.getX() + 50};
                        int[] yTri = {s.getY(), s.getY() + 50, s.getY() + 50};
                        g2d.fillPolygon(xTri, yTri, 3);
                        break;
                    case "diamond":
                        int[] xDia = {s.getX() + 25, s.getX() + 50, s.getX() + 25, s.getX()};
                        int[] yDia = {s.getY(), s.getY() + 25, s.getY() + 50, s.getY() + 25};
                        g2d.fillPolygon(xDia, yDia, 4);
                        break;
                    default:
                        // Forme par défaut si type non reconnu
                        g2d.fillOval(s.getX(), s.getY(), 50, 50);
                        break;
                }
            }
        }
    }
}
