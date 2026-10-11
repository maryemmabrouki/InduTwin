# Plateforme Distribuée de Supervision Industrielle & Multicast (Java)

Système distribué de supervision industrielle (SCADA 4.0), informatique industrielle et réseaux informatiques développé en **Java 17 / Swing** sur **UDP Multicast `224.1.1.1:5555`**.

Le système simule une infrastructure d'usine avec plusieurs machines industrielles virtuelles (`M01`, `M02`, `M03`), diffuse leur télémétrie et leurs battements de cœur sur un bus multicast, et fournit un **Dashboard de Supervision Graphique Swing** complet avec surveillance de la santé du réseau (débit, pertes potentielles, latence).

---

## 🎯 Architecture Modulaire (Phases 1, 2, 3 & 4)

L'architecture respecte strictement le principe de **Séparation des Responsabilités (SoC)**, le patron **Observer (MessageListener)** et le découplage des canaux de communication :

```text
                  INDUSTRIAL DIGITAL TWIN
                           │
              ┌────────────┼────────────┐
              │            │            │
       UDP MULTICAST      TCP         SQLITE
       Télémétrie       Contrôle     Historique
       Heartbeat        Commandes    & Audit
       Alarmes            ACK
              │            │            │
              └────────────┼────────────┘
                           │
                  IndustrialSupervisor
                           │
                 ┌─────────┴─────────┐
                 │                   │
         Dashboard SCADA      RMI (Port 1099)
          (IHM Swing)       Supervision Distante
```

