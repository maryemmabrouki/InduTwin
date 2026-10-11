package ui.industrial.components;

import ui.industrial.theme.UITheme;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Path2D;

/**
 * Composant Swing léger traçant une mini-courbe d'évolution temporelle (Sparkline).
 * Conçu pour afficher la tendance continue des capteurs industriels (température, vibration, etc.).
 * <p>
 * Caractéristiques techniques :
 * <ul>
 *   <li>Buffer circulaire de primitives {@code double} pour une insertion en O(1) avec zéro allocation mémoire</li>
 *   <li>Mise à l'échelle automatique (auto-scaling) avec protection stricte contre les divisions par zéro</li>
 *   <li>Filtrage automatique des valeurs non finies ({@code NaN}, {@code Infinity})</li>
 *   <li>Rendu vectoriel antialiasé avec gradient translucide sous la courbe et point LED sur la valeur courante</li>
 *   <li>Thread-safe pour les appels depuis les écouteurs de télémétrie réseau hors EDT</li>
 * </ul>
 */
public class SparklineChart extends JPanel {

    private static final long serialVersionUID = 1L;

    /** Capacité par défaut de l'historique glissant (ex: 40 échantillons) */
    public static final int DEFAULT_CAPACITY = 40;

    private final double[] buffer;
    private final int capacity;
    private int head = 0;
    private int count = 0;

    private Color lineColor;
    private boolean fillUnderCurve = true;
    private boolean showLastPointIndicator = true;
    private boolean showBaseline = true;

    private boolean isFixedScale = false;
    private double fixedMin = 0.0;
    private double fixedMax = 100.0;

