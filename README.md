# TP Multicast : Synchronisation d'Interface Graphique Distribuée en Java

Projet réalisé selon les spécifications du TP de réseaux / systèmes distribués avec **Java Swing** et **MulticastSocket**.

---

## 🎯 Architecture & Principe de Fonctionnement

- **Adresse de diffusion Multicast** : `224.1.1.1` (Classe D réservée au multicast local)
- **Port UDP** : `5555`
- **Format du message Datagramme** : `forme,x,y,rgb`
  - Exemple : `circle,150,220,-65536`
- **Formes prises en charge** :
  1. `circle` : Cercle plein via `g2d.fillOval(x, y, 50, 50)`
  2. `square` : Carré plein via `g2d.fillRect(x, y, 50, 50)`
  3. `triangle` : Triangle plein via `g2d.fillPolygon(xPoints, yPoints, 3)`
  4. `diamond` : Losange plein via `g2d.fillPolygon(xPoints, yPoints, 4)`
- **Multithreading & EDT (Event Dispatch Thread)** :
  - La réception des paquets réseau s'effectue dans un **Thread dédié d'arrière-plan** pour ne pas bloquer l'interface utilisateur.
  - La mise à jour de l'affichage Swing est transmise à l'EDT via `SwingUtilities.invokeLater()` pour garantir la sécurité des threads (**thread-safety**).

---

## 📁 Structure du Projet

```text
CISCO/
├── CISCO.iml                 # Fichier de module IntelliJ IDEA
├── .idea/                    # Configuration IntelliJ IDEA
│   ├── modules.xml
│   └── misc.xml
└── src/
    └── multicast/
        ├── MultiCastReceiver.java   # Nœud Récepteur Swing (port 5555, 224.1.1.1)
        └── MultiCastSender.java     # Nœud Émetteur Swing (avec mode manuel & flux continu)
```

---

## 🚀 Comment ouvrir et lancer le projet dans IntelliJ IDEA

1. **Ouvrir le projet dans IntelliJ** :
   - Lancez IntelliJ IDEA.
   - Cliquez sur **File -> Open...** (ou **Open** sur l'écran d'accueil).
   - Sélectionnez le dossier : `c:\Users\pc\OneDrive\Desktop\4eme_année\CISCO`.
   - Cliquez sur **Trust Project** si demandé.

2. **Lancer le Récepteur (Receiver)** :
   - Ouvrez [MultiCastReceiver.java](file:///c:/Users/pc/OneDrive/Desktop/4eme_ann%C3%A9e/CISCO/src/multicast/MultiCastReceiver.java).
   - Cliquez sur le triangle vert **Run** (ou `Shift + F10`).
   - La fenêtre `Multicast App - Receiver` s'affiche.

3. **Lancer l'Émetteur (Sender)** :
   - Ouvrez [MultiCastSender.java](file:///c:/Users/pc/OneDrive/Desktop/4eme_ann%C3%A9e/CISCO/src/multicast/MultiCastSender.java).
   - Cliquez sur le triangle vert **Run** (ou `Shift + F10`).
   - La fenêtre `Multicast App - Sender` s'affiche.

4. **Tester la synchronisation** :
   - **Mode Manuel** : Cliquez sur les boutons `Circle`, `Square`, `Triangle`, `Diamond` dans la fenêtre Sender. Vous verrez la même forme apparaître instantanément dans toutes les fenêtres (Sender et Receiver).
   - **Mode Flux Continu (Auto Stream)** : Cliquez sur le bouton `Flux Continu: OFF` dans le Sender pour l'activer (`ON`). Un flux continu de formes aléatoires sera diffusé à haute fréquence (exactement comme illustré dans la capture de la présentation du TP).

---

## 🌐 Test entre plusieurs PC sur le même réseau local (LAN / Wi-Fi)

Pour synchroniser plusieurs machines (comme les 3 ordinateurs portables de la page 1 du TP) :
1. Connectez tous les ordinateurs sur le **même réseau Wi-Fi ou câble Ethernet**.
2. Assurez-vous que le pare-feu (Windows Defender Firewall) autorise le trafic Java sur les réseaux privés (port UDP 5555).
3. Lancez `MultiCastReceiver` sur les PC 1 et 2, et `MultiCastSender` sur le PC 3 (ou lancez les deux sur chaque PC pour un tableau blanc partagé interactif).