```text
src/
├── config/
│   └── NetworkConfig.java        # Paramètres réseau (UDP 224.1.1.1:5555, TCP 5556, RMI 1099)
│
├── model/
│   ├── NetworkMessage.java       # Contrat d'abstraction pour les trames UDP
│   ├── TelemetryMessage.java     # Télémétrie : TELEMETRY,machineId,sensorType,value,unit,seq,timestamp
│   ├── HeartbeatMessage.java     # Battement de cœur : HEARTBEAT,machineId,state,seq,timestamp
│   ├── AlarmMessage.java         # Alarmes & événements : ALARM,machineId,level,event,sensor,value,desc,seq,timestamp
│   ├── ShapeMessage.java         # Rétrocompatibilité TP Swing : shape,x,y,rgb
│   ├── ShapeData.java            # Modèle géométrique du TP Phase 1
│   │
│   └── industrial/
│       ├── Machine.java          # Modèle d'équipement reliant capteurs, états et heartbeat
│       ├── MachineState.java     # États finis (STOPPED, STARTING, RUNNING, WARNING, CRITICAL, FAILURE, MAINTENANCE, OFFLINE)
│       ├── Sensor.java           # Capteur avec dérive temporelle réaliste et injection d'anomalies
│       ├── SensorType.java       # TEMPERATURE, PRESSURE, VIBRATION, SPEED, ENERGY, LOAD
│       ├── SensorReading.java    # Relevé instantané d'un capteur (valeur, unité, timestamp)
│       ├── Threshold.java        # Seuils d'évaluation (Warning, Critique)
│       ├── IndustrialEvent.java  # Événement de supervision
│       ├── EventType.java        # Typologie des événements (avec EMERGENCY_STOP)
│       └── AlarmLevel.java       # Gravité (INFO, WARNING, CRITICAL)
│
├── network/
│   ├── MulticastManager.java     # Couche réseau UDP Multicast moderne (joinGroup SocketAddress/NetworkInterface)
│   ├── MessageListener.java      # Interface de callback avec transmission de la taille en octets
│   └── MessageParser.java        # Décodeur robuste (TELEMETRY, HEARTBEAT, ALARM, SHAPE)
│
├── control/                      # Phase 4 : Canal de contrôle TCP fiable
│   ├── ControlCommandType.java   # Types de commandes (START, STOP, EMERGENCY_STOP, RESET, MAINTENANCE, RESUME)
│   ├── ControlStatus.java        # Statuts de réponse (ACCEPTED, REJECTED, INVALID, TIMEOUT)
│   ├── ControlCommand.java       # Trame de commande : CMD,commandId,machineId,commandType,timestamp
│   ├── ControlResponse.java      # Trame ACK : ACK,commandId,machineId,commandType,status,message,timestamp
│   ├── ControlCommandHandler.java # Interface de traitement des commandes
│   ├── IndustrialCommandHandler.java # Validation de machine à états, audit SQLite et exécution
│   ├── ControlServer.java        # Serveur TCP multithreadé (port 5556)
│   └── ControlClient.java        # Client TCP robuste avec gestion de timeouts et reconnexion
│
├── persistence/                  # Phase 5 : Persistance locale SQLite et historisation
│   ├── DatabaseConfig.java       # Configuration centralisée du chemin SQLite (data/industrial_supervision.db)
│   ├── DatabaseManager.java      # Gestionnaire de connexions JDBC SQLite (WAL mode, timeouts)
│   ├── DatabaseInitializer.java  # Initialisation idempotente du schéma (CREATE TABLE & INDEX IF NOT EXISTS)
│   ├── MachineRepository.java    # Table machines (id, machine_id, name, type, state, last_seen)
│   ├── TelemetryRepository.java  # Table telemetry (id, timestamp, machine_id, sensor_type, value, unit, sequence_number)
│   ├── AlarmRepository.java      # Table alarms (id, timestamp, machine_id, level, event_type, sensor_type, value, description)
│   ├── CommandRepository.java    # Table commands (id, command_id, timestamp, machine_id, command_type, status, message)
│   ├── EventRepository.java      # Table events (id, timestamp, machine_id, event_type, description)
│   └── PersistenceService.java   # Façade asynchrone non-bloquante (BlockingQueue + Worker Thread dédié)
│
├── rmi/                          # Phase 4 & 5 : Supervision distante Java RMI
│   ├── RemoteMonitoringService.java     # Interface distante RMI (temps réel + historique SQLite)
│   ├── RemoteMonitoringServiceImpl.java # Implémentation déléguant à IndustrialSupervisor et PersistenceService
│   ├── RmiServer.java            # Publication et cycle de vie du registre RMI (port 1099)
│   ├── RmiClient.java            # Client RMI d'interrogation distante
│   └── RmiClientMain.java        # Client console de démonstration RMI
│
├── industrial/
│   ├── MachineSimulator.java     # Simulateur multi-machines concurrent (ScheduledExecutorService)
│   ├── IndustrialSupervisor.java # Superviseur temps réel : traitement télémétrie, état mémoire et dispatch persistence
│   ├── HeartbeatMonitor.java     # Détection de présence, timeouts d'inactivité (>6s) et retour en ligne
│   └── FaultInjector.java        # Module d'injection d'anomalies (surchauffe, vibrations, coupure heartbeat)
│
├── monitoring/
│   ├── SequenceTracker.java      # Suivi des numéros de séquence et détection de sauts (gaps)
│   ├── NetworkStatistics.java    # Instantané immuable des métriques réseau (débit, paquets/s, pertes, latence)
│   └── NetworkMonitor.java       # Moteur de calcul en temps réel des métriques réseau applicatives
│
├── ui/
│   ├── DrawingPanel.java         # Canevas vectoriel antialiasing du TP initial
│   ├── ReceiverFrame.java        # IHM réceptrice du TP de formes
│   ├── SenderFrame.java          # IHM émettrice du TP (+ flux continu)
│   │
│   └── industrial/
│       ├── IndustrialDashboard.java     # Fenêtre principale SCADA Swing (UDP + TCP + RMI + SQLite)
│       ├── MachineStatusIndicator.java  # Badge d'état avec voyant LED coloré
│       ├── MachinePanel.java            # Panneau de sélection et vue d'ensemble du parc de machines
│       ├── MachineControlPanel.java     # Panneau de commande TCP avec bouton EMERGENCY STOP
│       ├── SensorPanel.java             # Vue détaillée des capteurs avec jauges dynamiques et seuils
│       ├── AlarmPanel.java              # Journal des alarmes et événements temps réel en mémoire (max 500)
│       ├── NetworkHealthPanel.java      # Surveillance de la santé réseau (débit Ko/s, cadence, pertes, latence)
│       ├── DatabaseHistoryPanel.java    # Onglet SCADA d'audit et d'historique SQLite asynchrone
│       └── FaultInjectionPanel.java     # Panneau de déclenchement des pannes et tests en direct
│
├── test/
│   ├── Phase1Verification.java      # 5 tests : Architecture & TP Multicast
│   ├── Phase2Verification.java      # 15 tests : Télémétrie, capteurs, heartbeat et pannes
│   ├── Phase3Verification.java      # 16 tests : Dashboard, métriques réseau et IHM
│   ├── Phase4Verification.java      # 24 tests : Canal TCP, ACK, State machine & RMI
│   ├── Phase5Verification.java      # 24 tests : Persistance SQLite, audit TCP, robustesse & rétention
│   ├── Phase6Verification.java      # 16 tests : Infrastructure Réseau Cisco, Adressage, TTL & Résilience
│   ├── RealTimeIntegrationDemo.java # Démonstration temps réel des 3 protocoles simultanés
│   ├── RealTimePersistenceDemo.java # Démonstration complète persistance avec arrêt/redémarrage
│   └── RealTimeNetworkDemo.java     # Démonstration réseau Cisco, perturbations et reprise nominale
│
├── IndustrialMain.java           # Point d'entrée console interactif / démo
├── Main.java                     # Lanceur unifié (Menu interactif : Dashboard SCADA, Démo 3 canaux, RMI, TP)
├── MultiCastReceiver.java        # Lanceur récepteur racine
└── MultiCastSender.java          # Lanceur émetteur racine
```