    private static final Stroke LINE_STROKE = new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final Stroke DASHED_STROKE = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{2.0f, 3.0f}, 0);

    /**
     * Crée un SparklineChart avec capacité par défaut (40 points) et couleur verte nominale.
     */
    public SparklineChart() {
        this(DEFAULT_CAPACITY, UITheme.STATUS_RUNNING);
    }

    /**
     * Crée un SparklineChart avec une capacité donnée et couleur verte nominale.
     *
     * @param capacity Nombre d'échantillons glissants conservés en mémoire
     */
    public SparklineChart(int capacity) {
        this(capacity, UITheme.STATUS_RUNNING);
    }

    /**
     * Crée un SparklineChart avec capacité par défaut et une couleur de tracé spécifique.
     *
     * @param lineColor Couleur principale de la courbe
     */
    public SparklineChart(Color lineColor) {
        this(DEFAULT_CAPACITY, lineColor);
    }

    /**
     * Crée un SparklineChart entièrement configuré.
     *
     * @param capacity  Nombre d'échantillons glissants (minimum 2)
     * @param lineColor Couleur principale de la courbe
     */
    public SparklineChart(int capacity, Color lineColor) {
        this.capacity = Math.max(2, capacity);
        this.buffer = new double[this.capacity];
        this.lineColor = lineColor != null ? lineColor : UITheme.STATUS_RUNNING;

        setOpaque(false);
        setPreferredSize(new Dimension(110, 32));
        setMinimumSize(new Dimension(50, 20));
    }

    /**
     * Ajoute un échantillon à l'historique glissant et planifie un rafraîchissement d'affichage.
     * Ignore automatiquement les valeurs {@code NaN} ou infinies.
     *
     * @param value Valeur numérique de la mesure
     */
    public synchronized void addValue(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return;
        }

        buffer[head] = value;
        head = (head + 1) % capacity;
        if (count < capacity) {
            count++;
        }

        safeRepaint();
    }

    /**
     * Réinitialise l'historique des points.
     */
    public synchronized void clear() {
        head = 0;
        count = 0;
        safeRepaint();
    }

    /**
     * Définit une échelle verticale fixe personnalisée (désactive l'auto-scaling).
     *
     * @param min Borne minimale
     * @param max Borne maximale
     */
    public synchronized void setFixedScale(double min, double max) {
        if (max > min) {
            this.fixedMin = min;
            this.fixedMax = max;
            this.isFixedScale = true;
            safeRepaint();
        }
    }

    /**
     * Rétablit l'ajustement dynamique automatique de l'échelle (auto-scaling).
     */
    public synchronized void setAutoScale() {
        this.isFixedScale = false;
        safeRepaint();
    }

    public synchronized void setLineColor(Color lineColor) {
        this.lineColor = lineColor != null ? lineColor : UITheme.STATUS_RUNNING;
        safeRepaint();
    }

    public synchronized Color getLineColor() {
        return lineColor;
    }

    public synchronized void setFillUnderCurve(boolean fillUnderCurve) {
        this.fillUnderCurve = fillUnderCurve;
        safeRepaint();
    }

    public synchronized boolean isFillUnderCurve() {
        return fillUnderCurve;
    }

    public synchronized void setShowLastPointIndicator(boolean showLastPointIndicator) {
        this.showLastPointIndicator = showLastPointIndicator;
        safeRepaint();
    }

    public synchronized boolean isShowLastPointIndicator() {
        return showLastPointIndicator;
    }

    public synchronized void setShowBaseline(boolean showBaseline) {
        this.showBaseline = showBaseline;
        safeRepaint();
    }

    public synchronized int getCount() {
        return count;
    }

    public synchronized int getCapacity() {
        return capacity;
    }

    /**
     * Retourne la dernière valeur ajoutée ou {@code Double.NaN} si l'historique est vide.
     */
    public synchronized double getLatestValue() {
        if (count == 0) {
            return Double.NaN;
        }
        int lastIdx = (head - 1 + capacity) % capacity;
        return buffer[lastIdx];
    }

    private void safeRepaint() {
        if (SwingUtilities.isEventDispatchThread()) {
            repaint();
        } else {
            SwingUtilities.invokeLater(this::repaint);
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        int w = getWidth();
        int h = getHeight();

        int padLeft = 4;
        int padRight = 8;
        int padTop = 4;
        int padBottom = 4;

        int innerW = w - padLeft - padRight;
        int innerH = h - padTop - padBottom;

        if (innerW <= 2 || innerH <= 2) {
            return;
        }

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            // 1. Ligne de repère médiane discrète (baseline)
            if (showBaseline) {
                g2.setColor(UITheme.withAlpha(UITheme.BORDER, 80));
                g2.setStroke(DASHED_STROKE);
                int midY = padTop + (innerH / 2);
                g2.drawLine(padLeft, midY, padLeft + innerW, midY);
            }

            // Extraction synchronisée des données
            int localCount;
            double[] localValues;
            Color currentLineColor;
            boolean doFill;
            boolean doLed;
            boolean fixed;
            double fMin;
            double fMax;

            synchronized (this) {
                localCount = this.count;
                if (localCount == 0) {
                    return;
                }
                localValues = new double[localCount];
                int start = (head - count + capacity) % capacity;
                for (int i = 0; i < localCount; i++) {
                    localValues[i] = buffer[(start + i) % capacity];
                }
                currentLineColor = this.lineColor;
                doFill = this.fillUnderCurve;
                doLed = this.showLastPointIndicator;
                fixed = this.isFixedScale;
                fMin = this.fixedMin;
                fMax = this.fixedMax;
            }

            // Calcul des bornes de l'échelle
            double min;
            double max;
            if (fixed) {
                min = fMin;
                max = fMax;
            } else {
                min = localValues[0];
                max = localValues[0];
                for (int i = 1; i < localCount; i++) {
                    if (localValues[i] < min) min = localValues[i];
                    if (localValues[i] > max) max = localValues[i];
                }
            }

            // Protection anti-division par zéro et marges de respiration
            double range = max - min;
            if (range <= 0.0001) {
                min -= 1.0;
                max += 1.0;
                range = 2.0;
            } else {
                // Marge de respiration de 6% en haut et en bas
                double margin = range * 0.06;
                min -= margin;
                max += margin;
                range = max - min;
            }

            // Cas d'un seul point : ligne plate et point LED
            if (localCount == 1) {
                int singleY = padTop + (innerH / 2);
                g2.setColor(currentLineColor);
                g2.setStroke(LINE_STROKE);
                g2.drawLine(padLeft, singleY, padLeft + innerW, singleY);

                if (doLed) {
                    drawLedPoint(g2, padLeft + innerW, singleY, currentLineColor);
                }
                return;
            }

            // Construction du tracé vectoriel
            Path2D.Double linePath = new Path2D.Double();
            double xStep = (double) innerW / (localCount - 1);

            int lastX = padLeft;
            int lastY = padTop + innerH;

            for (int i = 0; i < localCount; i++) {
                double curX = padLeft + (i * xStep);
                double norm = (max - localValues[i]) / range;
                double curY = padTop + (norm * innerH);

                // Bornage de sécurité dans la zone d'affichage
                curY = Math.max(padTop, Math.min(padTop + innerH, curY));

                if (i == 0) {
                    linePath.moveTo(curX, curY);
                } else {
                    linePath.lineTo(curX, curY);
                }

                if (i == localCount - 1) {
                    lastX = (int) Math.round(curX);
                    lastY = (int) Math.round(curY);
                }
            }

            // 2. Remplissage translucide en dégradé sous la courbe
            if (doFill) {
                Path2D.Double fillPath = (Path2D.Double) linePath.clone();
                fillPath.lineTo(padLeft + innerW, padTop + innerH);
                fillPath.lineTo(padLeft, padTop + innerH);
                fillPath.closePath();

                GradientPaint gradient = new GradientPaint(
                        0, padTop, UITheme.withAlpha(currentLineColor, 60),
                        0, padTop + innerH, UITheme.withAlpha(currentLineColor, 4)
                );
                g2.setPaint(gradient);
                g2.fill(fillPath);
            }

            // 3. Tracé de la courbe
            g2.setColor(currentLineColor);
            g2.setStroke(LINE_STROKE);
            g2.draw(linePath);

            // 4. Point indicateur LED sur la dernière mesure
            if (doLed) {
                drawLedPoint(g2, lastX, lastY, currentLineColor);
            }

        } finally {
            g2.dispose();
        }
    }

    private void drawLedPoint(Graphics2D g2, int cx, int cy, Color color) {
        // Halo lumineux
        g2.setColor(UITheme.withAlpha(color, 80));
        g2.fillOval(cx - 5, cy - 5, 10, 10);

        // Pastille centrale
        g2.setColor(color);
        g2.fillOval(cx - 3, cy - 3, 6, 6);

        // Point central brillant blanc
        g2.setColor(Color.WHITE);
        g2.fillOval(cx - 1, cy - 1, 2, 2);
    }
}
