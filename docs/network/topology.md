# TOPOLOGIE RÉSEAU CISCO — INDUTWIN (PHASE 6)

## 1. Vue d'Ensemble de l'Infrastructure

L'infrastructure réseau simulée d'**InduTwin** modélise une architecture réseau industriel conforme aux bonnes pratiques de segmentation **Purdue Model (ISA-95 / IEC 62443)** :
- **Niveau 1/2 (Atelier Industriel - Shop Floor)** : Machines virtuelles `M01`, `M02`, `M03` isolées dans le **VLAN 10 (INDUSTRIAL)**.
- **Niveau 3 (Supervision Usine - Operations Management)** : Postes de supervision SCADA, serveurs RMI et historisation SQLite dans le **VLAN 20 (SUPERVISION)**.
- **Séparation & Routage** : Assurés par un routeur Cisco en configuration **Router-on-a-Stick** avec contrôle des flux par **ACLs étendues** et ordonnancement **QoS (LLQ / CBWFQ)**.

---

## 2. Diagramme Topologique Réseau

```text
                                  ┌───────────────────────────────┐
                                  │      ROUTEUR CISCO (R1)       │
                                  │       Cisco 2911 / 4331       │
                                  │   (Router-on-a-Stick + PIM)   │
                                  └───────────────┬───────────────┘
                                                  │
                                                  │ Trunk 802.1Q (g0/0)
                                                  │ VLANs 10, 20
                                                  │
                                  ┌───────────────┴───────────────┐
                                  │     SWITCH CISCO (SW1)        │
                                  │     Catalyst 2960-24TT        │
                                  │   (IGMP Snooping Activé)      │
                                  └───┬───────────────────────┬───┘
                                      │                       │
           VLAN 10 : INDUSTRIAL       │                       │       VLAN 20 : SUPERVISION
             192.168.10.0/24          │                       │          192.168.20.0/24
      (Ports Access Fa0/1 - Fa0/3)    │                       │   (Ports Access Fa0/10 - Fa0/12)
       ┌──────────────┬───────────────┘                       └───────────────┬──────────────┐
       │              │              │                                        │              │
┌──────┴──────┐┌──────┴──────┐┌──────┴──────┐                          ┌──────┴──────┐┌──────┴──────┐
│     M01     ││     M02     ││     M03     │                          │ SUPERVISOR  ││ RMI-CLIENT  │
│   Presse    ││    Four     ││    Robot    │                          │  Dashboard  ││ Superviseur │
│ Hydraulique ││ Industriel  ││ Industriel  │                          │    SCADA    ││   Distant   │
│192.168.10.11││192.168.10.12││192.168.10.13│                          │192.168.20.10││192.168.20.20│
└─────────────┘└─────────────┘└─────────────┘                          └─────────────┘└─────────────┘
       │              │              │                                        ▲              ▲
       └──────────────┴──────────────┘                                        │              │
                      │                                                       │              │
                      └─── UDP Multicast 224.1.1.1:5555 (Télémétrie/Alarmes) ─┴──────────────┘
                      ▲
                      │─── TCP 5556 (Commandes de contrôle START / STOP / EMERGENCY_STOP) ───│
```

---

## 3. Matrice des Interconnexions Physiques & Logiques

| Équipement | Interface | Type de Lien | VLAN | Destination / Rôle |
| :--- | :--- | :--- | :---: | :--- |
| **R1 (Routeur)** | `GigabitEthernet0/0` | Trunk 802.1Q | 10, 20 | Vers SW1 `Gi0/1` (Agrégation des 2 VLANs) |
| **R1 (Routeur)** | `GigabitEthernet0/0.10` | Sous-interface | 10 | Passerelle par défaut VLAN 10 (`192.168.10.1/24`) |
| **R1 (Routeur)** | `GigabitEthernet0/0.20` | Sous-interface | 20 | Passerelle par défaut VLAN 20 (`192.168.20.1/24`) |
| **SW1 (Switch)** | `GigabitEthernet0/1` | Trunk 802.1Q | 10, 20 | Vers R1 `Gi0/0` |
| **SW1 (Switch)** | `FastEthernet0/1` | Access | 10 | Vers Station `M01` (Presse) |
| **SW1 (Switch)** | `FastEthernet0/2` | Access | 10 | Vers Station `M02` (Four) |
| **SW1 (Switch)** | `FastEthernet0/3` | Access | 10 | Vers Station `M03` (Robot) |
| **SW1 (Switch)** | `FastEthernet0/10` | Access | 20 | Vers Poste `SUPERVISOR-PC` (Dashboard SCADA) |
| **SW1 (Switch)** | `FastEthernet0/11` | Access | 20 | Vers Poste `RMI-CLIENT` (Audit & supervision) |

---

## 4. Matrice des Protocoles et Flux Industriels

| Flux Applicatif | Protocole Transport | Port | Source | Destination | Priorité QoS | Rôle Métier |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Télémétrie & Heartbeat** | UDP Multicast | `5555` | M01, M02, M03 | `224.1.1.1` (Groupe) | Normale (`AF21`) | Diffusion continue des mesures capteurs (1 Hz) et présence (0.5 Hz) |
| **Alarmes Industrielles** | UDP Multicast | `5555` | Machines / Supervisor | `224.1.1.1` (Groupe) | Haute (`CS6`) | Notification immédiate d'anomalie critique ou dépassement de seuil |
| **Contrôle / Ordres Machine**| TCP Unicast | `5556` | Supervisor SCADA | M01, M02, M03 | Critique (`CS6`) | Ordres synchrones fiables avec accusé ACK (`START`, `STOP`, `RESET`) |
| **Arrêt d'Urgence** | TCP Unicast | `5556` | Supervisor SCADA | Machine ciblée | Maximale (`CS6`) | Coupure immédiate `EMERGENCY_STOP` |
| **Supervision Distante** | TCP Unicast (RMI) | `1099` | RMI-CLIENT | SUPERVISOR-PC | Standard (`CS0`) | Consultation distante de l'état du parc et de l'historique SQLite |
| **Gestion Multicast** | IGMPv2 | - | Hôtes | Routeur / Switch | Contrôle réseau | Signalement d'adhésion (`Membership Report`) au groupe `224.1.1.1` |