---

## ⚡ Protocole TCP de Contrôle Industriel (Phase 4)

Le canal de contrôle TCP point-à-point (`localhost:5556`) garantit l'acheminement fiable et synchrone des commandes opérationnelles :

### 1. Format des trames de commande (Client ➔ Serveur)
```text
CMD,commandId,machineId,commandType,timestamp
```
*Exemple :* `CMD,cmd-101,M01,START,1728051234567`

### 2. Format des trames d'accusé de réception (Serveur ➔ Client)
```text
ACK,commandId,machineId,commandType,status,message,timestamp
```
*Exemples :*
- `ACK,cmd-101,M01,START,ACCEPTED,MACHINE_STARTED,1728051234600`
- `ACK,cmd-102,M02,START,REJECTED,MACHINE_ALREADY_RUNNING (RUNNING),1728051234710`

### 3. Matrice de validation des états
| Commande | État initial valide | État résultant | Réaction / UDP |
| :--- | :--- | :--- | :--- |
| **`START`** | `STOPPED` | `RUNNING` | Émission Heartbeat `RUNNING` |
| **`STOP`** | `RUNNING`, `WARNING`, `CRITICAL` | `STOPPED` | Émission Heartbeat `STOPPED` |
| **`EMERGENCY_STOP`** | *Tous états* | `FAILURE` | Émission Alarme UDP Critique + Heartbeat |
| **`RESET`** | `FAILURE`, `CRITICAL`, `STOPPED` | `STOPPED` | Rétablissement capteurs + Heartbeat |
| **`MAINTENANCE`**| `STOPPED`, `FAILURE` | `MAINTENANCE` | Isolation machine + Heartbeat |
| **`RESUME`** | `MAINTENANCE` | `STOPPED` | Sortie maintenance + Heartbeat |

---

## 🌐 Infrastructure Réseau Cisco (Phase 6)

L'infrastructure réseau industrielle d'**InduTwin** a été modélisée et validée pour environnement Cisco (Cisco Packet Tracer / Catalyst & IOS Routers) :

### 1. Segmentation & Adressage
- **VLAN 10 — INDUSTRIAL (`192.168.10.0/24`)** : Dédié aux automates et machines `M01`, `M02`, `M03`.
- **VLAN 20 — SUPERVISION (`192.168.20.0/24`)** : Dédié au poste de supervision SCADA, serveur RMI et audit SQLite.
- **Router-on-a-Stick** : Sous-interfaces `g0/0.10` (`192.168.10.1`) et `g0/0.20` (`192.168.20.1`) avec encapsulation `802.1Q`.
- **Trunking** : Liaison 802.1Q agrégée entre le switch `SW1` (`Gi0/1`) et le routeur `R1` (`Gi0/0`).

