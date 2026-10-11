package ui;

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
 * Interface graphique de l'application réceptrice Swing.
 * Utilise MessageListener pour recevoir les mises à jour sans couplage avec le socket.
 */
public class ReceiverFrame extends JFrame implements MessageListener {

    private static final long serialVersionUID = 1L;
    private final DrawingPanel drawingPanel;
    private final JLabel statusLabel;
    private final MulticastManager multicastManager;
    private final Random random = new Random();

    public ReceiverFrame() {
        super("Multicast App - Receiver");

        this.multicastManager = new MulticastManager();
        this.multicastManager.addListener(this);

        this.drawingPanel = new DrawingPanel();
        this.statusLabel = new JLabel(" Initialisation du réseau Multicast...");
        this.statusLabel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        initUI();
        initNetwork();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(750, 520);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        // Barre d'outils supérieure
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

        clearBtn.addActionListener(e -> {
            drawingPanel.clearShapes();
            statusLabel.setText(" Canevas effacé localement");
        });

        topPanel.add(circleBtn);
        topPanel.add(squareBtn);
        topPanel.add(triangleBtn);
        topPanel.add(diamondBtn);
        topPanel.add(clearBtn);

        add(topPanel, BorderLayout.NORTH);
        add(drawingPanel, BorderLayout.CENTER);
        add(statusLabel, BorderLayout.SOUTH);

        // Nettoyage réseau à la fermeture de la fenêtre
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                multicastManager.stop();
            }
        });
    }

    private void initNetwork() {
        try {
            multicastManager.start();
            statusLabel.setText(" Connecté au groupe " + multicastManager.getGroupAddress()
                    + ":" + multicastManager.getPort() + " | En attente de paquets...");
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

    @Override
    public void onMessageReceived(NetworkMessage message, InetAddress senderAddress) {
        if (message instanceof ShapeMessage) {
            ShapeData shapeData = ((ShapeMessage) message).getShapeData();

            // Injection thread-safe dans l'Event Dispatch Thread (EDT)
            SwingUtilities.invokeLater(() -> {
                drawingPanel.addShape(shapeData);
                statusLabel.setText(" Paquet reçu de " + senderAddress.getHostAddress()
                        + " | Forme: " + shapeData.getType()
                        + " | Total affiché: " + drawingPanel.getShapesCount());
            });
        }
    }

    @Override
    public void onNetworkError(Exception e) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(" Alerte réseau: " + e.getMessage()));
    }
}
