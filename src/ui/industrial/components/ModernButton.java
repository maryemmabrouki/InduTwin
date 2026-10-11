package ui.industrial.components;

import ui.industrial.theme.UITheme;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Bouton d'action Java Swing moderne pour l'interface SCADA InduTwin.
 * <p>
 * Caractéristiques :
 * <ul>
 *   <li>Design Dark Slate épuré avec coins arrondis conformes à {@link UITheme}</li>
 *   <li>4 variantes sémantiques : {@link ButtonVariant#PRIMARY}, {@link ButtonVariant#SECONDARY},
 *       {@link ButtonVariant#DANGER} et {@link ButtonVariant#GHOST}</li>
 *   <li>Gestion visuelle fluide des états : normal, survol (hover), enfoncé (pressed/armed), désactivé (disabled) et focus</li>
 *   <li>Compatible avec les raccourcis clavier, les mnémoniques, les icônes et les {@link java.awt.event.ActionListener} standards</li>
 * </ul>
 */
public class ModernButton extends JButton {

    private static final long serialVersionUID = 1L;

    /**
     * Variantes graphiques et sémantiques du bouton.
     */
    public enum ButtonVariant {
        /** Action principale (vert émeraude nominal ou confirmation) */
        PRIMARY,
        /** Action secondaire standard (surface ardoise neutre) */
        SECONDARY,
        /** Action destructrice ou critique (arrêt d'urgence, alerte, rouge) */
        DANGER,
        /** Bouton discret sans fond plein (barres d'outils, réinitialisations) */
        GHOST
    }

    private ButtonVariant variant;
    private int cornerRadius = UITheme.RADIUS_BUTTON;
    private boolean isHovered = false;
    private boolean isPressed = false;
    private Color customAccentColor = null;

    /**
     * Crée un bouton avec la variante secondaire par défaut.
     */
    public ModernButton(String text) {
        this(text, null, ButtonVariant.SECONDARY);
    }

    /**
     * Crée un bouton avec le texte et la variante spécifiés.
     */
    public ModernButton(String text, ButtonVariant variant) {
        this(text, null, variant);
    }

    /**
     * Crée un bouton avec icône et variante spécifiées.
     */
    public ModernButton(Icon icon, ButtonVariant variant) {
        this(null, icon, variant);
    }

    /**
     * Crée un bouton entièrement configuré (texte, icône, variante).
     */
    public ModernButton(String text, Icon icon, ButtonVariant variant) {
        super(text, icon);
        this.variant = variant != null ? variant : ButtonVariant.SECONDARY;

        initButton();
    }

    private void initButton() {
        setFont(UITheme.FONT_SUBTITLE);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setOpaque(false);
        setRolloverEnabled(true);
        setCursor(new Cursor(Cursor.HAND_CURSOR));

        // Marges internes équilibrées
        setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        setPreferredSize(new Dimension(getPreferredSize().width, 32));

        // Écouteur pour les effets interactifs de survol et de clic
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                if (isEnabled()) {
                    isHovered = true;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                isHovered = false;
                isPressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isEnabled()) {
                    isPressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                isPressed = false;
                repaint();
            }
        });

        // Écouteur de focus clavier pour l'accessibilité
        addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                repaint();
            }

            @Override
            public void focusLost(FocusEvent e) {
                repaint();
            }
        });
    }

    public ButtonVariant getVariant() {
        return variant;
    }

    public void setVariant(ButtonVariant variant) {
        this.variant = variant != null ? variant : ButtonVariant.SECONDARY;
        repaint();
    }

    public int getCornerRadius() {
        return cornerRadius;
    }

    public void setCornerRadius(int cornerRadius) {
        this.cornerRadius = Math.max(0, cornerRadius);
        repaint();
    }

    /**
     * Permet d'injecter une couleur d'accentuation dédiée (ex: bleu cyan pour MAINTENANCE).
     */
    public void setCustomAccentColor(Color customAccentColor) {
        this.customAccentColor = customAccentColor;
        repaint();
    }

    public Color getCustomAccentColor() {
        return customAccentColor;
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        setCursor(enabled ? new Cursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (!enabled) {
            isHovered = false;
            isPressed = false;
        }
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        int w = getWidth();
        int h = getHeight();

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            boolean active = isEnabled();
            boolean armed = active && (isPressed || getModel().isPressed() || getModel().isArmed());
            boolean hover = active && !armed && (isHovered || getModel().isRollover());

            Color bgColor;
            Color borderColor;
            Color textColor;

            if (!active) {
                // État désactivé
                bgColor = (variant == ButtonVariant.GHOST)
                        ? new Color(0, 0, 0, 0)
                        : UITheme.withAlpha(UITheme.SURFACE_ALT, 80);
                borderColor = UITheme.withAlpha(UITheme.BORDER, 60);
                textColor = UITheme.TEXT_MUTED;
            } else if (customAccentColor != null) {
                // Accentuation personnalisée
                if (armed) {
                    bgColor = customAccentColor.darker();
                } else if (hover) {
                    bgColor = customAccentColor.brighter();
                } else {
                    bgColor = customAccentColor;
                }
                borderColor = customAccentColor.darker();
                textColor = Color.WHITE;
            } else {
                switch (variant) {
                    case PRIMARY:
                        if (armed) {
                            bgColor = new Color(0x05, 0x96, 0x69); // Emerald 600
                        } else if (hover) {
                            bgColor = new Color(0x34, 0xD3, 0x99); // Emerald 400
                        } else {
                            bgColor = UITheme.STATUS_RUNNING;     // Emerald 500 (#10B981)
                        }
                        borderColor = new Color(0x05, 0x96, 0x69);
                        textColor = Color.WHITE;
                        break;

                    case DANGER:
                        if (armed) {
                            bgColor = UITheme.STATUS_FAILURE;     // Bordeaux (#B91C1C)
                        } else if (hover) {
                            bgColor = new Color(0xF8, 0x71, 0x71); // Red 400
                        } else {
                            bgColor = UITheme.STATUS_CRITICAL;    // Red 500 (#EF4444)
                        }
                        borderColor = new Color(0xDC, 0x26, 0x26);
                        textColor = Color.WHITE;
                        break;

                    case GHOST:
                        if (armed) {
                            bgColor = UITheme.withAlpha(UITheme.SURFACE_ALT, 200);
                        } else if (hover) {
                            bgColor = UITheme.withAlpha(UITheme.SURFACE_HOVER, 140);
                        } else {
                            bgColor = new Color(0, 0, 0, 0); // Transparent
                        }
                        borderColor = hover ? UITheme.BORDER : UITheme.withAlpha(UITheme.BORDER, 80);
                        textColor = UITheme.TEXT_PRIMARY;
                        break;

                    case SECONDARY:
                    default:
                        if (armed) {
                            bgColor = UITheme.SURFACE;            // #1E293B
                        } else if (hover) {
                            bgColor = UITheme.SURFACE_HOVER;      // #334155
                        } else {
                            bgColor = UITheme.SURFACE_ALT;        // #27354A
                        }
                        borderColor = hover ? UITheme.BORDER_ACTIVE : UITheme.BORDER;
                        textColor = UITheme.TEXT_PRIMARY;
                        break;
                }
            }

            // 1. Fond arrondi
            if (bgColor.getAlpha() > 0) {
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
            }

            // 2. Bordure subtile
            if (borderColor.getAlpha() > 0) {
                g2.setColor(borderColor);
                g2.drawRoundRect(0, 0, w - 1, h - 1, cornerRadius, cornerRadius);
            }

            // 3. Indicateur de focus clavier (liseré cyan accessible)
            if (hasFocus() && active) {
                g2.setColor(UITheme.withAlpha(UITheme.BORDER_ACTIVE, 180));
                g2.drawRoundRect(1, 1, w - 3, h - 3, cornerRadius, cornerRadius);
            }

            // 4. Couleur du texte pour le rendu de la superclasse
            setForeground(textColor);

        } finally {
            g2.dispose();
        }

        // Rendu standard de l'icône, du texte et des mnémoniques Swing
        super.paintComponent(g);
    }
}
