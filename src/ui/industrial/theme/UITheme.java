package ui.industrial.theme;

import model.industrial.AlarmLevel;
import model.industrial.MachineState;

import javax.swing.BorderFactory;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Système de design centralisé pour l'interface de supervision industrielle InduTwin (SCADA 4.0).
 * <p>
 * Définit la charte graphique complète :
 * <ul>
 *   <li>Palette sombre industrielle (Dark Slate Industrial) optimisée pour salle de contrôle 24/7</li>
 *   <li>Couleurs sémantiques des états machines et alarmes conformes aux standards industriels</li>
 *   <li>Typographies avec gestion automatique des polices de repli (Segoe UI / Consolas)</li>
 *   <li>Constantes d'espacement, rayons d'arrondi et méthodes utilitaires graphiques</li>
 * </ul>
 */
public final class UITheme {

    // =========================================================================
    // 1. PALETTE DE COULEURS DE FOND, SURFACES ET TEXTE (DARK SLATE)
    // =========================================================================

    /** Fond principal de l'application (Slate très sombre) */
    public static final Color BG_DARK = new Color(0x0B, 0x0F, 0x19); // #0B0F19

    /** Fond de l'en-tête de supervision et des barres d'outils */
    public static final Color BG_HEADER = new Color(0x0F, 0x17, 0x2A); // #0F172A

    /** Surface des cartes, conteneurs et panneaux principaux */
    public static final Color SURFACE = new Color(0x1E, 0x29, 0x3B); // #1E293B

    /** Surface secondaire (éléments imbriqués, headers de tables, entrées de saisie) */
    public static final Color SURFACE_ALT = new Color(0x27, 0x35, 0x4A); // #27354A

    /** Surface au survol (hover) d'un élément cliquable */
    public static final Color SURFACE_HOVER = new Color(0x33, 0x41, 0x55); // #334155

    /** Bordure standard discrète */
    public static final Color BORDER = new Color(0x33, 0x41, 0x55); // #334155

    /** Bordure contrastée pour élément sélectionné ou actif */
    public static final Color BORDER_ACTIVE = new Color(0x38, 0xBD, 0xF8); // #38BDF8 (Cyan)

    /** Texte principal (titres, valeurs, données clés) */
    public static final Color TEXT_PRIMARY = new Color(0xF1, 0xF5, 0xF9); // #F1F5F9

    /** Texte secondaire (libellés, unités, statuts neutres) */
    public static final Color TEXT_SECONDARY = new Color(0x94, 0xA3, 0xB8); // #94A3B8

    /** Texte atténué (horodatages, métadonnées, légendes) */
    public static final Color TEXT_MUTED = new Color(0x64, 0x74, 0x8B); // #64748B

    // =========================================================================
    // 2. COULEURS SÉMANTIQUES DES ÉTATS INDUSTRIELS & ALARMES
    // =========================================================================

    /** État nominal, en cours d'exécution, bonne santé (Vert Émeraude) */
    public static final Color STATUS_RUNNING = new Color(0x10, 0xB9, 0x81); // #10B981

    /** État d'avertissement, seuil haut atteint (Ambre / Orange) */
    public static final Color STATUS_WARNING = new Color(0xF5, 0x9E, 0x0B); // #F59E0B

    /** État critique, alarme de niveau 1 (Rouge vif) */
    public static final Color STATUS_CRITICAL = new Color(0xEF, 0x44, 0x44); // #EF4444

    /** Panne matérielle bloquante, arrêt d'urgence (Rouge cramoisi sombre) */
    public static final Color STATUS_FAILURE = new Color(0xB9, 0x1C, 0x1C); // #B91C1C

    /** Mode maintenance ou configuration (Bleu / Cyan) */
    public static final Color STATUS_MAINTENANCE = new Color(0x0E, 0xA5, 0xE9); // #0EA5E9

    /** Démarrage ou transition en cours (Bleu cobalt) */
    public static final Color STATUS_STARTING = new Color(0x3B, 0x82, 0xF6); // #3B82F6

    /** Machine déconnectée, perte de pulsation heartbeat (Gris ardoise) */
    public static final Color STATUS_OFFLINE = new Color(0x64, 0x74, 0x8B); // #64748B

    /** Machine arrêtée normalement à l'arrêt nominal (Gris neutre) */
    public static final Color STATUS_STOPPED = new Color(0x47, 0x55, 0x69); // #475569

    // =========================================================================
    // 3. TYPOGRAPHIES (AVEC REPLI SUR POLICES SYSTÈME)
    // =========================================================================

    private static final String SANS_SERIF_NAME = resolveFontFamily("Segoe UI", "Inter", "SansSerif");
    private static final String MONO_NAME = resolveFontFamily("Consolas", "Cascadia Code", "Monospaced");

    /** Grand titre du bandeau supérieur (17 pt Bold) */
    public static final Font FONT_HEADER_TITLE = new Font(SANS_SERIF_NAME, Font.BOLD, 17);

