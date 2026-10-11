# CONFIGURATION DES VLANS & ROUTAGE INTER-VLAN — INDUTWIN (PHASE 6)

## 1. Objectifs de la Segmentation

La segmentation par VLAN répond à trois exigences fondamentales de l'architecture industrielle :
1. **Isolation des Domaines de Diffusion (Broadcast Domains)** : Empêcher les tempêtes de diffusion ou le bruit de fond bureautique de perturber les automates et capteurs d'atelier.
2. **Cloisonnement de Sécurité** : Restreindre les flux d'atelier à la seule communication multicast légitime et aux commandes de contrôle strictement autorisées par le superviseur.
3. **Optimisation des Performances** : Faciliter l'application de politiques QoS dédiées et le filtrage IGMP Snooping sur chaque segment.

---

## 2. Configuration du Switch Cisco Catalyst (SW1)

### Création des VLANs
```cisco
SW1> enable
SW1# configure terminal
SW1(config)# hostname SW1-DISTRIB

! 1. Création des VLANs
SW1(config)# vlan 10
SW1(config-vlan)# name INDUSTRIAL_PLANT
SW1(config-vlan)# exit

SW1(config)# vlan 20
SW1(config-vlan)# name SCADA_SUPERVISION
SW1(config-vlan)# exit
```

### Configuration des Ports d'Accès (Access Ports)
```cisco
! 2. Affectation des machines industrielles au VLAN 10
SW1(config)# interface range FastEthernet0/1 - 3
SW1(config-if-range)# switchport mode access
SW1(config-if-range)# switchport access vlan 10
SW1(config-if-range)# spanning-tree portfast
SW1(config-if-range)# description Hotes Industriels M01-M02-M03
SW1(config-if-range)# no shutdown
SW1(config-if-range)# exit

! 3. Affectation des postes de supervision au VLAN 20
SW1(config)# interface range FastEthernet0/10 - 12
SW1(config-if-range)# switchport mode access
SW1(config-if-range)# switchport access vlan 20
SW1(config-if-range)# spanning-tree portfast
SW1(config-if-range)# description Postes Supervision SCADA et RMI
SW1(config-if-range)# no shutdown
SW1(config-if-range)# exit
```

### Configuration du Port Trunk 802.1Q vers le Routeur
```cisco
! 4. Liaison Trunk vers le routeur R1
SW1(config)# interface GigabitEthernet0/1
SW1(config-if)# switchport mode trunk
SW1(config-if)# switchport trunk allowed vlan 10,20
SW1(config-if)# description Trunk vers R1 Gi0/0
SW1(config-if)# no shutdown
SW1(config-if)# exit

! 5. Interface d'administration du switch dans le VLAN 20
SW1(config)# interface vlan 20
SW1(config-if)# ip address 192.168.20.2 255.255.255.0
SW1(config-if)# no shutdown
SW1(config-if)# exit
SW1(config)# ip default-gateway 192.168.20.1
```

---

## 3. Configuration du Routeur Cisco (R1) — Router-on-a-Stick

Le routage inter-VLAN s'appuie sur une interface physique découpée en sous-interfaces 802.1Q :

```cisco
R1> enable
R1# configure terminal
R1(config)# hostname R1-INDUS

! 1. Activation de l'interface physique racine (sans IP directe)
R1(config)# interface GigabitEthernet0/0
R1(config-if)# no ip address
R1(config-if)# no shutdown
R1(config-if)# description Liaison Trunk vers SW1 Gi0/1
R1(config-if)# exit

! 2. Sous-interface VLAN 10 (Industrial)
R1(config)# interface GigabitEthernet0/0.10
R1(config-subif)# encapsulation dot1Q 10
R1(config-subif)# ip address 192.168.10.1 255.255.255.0
R1(config-subif)# description Passerelle Atelier VLAN 10
R1(config-subif)# no shutdown
R1(config-subif)# exit

! 3. Sous-interface VLAN 20 (Supervision)
R1(config)# interface GigabitEthernet0/0.20
R1(config-subif)# encapsulation dot1Q 20
R1(config-subif)# ip address 192.168.20.1 255.255.255.0
R1(config-subif)# description Passerelle Supervision VLAN 20
R1(config-subif)# no shutdown
R1(config-subif)# exit
```

---

## 4. Commandes de Vérification Cisco IOS

```cisco
! Vérification des VLANs sur le switch
show vlan brief

! Vérification de la liaison Trunk
show interfaces trunk

! Vérification des sous-interfaces et de la table de routage sur le routeur
show ip interface brief
show ip route
```

### Résultat attendu sur `show ip route` :
```text
Gateway of last resort is not set

C    192.168.10.0/24 is directly connected, GigabitEthernet0/0.10
L    192.168.10.1/32 is directly connected, GigabitEthernet0/0.10
C    192.168.20.0/24 is directly connected, GigabitEthernet0/0.20
L    192.168.20.1/32 is directly connected, GigabitEthernet0/0.20
```

---

## 5. Scripts Associés

Les fichiers de configuration Cisco IOS prêts à l'injection sont disponibles dans le dossier `configs/` :
- Switch SW1 : [`SW1_Switch_Config.ios`](configs/SW1_Switch_Config.ios)
- Routeur R1 : [`R1_Router_Config.ios`](configs/R1_Router_Config.ios)