### 2. Multicast, IGMP & PIM
- **Groupe Multicast** : `224.1.1.1` (Port `5555`, TTL >= 4).
- **IGMP Snooping & Querier** : Activé sur `SW1-DISTRIB` pour restreindre la diffusion des trames multicast aux seuls ports souscripteurs et éliminer le flooding.
- **Routage Multicast PIM** : `ip multicast-routing` et `ip pim sparse-mode` configurés sur `R1-INDUS` avec Rendezvous Point (RP) pour relayer les flux télémétriques inter-VLAN.

### 3. Contrôle d'Accès par ACLs Étendues
- **Commandes TCP (`5556`)** : Autorisées exclusivement depuis l'adresse IP du superviseur SCADA (`192.168.20.10`) vers les machines (`192.168.10.0/24`).
- **Supervision RMI (`1099`)** : Confinée au VLAN de supervision 20 ; accès direct depuis l'atelier formellement bloqué.
- **UDP Multicast (`224.1.1.1:5555`)** : Autorisé pour la télémétrie et les alarmes.
- **Rejet par Défaut** : `deny ip any any log` pour tous les autres flux inter-VLAN non sollicités.

### 4. Qualité de Service Industrielle (QoS MQC)
- **Classe Contrôle Critique (TCP 5556 & EMERGENCY STOP)** : Marquage DSCP `CS6` / File prioritaire stricte **LLQ (Low Latency Queuing)** garantie à 30% de la bande.
- **Classe Télémétrie Industrielle (UDP 5555)** : Marquage DSCP `AF21` / File garantie **CBWFQ (Class-Based Weighted Fair Queuing)** à 40% de la bande passante.
- **Trafic Standard** : Best effort partagé (`fair-queue`).

Documentation réseau complète disponible dans `docs/network/` :
- [`topology.md`](docs/network/topology.md) : Architecture physique et logique, inventaire matériel et câblage.
- [`addressing.md`](docs/network/addressing.md) : Plan d'adressage IP complet, passerelles, masques et ports.
- [`vlan.md`](docs/network/vlan.md) : Détail des VLANs 10 & 20, Trunk 802.1Q et Router-on-a-stick.
- [`multicast.md`](docs/network/multicast.md) : Multicast 224.1.1.1, IGMPv2, IGMP Snooping, Querier et PIM Sparse-Mode.
- [`acl.md`](docs/network/acl.md) : Matrice de flux et listes de contrôle d'accès étendues.
- [`qos.md`](docs/network/qos.md) : Classification, marquage DSCP CS6/AF21 et ordonnancement LLQ/CBWFQ.
- [`tests.md`](docs/network/tests.md) : Cahier de recette et commandes de vérification Cisco IOS.
- Scripts IOS : [`R1_Router_Config.ios`](docs/network/configs/R1_Router_Config.ios) et [`SW1_Switch_Config.ios`](docs/network/configs/SW1_Switch_Config.ios).

---

## 🛰️ Supervision Distante Java RMI (Phase 4 & 5)

Le service RMI (`localhost:1099/IndustrialSupervisionService`) expose une API complète combinant données temps réel et historique persistant :

### Méthodes Temps Réel (Mémoire) :
- `getMachineStatus(String machineId)` : État courant d'une machine (`RUNNING`, `STOPPED`, etc.).
- `getAllMachineStatuses()` : Dictionnaire complet de l'ensemble du parc.
- `getActiveAlarms()` : Liste consolidée des alertes en cours.
- `getNetworkStatistics()` : Synthèse des métriques réseau (débit, paquets/s, pertes, statut de santé).
- `getRecentEvents(int limit)` : N derniers événements en mémoire vive.

