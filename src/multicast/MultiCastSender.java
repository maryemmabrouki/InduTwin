package multicast;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * TP Multicast : Synchronisation d'interface graphique distribuée en Java
 * Application Émettrice / Nœud Émetteur (MultiCastSender)
 * Adresse Multicast : 224.1.1.1 | Port : 5555
 */
public class MultiCastSender {

    // Modèle de données pour stocker une forme géométrique
    static class ShapeData {
        String type;
        int x, y;
        Color color;

        ShapeData(String type, int x, int y, Color color) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }

    // Panneau de dessin synchronisé
    static class DrawingPanel extends JPanel {
        private final List<ShapeData> shapes = new ArrayList<>();

        public synchronized void addShape(ShapeData s) {
            shapes.add(s);
            repaint();
        }

        public synchronized void clearShapes() {
            shapes.clear();
            repaint();
        }

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
                    g2d.setColor(s.color);
                    if (s.type.equalsIgnoreCase("circle")) {
                        g2d.fillOval(s.x, s.y, 50, 50);
                    } else if (s.type.equalsIgnoreCase("square")) {
                        g2d.fillRect(s.x, s.y, 50, 50);
                    } else if (s.type.equalsIgnoreCase("triangle")) {
                        int[] xPoints = {s.x + 25, s.x, s.x + 50};
                        int[] yPoints = {s.y, s.y + 50, s.y + 50};
                        g2d.fillPolygon(xPoints, yPoints, 3);
                    } else if (s.type.equalsIgnoreCase("diamond")) {
                        int[] xPoints = {s.x + 25, s.x + 50, s.x + 25, s.x};
                        int[] yPoints = {s.y, s.y + 25, s.y + 50, s.y + 25};
                        g2d.fillPolygon(xPoints, yPoints, 4);
                    }
                }
            }
        }
    }

    public static void main(String[] args) {
        // Fenêtre Swing de l'émetteur
        JFrame frame = new JFrame("Multicast App - Sender");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(750, 520);
        frame.setLocationRelativeTo(null);

        DrawingPanel drawingPanel = new DrawingPanel();
        drawingPanel.setBackground(Color.WHITE);

        // Barre d'outils avec boutons d'envoi de formes
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        JButton circleBtn = new JButton("Circle");
        JButton squareBtn = new JButton("Square");
        JButton triangleBtn = new JButton("Triangle");
        JButton diamondBtn = new JButton("Diamond");
        JButton autoStreamBtn = new JButton("Flux Continu: OFF");
        JButton clearBtn = new JButton("Effacer");

        topPanel.add(circleBtn);
        topPanel.add(squareBtn);
        topPanel.add(triangleBtn);
        topPanel.add(diamondBtn);
        topPanel.add(autoStreamBtn);
        topPanel.add(clearBtn);

        // Barre de statut en bas
        JLabel statusLabel = new JLabel(" Prêt | Groupe: 224.1.1.1:5555 | Formes affichées: 0");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(drawingPanel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        // Configuration réseau Multicast
        String ip = "224.1.1.1";
        int port = 5555;
        MulticastSocket socket = null;
        InetAddress group = null;

        try {
            socket = new MulticastSocket(port);
            group = InetAddress.getByName(ip);
            socket.joinGroup(group);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Erreur réseau: " + e.getMessage(), "Erreur", JOptionPane.ERROR_MESSAGE);
            return;
        }

        final MulticastSocket finalSocket = socket;
        final InetAddress finalGroup = group;

        // Thread récepteur pour afficher en temps réel les formes sur le canvas
        Thread receiverThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (!finalSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    finalSocket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());

                    // format: shape,x,y,rgb
                    String[] parts = message.trim().split(",");
                    if (parts.length == 4) {
                        String shape = parts[0];
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        Color c = new Color(Integer.parseInt(parts[3]));

                        // Injection sûre dans l'EDT Swing
                        SwingUtilities.invokeLater(() -> {
                            drawingPanel.addShape(new ShapeData(shape, x, y, c));
                            statusLabel.setText(" Connecté à 224.1.1.1:5555 | Formes: " + drawingPanel.getShapesCount());
                        });
                    }
                }
            } catch (Exception e) {
                if (!finalSocket.isClosed()) {
                    e.printStackTrace();
                }
            }
        });
        receiverThread.setDaemon(true);
        receiverThread.start();

        Random random = new Random();

        // Fonction d'envoi d'une trame Multicast
        Runnable sendRandomShape = () -> {
            try {
                String[] shapeTypes = {"circle", "square", "triangle", "diamond"};
                String shape = shapeTypes[random.nextInt(shapeTypes.length)];

                int panelW = Math.max(50, drawingPanel.getWidth() - 50);
                int panelH = Math.max(50, drawingPanel.getHeight() - 50);

                int x = random.nextInt(Math.max(1, panelW));
                int y = random.nextInt(Math.max(1, panelH));
                Color c = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));

                String msg = shape + "," + x + "," + y + "," + c.getRGB();
                byte[] buf = msg.getBytes();
                DatagramPacket packet = new DatagramPacket(buf, buf.length, finalGroup, port);
                finalSocket.send(packet);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        };

        // Envoi au clic sur un bouton spécifique
        ActionListener sendShapeAction = new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    String shape = e.getActionCommand().toLowerCase();
                    int panelW = Math.max(50, drawingPanel.getWidth() - 50);
                    int panelH = Math.max(50, drawingPanel.getHeight() - 50);

                    int x = random.nextInt(Math.max(1, panelW));
                    int y = random.nextInt(Math.max(1, panelH));
                    Color c = new Color(random.nextInt(256), random.nextInt(256), random.nextInt(256));

                    // Trame UDP : shape,x,y,rgb
                    String msg = shape + "," + x + "," + y + "," + c.getRGB();
                    byte[] buf = msg.getBytes();
                    DatagramPacket packet = new DatagramPacket(buf, buf.length, finalGroup, port);
                    finalSocket.send(packet);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        circleBtn.addActionListener(sendShapeAction);
        squareBtn.addActionListener(sendShapeAction);
        triangleBtn.addActionListener(sendShapeAction);
        diamondBtn.addActionListener(sendShapeAction);

        // Timer pour flux continu (comme décrit dans le rapport TP)
        Timer continuousStreamTimer = new Timer(60, evt -> sendRandomShape.run());

        autoStreamBtn.addActionListener(e -> {
            if (continuousStreamTimer.isRunning()) {
                continuousStreamTimer.stop();
                autoStreamBtn.setText("Flux Continu: OFF");
                autoStreamBtn.setBackground(null);
            } else {
                continuousStreamTimer.start();
                autoStreamBtn.setText("Flux Continu: ON");
                autoStreamBtn.setBackground(new Color(180, 240, 180));
            }
        });

        clearBtn.addActionListener(e -> {
            drawingPanel.clearShapes();
            statusLabel.setText(" Canvas effacé | Connecté à 224.1.1.1:5555");
        });

        // Fermeture propre de la socket et désabonnement du groupe
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                try {
                    if (continuousStreamTimer.isRunning()) {
                        continuousStreamTimer.stop();
                    }
                    if (finalSocket != null && !finalSocket.isClosed()) {
                        finalSocket.leaveGroup(finalGroup);
                        finalSocket.close();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        frame.setVisible(true);
    }
}
