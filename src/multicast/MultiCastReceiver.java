package multicast;

import ui.ReceiverFrame;

import javax.swing.SwingUtilities;

/**
 * Lanceur de compatibilité pour le package multicast.
 * Délègue directement à l'IHM découplée ui.ReceiverFrame.
 */
public class MultiCastReceiver {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ReceiverFrame frame = new ReceiverFrame();
            frame.setVisible(true);
        });
    }
}
