package ui;

import config.NetworkConfig;
import model.NetworkMessage;
import model.ShapeData;
import model.ShapeMessage;
import network.MessageListener;
import network.MulticastManager;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

/**
 * Interface graphique de l'application émettrice Swing.
 * Propose l'envoi unitaire par clic et l'émission cadencée en flux continu (Timer).
 */
public class SenderFrame extends JFrame implements MessageListener {

    private static final long serialVersionUID = 1L;
    private final DrawingPanel drawingPanel;
    private final JLabel statusLabel;
    private final JButton autoStreamBtn;
    private final MulticastManager multicastManager;
    private final Random random = new Random();
    private final Timer continuousStreamTimer;

    private static final String[] SHAPE_TYPES = {"circle", "square", "triangle", "diamond"};

    public SenderFrame() {
        super("Multicast App - Sender");

        this.multicastManager = new MulticastManager();
        this.multicastManager.addListener(this);

        this.drawingPanel = new DrawingPanel();
        this.statusLabel = new JLabel(" Initialisation du réseau Multicast...");
        this.statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        this.autoStreamBtn = new JButton("Flux Continu: OFF");

        // Timer Swing pour la diffusion continue de formes aléatoires
        this.continuousStreamTimer = new Timer(NetworkConfig.DEFAULT_STREAM_DELAY_MS, e -> sendRandomShape());

        initUI();
        initNetwork();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(750, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        JButton circleBtn = new JButton("Circle");
        JButton squareBtn = new JButton("Square");
        JButton triangleBtn = new JButton("Triangle");
        JButton diamondBtn = new JButton("Diamond");
        JButton clearBtn = new JButton("Effacer");

        ActionListener shapeListener = this::onShapeButtonClicked;
        circleBtn.addActionListener(shapeListener);
        squareBtn.addActionListener(shapeListener);
        triangleBtn.addActionListener(shapeListener);
        diamondBtn.addActionListener(shapeListener);

        autoStreamBtn.addActionListener(e -> toggleContinuousStream());

        clearBtn.addActionListener(e -> {
            drawingPanel.clearShapes();
            statusLabel.setText(" Canevas effacé localement");
        });

        topPanel.add(circleBtn);
        topPanel.add(squareBtn);
        topPanel.add(triangleBtn);
        topPanel.add(diamondBtn);
        topPanel.add(autoStreamBtn);
        topPanel.add(clearBtn);

        add(topPanel, BorderLayout.NORTH);
        add(drawingPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (continuousStreamTimer.isRunning()) {
                    continuousStreamTimer.stop();
                }
                multicastManager.stop();
            }
        });
    }

    private void initNetwork() {
        try {
            multicastManager.start();
            statusLabel.setText(" Émetteur connecté au groupe " + multicastManager.getGroupAddress()
                    + ":" + multicastManager.getPort() + " | Prêt pour diffusion");
        } catch (IOException e) {
            statusLabel.setText(" Erreur réseau: " + e.getMessage());
            JOptionPane.showMessageDialog(this,
                    "Impossible de rejoindre le groupe multicast : " + e.getMessage(),
                    "Erreur Réseau",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onShapeButtonClicked(ActionEvent e) {
        String shapeType = e.getActionCommand().toLowerCase();
        sendShape(shapeType);
    }

    private void sendRandomShape() {
        String shapeType = SHAPE_TYPES[random.nextInt(SHAPE_TYPES.length)];
        sendShape(shapeType);
    }

    private void sendShape(String shapeType) {
        int panelW = Math.max(50, drawingPanel.getWidth() - 50);
        int panelH = Math.max(50, drawingPanel.getHeight() - 50);

        int x = random.nextInt(Math.max(1, panelW));
        int y = random.nextInt(Math.max(1, panelH));
        Color color = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));

        ShapeMessage message = new ShapeMessage(shapeType, x, y, color);

        try {
            multicastManager.send(message);
        } catch (IOException ex) {
            statusLabel.setText(" Erreur d'envoi: " + ex.getMessage());
        }
    }

    private void toggleContinuousStream() {
        if (continuousStreamTimer.isRunning()) {
            continuousStreamTimer.stop();
            autoStreamBtn.setText("Flux Continu: OFF");
            autoStreamBtn.setBackground(null);
            statusLabel.setText(" Flux continu arrêté");
        } else {
            continuousStreamTimer.start();
            autoStreamBtn.setText("Flux Continu: ON");
            autoStreamBtn.setBackground(new Color(180, 240, 180));
            statusLabel.setText(" Flux continu actif (cadence: " + NetworkConfig.DEFAULT_STREAM_DELAY_MS + " ms)");
        }
    }

    @Override
    public void onMessageReceived(NetworkMessage message, InetAddress senderAddress) {
        if (message instanceof ShapeMessage) {
            ShapeData shapeData = ((ShapeMessage) message).getShapeData();

            SwingUtilities.invokeLater(() -> {
                drawingPanel.addShape(shapeData);
                statusLabel.setText(" Forme diffusée: " + shapeData.getType()
                        + " | Total affiché: " + drawingPanel.getShapesCount());
            });
        }
    }

    @Override
    public void onNetworkError(Exception e) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(" Alerte réseau: " + e.getMessage()));
    }
}
