package ui.industrial.components;

import model.industrial.AlarmLevel;
import model.industrial.MachineState;
import ui.industrial.theme.UITheme;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Composant Swing d'affichage d'état sous forme de badge industriel moderne (pilule avec voyant LED).
 * Intègre le système de design {@link UITheme} pour garantir un rendu haute lisibilité sur fond sombre.
 * <p>
 * Prend en charge les états des machines {@link MachineState}, les niveaux d'alarme {@link AlarmLevel}
 * ou des libellés personnalisés avec couleur d'accentuation dédiée.
 */
public class ModernBadge extends JPanel {

    private static final long serialVersionUID = 1L;

    private String text;
    private Color accentColor;
    private boolean showIndicator = true;

    /**
     * Crée un badge avec l'état initial par défaut (STOPPED).
     */
    public ModernBadge() {
        this(MachineState.STOPPED);
    }

    /**
     * Crée un badge configuré pour un état machine spécifique.
     *
     * @param state État opérationnel de la machine
     */
    public ModernBadge(MachineState state) {
        setOpaque(false);
        setState(state);
    }

    /**
     * Crée un badge configuré pour un niveau de gravité d'alarme.
     *
     * @param level Niveau de sévérité de l'alarme
     */
    public ModernBadge(AlarmLevel level) {
        setOpaque(false);
        setAlarmLevel(level);
    }

    /**
     * Crée un badge avec un texte personnalisé et une couleur d'accentuation.
     *
     * @param text        Libellé du badge
     * @param accentColor Couleur sémantique de l'indicateur et du halo
     */
    public ModernBadge(String text, Color accentColor) {
        setOpaque(false);
        setCustom(text, accentColor);
    }

    /**
     * Met à jour le badge selon l'état machine spécifié.
     */
    public void setState(MachineState state) {
        String newText = state != null ? state.name() : "STOPPED";
        Color newColor = UITheme.getColorForState(state);
        updateContent(newText, newColor);
    }

    /**
     * Met à jour le badge selon le niveau d'alarme spécifié.
     */
    public void setAlarmLevel(AlarmLevel level) {
        String newText = level != null ? level.name() : "NONE";
        Color newColor = UITheme.getColorForAlarmLevel(level);
        updateContent(newText, newColor);
    }

    /**
     * Met à jour le badge avec un libellé libre et une couleur personnalisée.
     */
    public void setCustom(String text, Color accentColor) {
        String newText = text != null ? text : "";
        Color newColor = accentColor != null ? accentColor : UITheme.STATUS_STOPPED;
        updateContent(newText, newColor);
    }

    /**
     * Active ou masque le voyant circulaire LED.
     */
    public void setShowIndicator(boolean showIndicator) {
        if (this.showIndicator != showIndicator) {
            this.showIndicator = showIndicator;
            safeRepaint();
        }
    }

    private synchronized void updateContent(String newText, Color newColor) {
        if (!newText.equals(this.text) || !newColor.equals(this.accentColor)) {
            this.text = newText;
            this.accentColor = newColor;
            safeRepaint();
        }
    }

    private void safeRepaint() {
        if (SwingUtilities.isEventDispatchThread()) {
            revalidate();
            repaint();
        } else {
            SwingUtilities.invokeLater(() -> {
                revalidate();
                repaint();
            });
        }
    }

    public synchronized String getText() {
        return text;
    }

    public synchronized Color getAccentColor() {
        return accentColor;
    }

    public boolean isShowIndicator() {
        return showIndicator;
    }

    @Override
    public Dimension getPreferredSize() {
        FontMetrics fm = getFontMetrics(UITheme.FONT_BADGE);
        int textWidth = fm.stringWidth(text != null ? text : "");
        int paddingLeft = 10;
        int indicatorSpace = showIndicator ? (8 + 6) : 0;
        int paddingRight = 10;
        int width = paddingLeft + indicatorSpace + textWidth + paddingRight;
        int height = Math.max(24, fm.getHeight() + 6);
        return new Dimension(Math.max(64, width), height);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int radius = UITheme.RADIUS_BADGE;

            Color base = accentColor != null ? accentColor : UITheme.STATUS_STOPPED;

            // 1. Fond teinté translucide (effet pilule moderne)
            Color bgTint = UITheme.withAlpha(base, 32);
            g2.setColor(bgTint);
            g2.fillRoundRect(0, 0, w - 1, h - 1, radius, radius);

            // 2. Bordure subtile
            Color border = UITheme.withAlpha(base, 110);
            g2.setColor(border);
            g2.drawRoundRect(0, 0, w - 1, h - 1, radius, radius);

            // 3. Indicateur LED circulaire (avec halo lumineux)
            int currentX = 10;
            if (showIndicator) {
                int dotSize = 8;
                int dotY = (h - dotSize) / 2;

                // Halo lumineux
                Color halo = UITheme.withAlpha(base, 65);
                g2.setColor(halo);
                g2.fillOval(currentX - 2, dotY - 2, dotSize + 4, dotSize + 4);

                // Cœur du voyant LED
                g2.setColor(base);
                g2.fillOval(currentX, dotY, dotSize, dotSize);

                currentX += dotSize + 6;
            }

            // 4. Texte du statut
            g2.setFont(UITheme.FONT_BADGE);
            g2.setColor(UITheme.TEXT_PRIMARY);

            FontMetrics fm = g2.getFontMetrics();
            int textY = (h - fm.getHeight()) / 2 + fm.getAscent();
            String displayText = text != null ? text : "";
            g2.drawString(displayText, currentX, textY);

        } finally {
            g2.dispose();
        }
    }
}
