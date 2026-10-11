import ui.SenderFrame;

import javax.swing.SwingUtilities;

/**
 * Point d'entrée pour lancer l'application émettrice (MultiCastSender).
 * Délègue la création et l'affichage à SenderFrame.
 */
public class MultiCastSender {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            SenderFrame frame = new SenderFrame();
            frame.setVisible(true);
        });
    }
}
