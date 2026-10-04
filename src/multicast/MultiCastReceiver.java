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
 * Application Réceptrice / Nœud Récepteur (MultiCastReceiver)
 * Adresse Multicast : 224.1.1.1 | Port : 5555
 */
public class MultiCastReceiver {

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

    // Panneau de dessin personnalisé pour afficher les formes reçues
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
            // Antialiasing pour un rendu graphique soigné
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
        // Configuration de la fenêtre principale Swing
        JFrame frame = new JFrame("Multicast App - Receiver");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(750, 520);
        frame.setLocationRelativeTo(null);

        DrawingPanel drawingPanel = new DrawingPanel();
        drawingPanel.setBackground(Color.WHITE);

        // Barre d'outils supérieure avec boutons pour aussi émettre
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        JButton circleBtn = new JButton("Circle");
        JButton squareBtn = new JButton("Square");
        JButton triangleBtn = new JButton("Triangle");
        JButton diamondBtn = new JButton("Diamond");
        JButton clearBtn = new JButton("Effacer");

        topPanel.add(circleBtn);
        topPanel.add(squareBtn);
        topPanel.add(triangleBtn);
        topPanel.add(diamondBtn);
        topPanel.add(clearBtn);

        // Barre de statut
        JLabel statusLabel = new JLabel(" En attente de paquets... | Groupe: 224.1.1.1:5555");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(drawingPanel, BorderLayout.CENTER);
        frame.add(statusLabel, BorderLayout.SOUTH);

        // Configuration du réseau Multicast
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

        // Thread dédié à la réception en continu des paquets Multicast UDP
        Thread receiverThread = new Thread(() -> {
            try {
                byte[] buffer = new byte[1024];
                while (!finalSocket.isClosed()) {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    finalSocket.receive(packet);

                    String message = new String(packet.getData(), 0, packet.getLength());

                    // Format: shape,x,y,rgb
                    String[] parts = message.trim().split(",");
                    if (parts.length == 4) {
                        String shape = parts[0];
                        int x = Integer.parseInt(parts[1]);
                        int y = Integer.parseInt(parts[2]);
                        Color c = new Color(Integer.parseInt(parts[3]));

                        // Injection sûre dans l'EDT (Event Dispatch Thread) de Swing via invokeLater()
                        SwingUtilities.invokeLater(() -> {
                            drawingPanel.addShape(new ShapeData(shape, x, y, c));
                            statusLabel.setText(" Paquet reçu de " + packet.getAddress().getHostAddress() 
                                    + " | Formes: " + drawingPanel.getShapesCount());
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

        // Envoi au clic (permet au récepteur d'interagir également)
        Random random = new Random();
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

        clearBtn.addActionListener(e -> {
            drawingPanel.clearShapes();
            statusLabel.setText(" Canvas effacé localement");
        });

        // Nettoyage fermeture
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                try {
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
