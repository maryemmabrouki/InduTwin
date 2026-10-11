# MULTICAST INDUSTRIEL, IGMP & ROUTAGE MULTICAST — INDUTWIN (PHASE 6)

## 1. Principes du Multicast dans InduTwin

Dans le système **InduTwin**, les machines industrielles (`M01`, `M02`, `M03`) utilisent le mode **One-to-Many** pour diffuser leurs flux continus :
- **Télémétrie capteurs** (Température, Pression, Vibration, etc.) à 1 Hz.
- **Heartbeat machine** à 0.5 Hz.
- **Alarmes et anomalies** transmises instantanément.

L'utilisation d'UDP Multicast sur l'adresse de groupe **`224.1.1.1` (Port `5555`)** évite la multiplication des flux Unicast (économie de bande passante et CPU sur les automates).

---

## 2. Rôle du Protocole IGMP (Internet Group Management Protocol)

### Mécanisme d'Adhésion
1. Le superviseur (`SUPERVISOR-PC` / `IndustrialSupervisor`) émet un paquet **`IGMPv2 Membership Report`** à destination du groupe `224.1.1.1` dès son démarrage (`joinGroup`).
2. Périodiquement, le routeur ou le querier émet des paquets **`IGMP General Query`** (`224.0.0.1`) pour vérifier si des membres sont toujours actifs.
3. À la fermeture du superviseur, un paquet **`IGMP Leave Group`** (`224.0.0.2`) signale son départ.

### Rôle d'IGMP Snooping sur le Switch Catalyst
Sans IGMP Snooping, un switch traite les trames multicast comme du broadcast (flooding sur tous les ports du VLAN).
Avec **IGMP Snooping**, le switch SW1 analyse les échanges IGMP entre les hôtes et le routeur :
- Le flux multicast n'est commuté **que vers les ports ayant explicitement souscrit au groupe** (ici, le port de supervision `Fa0/10` et le port Trunk `Gi0/1`).
- Les autres ports (par exemple d'autres équipements du VLAN 20 non intéressés) sont épargnés.

---

## 3. Configuration Cisco IOS du Multicast

### Configuration d'IGMP Snooping sur le Switch SW1
```cisco
SW1-DISTRIB(config)# ip igmp snooping
SW1-DISTRIB(config)# ip igmp snooping vlan 10
SW1-DISTRIB(config)# ip igmp snooping vlan 20
SW1-DISTRIB(config)# ip igmp snooping querier
SW1-DISTRIB(config)# ip igmp snooping vlan 10 immediate-leave
SW1-DISTRIB(config)# ip igmp snooping vlan 20 immediate-leave
```

### Configuration du Routage Multicast sur le Routeur R1 (PIM-SM)
Pour permettre aux paquets émis par les machines dans le **VLAN 10** d'atteindre le superviseur dans le **VLAN 20**, le routeur doit acheminer les paquets multicast d'un sous-réseau à l'autre :

```cisco
! 1. Activation globale du routage multicast sur le routeur
R1-INDUS(config)# ip multicast-routing

! 2. Configuration du mode PIM Sparse-Mode sur les sous-interfaces
R1-INDUS(config)# interface GigabitEthernet0/0.10
R1-INDUS(config-subif)# ip pim sparse-mode
R1-INDUS(config-subif)# ip igmp version 2
R1-INDUS(config-subif)# exit

R1-INDUS(config)# interface GigabitEthernet0/0.20
R1-INDUS(config-subif)# ip pim sparse-mode
R1-INDUS(config-subif)# ip igmp version 2
R1-INDUS(config-subif)# exit

! 3. Déclaration du Rendezvous Point (RP) pour PIM Sparse-Mode
R1-INDUS(config)# ip pim rp-address 192.168.20.1
```

*Note technique sur Packet Tracer* : Selon la version de Cisco Packet Tracer (ex: 8.x / 9.0), certaines commandes PIM peuvent être simplifiées ou remplacées par `ip pim dense-mode` ou la redirection IGMP proxy. La configuration ci-dessus représente la norme IOS d'entreprise réelle.

---

## 4. Paramètres Applicatifs Java (TTL & Socket)

Dans le code Java d'InduTwin ([`MulticastManager.java`](file:///c:/Users/pc/OneDrive/Desktop/4eme_année/InduTwin/src/network/MulticastManager.java)) :
- La valeur du **TTL (Time-To-Live)** par défaut sur un datagramme Multicast standard est de `1` (limité au réseau local / même VLAN).
- Pour traverser le routeur inter-VLAN entre le VLAN 10 et le VLAN 20, le socket d'émission multicast (`MulticastSocket` ou `DatagramSocket`) configure son TTL à `4` ou supérieur :
  ```java
  multicastSocket.setTimeToLive(4);
  ```
- Cela permet au paquet de franchir le routeur `R1` sans être détruit par décrémentation à 0.

---

## 5. Commandes de Vérification Multicast Cisco IOS

```cisco
! Affichage des groupes multicast actifs reçus via IGMP
show ip igmp groups

! Affichage de la table de routage multicast
show ip mroute

! Affichage des voisins PIM
show ip pim neighbor

! Affichage des statistiques IGMP Snooping sur le switch
show ip igmp snooping groups
```

---

## 5. Scripts Associés

Les configurations Cisco IOS de routage multicast et de snooping sont réparties comme suit :
- Switch SW1 (IGMP Snooping) : [`SW1_Switch_Config.ios`](configs/SW1_Switch_Config.ios)
- Routeur R1 (PIM-SM & Routage) : [`R1_Router_Config.ios`](configs/R1_Router_Config.ios)