    /** Titre de carte ou de panneau (14 pt Bold) */
    public static final Font FONT_TITLE = new Font(SANS_SERIF_NAME, Font.BOLD, 14);

    /** Sous-titre ou en-tête de section (12 pt Bold) */
    public static final Font FONT_SUBTITLE = new Font(SANS_SERIF_NAME, Font.BOLD, 12);

    /** Corps de texte standard (12 pt Plain) */
    public static final Font FONT_BODY = new Font(SANS_SERIF_NAME, Font.PLAIN, 12);

    /** Texte secondaire compact (11 pt Plain) */
    public static final Font FONT_SMALL = new Font(SANS_SERIF_NAME, Font.PLAIN, 11);

    /** Texte très compact pour timestamps ou légendes (10 pt Plain) */
    public static final Font FONT_TINY = new Font(SANS_SERIF_NAME, Font.PLAIN, 10);

    /** Grand affichage de valeur numérique KPI (20 pt Bold Monospace) */
    public static final Font FONT_VALUE_LARGE = new Font(MONO_NAME, Font.BOLD, 20);

    /** Valeur de capteur en temps réel (15 pt Bold Monospace) */
    public static final Font FONT_VALUE_MEDIUM = new Font(MONO_NAME, Font.BOLD, 15);

    /** Données tabulaires ou métriques réseau (12 pt Plain Monospace) */
    public static final Font FONT_VALUE_SMALL = new Font(MONO_NAME, Font.PLAIN, 12);

    /** Badge ou libellé de statut (11 pt Bold) */
    public static final Font FONT_BADGE = new Font(SANS_SERIF_NAME, Font.BOLD, 11);

    // =========================================================================
    // 4. RAYONS D'ARRONDI, PADDING ET DIMENSIONS GRAPHIQUES
    // =========================================================================

    public static final int RADIUS_CARD = 8;
    public static final int RADIUS_BADGE = 12;
    public static final int RADIUS_BUTTON = 6;
    public static final int RADIUS_GAUGE = 4;

    public static final int PADDING_CARD = 10;
    public static final int PADDING_HEADER = 12;

    private UITheme() {
        // Classe utilitaire non instanciable
    }

    // =========================================================================
    // 5. MÉTHODES UTILITAIRES POUR ÉTATS, COULEURS ET BORDURES
    // =========================================================================

    /**
     * Retourne la couleur sémantique officielle associée à l'état machine.
     */
    public static Color getColorForState(MachineState state) {
        if (state == null) {
            return STATUS_STOPPED;
        }
        switch (state) {
            case RUNNING:
                return STATUS_RUNNING;
            case WARNING:
                return STATUS_WARNING;
            case CRITICAL:
                return STATUS_CRITICAL;
            case FAILURE:
                return STATUS_FAILURE;
            case MAINTENANCE:
                return STATUS_MAINTENANCE;
            case STARTING:
                return STATUS_STARTING;
            case OFFLINE:
                return STATUS_OFFLINE;
            case STOPPED:
            default:
                return STATUS_STOPPED;
        }
    }

    /**
     * Retourne la couleur sémantique associée à un niveau d'alarme.
     */
    public static Color getColorForAlarmLevel(AlarmLevel level) {
        if (level == null) {
            return TEXT_MUTED;
        }
        switch (level) {
            case CRITICAL:
                return STATUS_CRITICAL;
            case WARNING:
                return STATUS_WARNING;
            case INFO:
                return STATUS_MAINTENANCE;
            case NONE:
            default:
                return STATUS_RUNNING;
        }
    }

    /**
     * Crée une couleur dérivée avec un canal alpha personnalisé (translucidité).
     *
     * @param baseColor Couleur source
     * @param alpha     Opacité entre 0 (transparent) et 255 (opaque)
     * @return Nouvelle instance de {@link Color} avec l'alpha spécifié
     */
    public static Color withAlpha(Color baseColor, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), clamped);
    }

    /**
     * Crée une bordure standard de carte avec contour discret et marges internes.
     */
    public static Border createCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1),
                BorderFactory.createEmptyBorder(PADDING_CARD, PADDING_CARD, PADDING_CARD, PADDING_CARD)
        );
    }

    /**
     * Crée une bordure de carte sélectionnée avec mise en valeur (liseré actif).
     */
    public static Border createActiveCardBorder() {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_ACTIVE, 2),
                BorderFactory.createEmptyBorder(PADDING_CARD - 1, PADDING_CARD - 1, PADDING_CARD - 1, PADDING_CARD - 1)
        );
    }

    /**
     * Résout la première police disponible sur la machine parmi une liste de candidats.
     */
    private static String resolveFontFamily(String preferred, String secondary, String fallback) {
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        Set<String> available = new HashSet<>(Arrays.asList(ge.getAvailableFontFamilyNames()));

        if (available.contains(preferred)) {
            return preferred;
        }
        if (available.contains(secondary)) {
            return secondary;
        }
        return fallback;
    }
}