### Méthodes Historiques (SQLite) :
- `getHistoricAlarms(int limit)` : Consultation des alarmes historisées en base.
- `getRecentCommands(int limit)` : Audit des commandes TCP exécutées/rejetées.
- `getMachineHistory(String machineId, int limit)` : Reconstitution chronologique complète d'un équipement.
- `getDatabaseStatistics()` : Compteurs globaux (télémétries, alarmes, commandes par statut).

---

## 🚀 Guide d'Exécution

### 1. Lancer le Dashboard SCADA Complet (Swing + UDP + TCP + RMI + SQLite)
```powershell
java -cp "lib/*;bin" Main
```
*Sélectionnez l'option 0 : **"Dashboard SCADA Industriel (IHM Swing)"**.*
Le dashboard intègre le panneau de commande TCP, le panneau de santé réseau, et l'onglet **"Historique & Audit Persistant (SQLite)"**.

### 2. Lancer la Démonstration Réseau Complète (Phase 6)
```powershell
java -cp "lib/*;bin" test.RealTimeNetworkDemo
```
Valide en direct l'insertion réseau, le débit en régime établi, les commandes de contrôle fiables, l'arrêt d'urgence prioritaire, la simulation de coupure de lien (Heartbeat loss), la détection `OFFLINE`, la restauration et la reprise nominale.

### 3. Lancer la Démonstration de Persistance & Historisation (Phase 5)
```powershell
java -cp "lib/*;bin" test.RealTimePersistenceDemo
```

### 4. Lancer le Client Console de Supervision RMI
```powershell
java -cp "lib/*;bin" rmi.RmiClientMain
```

### 5. Exécuter l'ensemble des suites de tests automatisées (100 tests, 100% sans échec)
```powershell
# Phase 1 : Validation architecture découplée & Multicast (5 tests)
java -cp "lib/*;bin" test.Phase1Verification

# Phase 2 : Validation modèle industriel, capteurs et dérive temporelle (15 tests)
java -cp "lib/*;bin" test.Phase2Verification

# Phase 3 : Validation Dashboard, calculs réseau, gaps et IHM Swing (16 tests)
java -cp "lib/*;bin" test.Phase3Verification

# Phase 4 : Validation Canal TCP, ACK, State machine & Java RMI (24 tests)
java -cp "lib/*;bin" test.Phase4Verification

# Phase 5 : Validation Persistance SQLite, audit TCP, concurrence & reprise (24 tests)
java -cp "lib/*;bin" test.Phase5Verification

# Phase 6 : Validation Infrastructure Réseau Cisco, Adressage, TTL & Résilience (16 tests)
java -cp "lib/*;bin" test.Phase6Verification
```

---

## 🗺️ Feuille de Route du Projet (Roadmap)

| Phase | Description | Statut |
| :---: | :--- | :---: |
| **Phase 1** | **Architecture & Découplage UDP Multicast (Refactorisation propre du TP)** | ✅ **Terminé** (5/5 tests) |
| **Phase 2** | **Télémétrie Industrielle, Capteurs, Heartbeat, Machine à états & Simulateur** | ✅ **Terminé** (15/15 tests) |
| **Phase 3** | **Dashboard de Supervision Graphique SCADA & Métriques Réseau (KPI, Débit, Pertes)** | ✅ **Terminé** (16/16 tests) |
| **Phase 4** | **Canal de Contrôle Fiable TCP (Commandes/ACK) & Supervision Distante Java RMI** | ✅ **Terminé** (24/24 tests) |
| **Phase 5** | **Persistance Locale SQLite (JDBC), Audit TCP, File Asynchrone & Historisation** | ✅ **Terminé** (24/24 tests) |
| **Phase 6** | **Infrastructure Réseau Cisco (VLANs, IGMP Snooping, PIM, ACLs & QoS)** | ✅ **Terminé** (16/16 tests) |
| **Phase 7** | **Analyse de Trames Réseau Wireshark (Captures PCAP, Jitter, Perte de paquets)** | ⏳ *À venir* |
| **Phase 8** | **Scénarios Avancés & Injection de Pannes Réseau (Latence, Drops)** | ⏳ *À venir* |
| **Phase 9** | **Module Optionnel d'Analyse / Détection d'Anomalies** | ⏳ *À venir* |
