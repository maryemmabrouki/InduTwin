import ui.ReceiverFrame;
import ui.SenderFrame;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/**
 * Lanceur unifié permettant de démarrer au choix :
 * 1. Sender et Receiver simultanément (idéal pour tester en local)
 * 2. Receiver uniquement
 * 3. Sender uniquement
 */
public class Main {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            String[] options = {
                    "Dashboard SCADA Industriel (IHM Swing)",
                    "Serveur Industriel Autonome (InduTwinServer)",
                    "Démonstration Temps Réel (UDP + TCP + RMI)",
                    "Client Console RMI (Supervision Distante)",
                    "Supervision Console (IndustrialMain)",
                    "Lancer TP (Sender + Receiver)",
                    "Lancer Receiver seul",
                    "Lancer Sender seul"
            };

            int choice = JOptionPane.showOptionDialog(
                    null,
                    "Sélectionnez le composant à exécuter :",
                    "Plateforme Industrielle Distribuée (Phase 4)",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 0) {
                // Démarre le Dashboard industriel SCADA Swing
                try {
                    ui.industrial.IndustrialDashboard dashboard = new ui.industrial.IndustrialDashboard();
                    dashboard.setVisible(true);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            } else if (choice == 1) {
                // Démarre le serveur autonome headless
                new Thread(() -> industrial.InduTwinServer.main(new String[0])).start();
            } else if (choice == 2) {
                // Démarre la démonstration complète 3 canaux
                new Thread(() -> test.RealTimeIntegrationDemo.main(new String[0])).start();
            } else if (choice == 3) {
                // Démarre le client RMI console
                new Thread(() -> rmi.RmiClientMain.main(new String[0])).start();
            } else if (choice == 4) {
                // Démarre la supervision en console
                new Thread(() -> IndustrialMain.main(new String[0])).start();
            } else if (choice == 5) {
                // Lance les deux fenêtres TP côte à côte
                ReceiverFrame receiver = new ReceiverFrame();
                receiver.setLocation(60, 100);
                receiver.setVisible(true);

                SenderFrame sender = new SenderFrame();
                sender.setLocation(830, 100);
                sender.setVisible(true);
            } else if (choice == 6) {
                ReceiverFrame receiver = new ReceiverFrame();
                receiver.setVisible(true);
            } else if (choice == 7) {
                SenderFrame sender = new SenderFrame();
                sender.setVisible(true);
            }
        });
    }
}
