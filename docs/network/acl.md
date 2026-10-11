# LISTES DE CONTRÔLE D'ACCÈS (ACL) & SÉCURITÉ INDUSTRIELLE — INDUTWIN (PHASE 6)

## 1. Politique de Sécurité Réseau

L'objectif de la politique ACL (Access Control List) est d'appliquer le principe du **moindre privilège** entre la zone de supervision et la zone d'atelier :
1. **Canal TCP de Contrôle (`5556`)** : Strictement autorisé depuis le poste de supervision (`192.168.20.10`) vers les équipements industriels (`192.168.10.0/24`). Tout autre hôte tentant d'envoyer des commandes de contrôle est rejeté.
2. **Supervision Distante RMI (`1099`)** : Autorisée uniquement au sein du VLAN de supervision (`192.168.20.0/24`). Les machines de l'atelier ne doivent pas accéder au service RMI.
3. **Flux Multicast UDP (`224.1.1.1:5555`)** : Autorisé depuis les machines vers le groupe multicast.
4. **Diagnostic ICMP (Echo & Echo-Reply)** : Autorisé pour permettre aux exploitants de diagnostiquer la joignabilité réseau (`ping`).
5. **Rejet par défaut (Explicit Deny with Logging)** : Tout autre flux inter-VLAN non explicitement permis est bloqué et journalisé.

---

## 2. Configuration Cisco IOS des ACLs Étendues

### ACL sur le Routeur R1
```cisco
! 1. Définition de l'ACL étendue pour le trafic entrant depuis la supervision (VLAN 20)
ip access-list extended ACL_SUPERVISION_IN

 ! Règle 1 : Autoriser les commandes de contrôle TCP vers les machines
 permit tcp host 192.168.20.10 192.168.10.0 0.0.0.255 eq 5556

 ! Règle 2 : Autoriser le ping vers l'atelier pour supervision ICMP
 permit icmp host 192.168.20.10 192.168.10.0 0.0.0.255 echo
 permit icmp host 192.168.20.10 192.168.10.0 0.0.0.255 echo-reply

 ! Règle 3 : Autoriser le trafic de retour pour les connexions établies
 permit tcp 192.168.20.0 0.0.0.255 192.168.10.0 0.0.0.255 established

 ! Règle 4 : Interdire tout autre trafic direct non autorisé depuis la supervision
 deny ip any 192.168.10.0 0.0.0.255 log
 permit ip any any
exit

! 2. Définition de l'ACL étendue pour le trafic entrant depuis l'atelier (VLAN 10)
ip access-list extended ACL_INDUSTRIAL_IN

 ! Règle 1 : Autoriser la télémétrie UDP Multicast vers le groupe 224.1.1.1 port 5555
 permit udp 192.168.10.0 0.0.0.255 host 224.1.1.1 eq 5555

 ! Règle 2 : Autoriser les paquets IGMP pour l'adhésion multicast
 permit igmp 192.168.10.0 0.0.0.255 any

 ! Règle 3 : Autoriser les réponses ACK TCP (port source 5556 vers supervision)
 permit tcp 192.168.10.0 0.0.0.255 host 192.168.20.10 established

 ! Règle 4 : Autoriser les réponses ping ICMP
 permit icmp 192.168.10.0 0.0.0.255 host 192.168.20.10 echo-reply

 ! Règle 5 : Bloquer toute tentative d'accès de l'atelier vers le serveur RMI (port 1099)
 deny tcp 192.168.10.0 0.0.0.255 host 192.168.20.10 eq 1099 log

 ! Règle 6 : Interdire tout autre accès direct vers le réseau de supervision
 deny ip 192.168.10.0 0.0.0.255 192.168.20.0 0.0.0.255 log
 permit ip any any
exit
```

### Application des ACLs sur les Sous-interfaces du Routeur R1
```cisco
R1-INDUS(config)# interface GigabitEthernet0/0.10
R1-INDUS(config-subif)# ip access-group ACL_INDUSTRIAL_IN in
R1-INDUS(config-subif)# exit

R1-INDUS(config)# interface GigabitEthernet0/0.20
R1-INDUS(config-subif)# ip access-group ACL_SUPERVISION_IN in
R1-INDUS(config-subif)# exit
```

---

## 3. Matrice de Validation des Règles ACL

| Test de Sécurité | Source | Destination | Protocole / Port | Action Attendue | Résultat |
| :--- | :--- | :--- | :---: | :---: | :---: |
| **Commande TCP SCADA** | `192.168.20.10` | `192.168.10.11` (M01) | `TCP 5556` | **PERMIT** | Validé |
| **Arrêt d'Urgence TCP** | `192.168.20.10` | `192.168.10.12` (M02) | `TCP 5556` | **PERMIT** | Validé |
| **Télémétrie Multicast** | `192.168.10.11` | `224.1.1.1` | `UDP 5555` | **PERMIT** | Validé |
| **Commande non autorisée**| `192.168.20.99` (Hôte inconnu)| `192.168.10.11` | `TCP 5556` | **DENY** | Bloqué |
| **Accès Atelier vers RMI**| `192.168.10.11` | `192.168.20.10` | `TCP 1099` | **DENY** | Bloqué |
| **Trafic arbitraire inter-VLAN**| `192.168.10.13`| `192.168.20.20` | `TCP 80 / 443 / SMB` | **DENY** | Bloqué |

---

## 4. Commandes de Vérification Cisco IOS

```cisco
! Affichage des listes de contrôle d'accès et des compteurs de correspondances (matches)
show access-lists

! Vérification de l'application sur les sous-interfaces
show ip interface GigabitEthernet0/0.10 | include Inbound access list
show ip interface GigabitEthernet0/0.20 | include Inbound access list
```

---

## 5. Script Associé

La configuration complète des ACLs est intégrée dans le script du routeur :
- Routeur R1 : [`R1_Router_Config.ios`](configs/R1_Router_Config.ios)
