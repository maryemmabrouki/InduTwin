package multicast;

import ui.SenderFrame;

import javax.swing.SwingUtilities;

/**
 * Lanceur de compatibilité pour le package multicast.
 * Délègue directement à l'IHM découplée ui.SenderFrame.
 */
public class MultiCastSender {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SenderFrame frame = new SenderFrame();
            frame.setVisible(true);
        });
    }
}
