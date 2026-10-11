import ui.ReceiverFrame;

import javax.swing.SwingUtilities;

/**
 * Point d'entrée pour lancer l'application réceptrice (MultiCastReceiver).
 * Délègue la création et l'affichage à ReceiverFrame.
 */
public class MultiCastReceiver {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            ReceiverFrame frame = new ReceiverFrame();
            frame.setVisible(true);
        });
    }
}
