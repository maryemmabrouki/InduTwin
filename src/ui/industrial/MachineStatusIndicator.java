package ui.industrial;

import model.industrial.MachineState;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Composant visuel Swing sous forme de badge d'état industriel (voyant LED + texte).
 */
public class MachineStatusIndicator extends JPanel {

    private static final long serialVersionUID = 1L;

    private MachineState state;
    private static final Font BADGE_FONT = new Font("SansSerif", Font.BOLD, 12);

    public MachineStatusIndicator(MachineState initialState) {
        this.state = initialState != null ? initialState : MachineState.STOPPED;
        setOpaque(false);
        setPreferredSize(new Dimension(115, 26));
    }

    public synchronized void updateState(MachineState newState) {
        if (this.state != newState) {
            this.state = newState != null ? newState : MachineState.STOPPED;
            repaint();
        }
    }

    public synchronized MachineState getState() {
        return state;
    }

    public static Color getStateColor(MachineState state) {
        if (state == null) return new Color(148, 163, 184); // Gray
        switch (state) {
            case RUNNING:
                return new Color(34, 197, 94);   // Vert vif
            case WARNING:
                return new Color(245, 158, 11);  // Orange / Ambre
            case CRITICAL:
                return new Color(239, 68, 68);   // Rouge
            case FAILURE:
                return new Color(153, 27, 27);   // Rouge bordeaux
            case MAINTENANCE:
                return new Color(234, 179, 8);   // Jaune
            case STARTING:
                return new Color(59, 130, 246);  // Bleu
            case OFFLINE:
                return new Color(100, 116, 139); // Gris ardoise
            case STOPPED:
            default:
                return new Color(148, 163, 184); // Gris
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Color baseColor = getStateColor(state);

        // Fond arrondi du badge
        Color bgColor = new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 35);
        g2d.setColor(bgColor);
        g2d.fillRoundRect(0, 1, getWidth() - 1, getHeight() - 3, 14, 14);

        // Bordure du badge
        g2d.setColor(new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), 160));
        g2d.drawRoundRect(0, 1, getWidth() - 1, getHeight() - 3, 14, 14);

        // Voyant LED circulaire
        int circleD = 10;
        int circleY = (getHeight() - circleD) / 2;
        g2d.setColor(baseColor);
        g2d.fillOval(8, circleY, circleD, circleD);

        // Texte de l'état
        g2d.setFont(BADGE_FONT);
        g2d.setColor(new Color(30, 41, 59));
        String text = state.name();
        g2d.drawString(text, 24, getHeight() / 2 + 5);
    }
}
