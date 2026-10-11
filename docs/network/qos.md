# QUALITÉ DE SERVICE (QoS) INDUSTRIELLE — INDUTWIN (PHASE 6)

## 1. Philosophie & Contraintes de la QoS Industrielle

En environnement SCADA / Digital Twin, la perte de données ou une latence excessive sur un ordre critique peut entraîner des avaries matérielles ou des accidents humains.

### Hiérarchie des Priorités Métier
1. **Priorité Maximale (Classe Contrôle Critique - Network Control / Signaling)** :
   - Flux : **Commandes TCP de contrôle (Port `5556`)**, en particulier l'**Arrêt d'Urgence (`EMERGENCY_STOP`)**.
   - Objectif QoS : **Gigue nulle, latence minimale, 0% de perte**.
   - Traitement Cisco : File de priorité stricte (**Low Latency Queuing - LLQ**), marquage DSCP **`CS6`** (IP Precedence 6).
2. **Priorité Normale (Classe Télémétrie Industrielle - Critical Data)** :
   - Flux : **Télémétrie capteurs et Heartbeats UDP (Port `5555`)**.
   - Objectif QoS : **Bande passante garantie, cadence stable**.
   - Traitement Cisco : File pondérée garantie (**Class-Based Weighted Fair Queuing - CBWFQ**), marquage DSCP **`AF21`** (Assured Forwarding Class 2).
3. **Priorité Standard (Classe Best Effort - Default)** :
   - Flux : Consultations RMI, requêtes ICMP ping, synchronisation administrative.
   - Traitement Cisco : File standard sans garantie de bande passante (`class-default`).

---

## 2. Limite Technique & Honnêteté Applicative

> **Remarque importante d'ingénierie réseau** :
> Au niveau réseau IP/Ethernet pur (Couches 3 et 4), un paquet TCP port `5556` transportant un `START` ou un `EMERGENCY_STOP` possède les mêmes en-têtes IP/TCP.
> Le routeur Cisco classe et priorise l'ensemble du flux **TCP port 5556** dans la file prioritaire LLQ.
> La distinction fine entre `START` et `EMERGENCY_STOP` est effectuée au niveau applicatif par le protocole Java InduTwin dans [`IndustrialCommandHandler.java`](file:///c:/Users/pc/OneDrive/Desktop/4eme_année/CISCO/src/control/IndustrialCommandHandler.java).

---

## 3. Configuration Cisco IOS Modular QoS CLI (MQC)

La mise en œuvre repose sur les 3 étapes normalisées Cisco MQC : **Classification (`class-map`)**, **Marquage/Action (`policy-map`)**, et **Application (`service-policy`)**.

```cisco
! ==================================================================
! ÉTAPE 1 : CLASSIFICATION DES FLUX (CLASS-MAP)
! ==================================================================

! 1.1 ACLs de matching pour la classification
ip access-list extended ACL_MATCH_CONTROL
 permit tcp any any eq 5556
 permit tcp any eq 5556 any
exit

ip access-list extended ACL_MATCH_TELEMETRY
 permit udp any host 224.1.1.1 eq 5555
 permit udp any any eq 5555
exit

! 1.2 Définition des classes de trafic
class-map match-any CM_CRITICAL_CONTROL
 match access-group name ACL_MATCH_CONTROL
exit

class-map match-any CM_INDUSTRIAL_TELEMETRY
 match access-group name ACL_MATCH_TELEMETRY
exit

! ==================================================================
! ÉTAPE 2 : POLITIQUE D'ORDONNANCEMENT ET DE BANDE PASSANTE (POLICY-MAP)
! ==================================================================

policy-map PM_INDUSTRIAL_QOS
 ! Classe 1 : Contrôle Critique (LLQ - File prioritaire stricte à 30% de la bande)
 class CM_CRITICAL_CONTROL
  set dscp cs6
  priority percent 30

 ! Classe 2 : Télémétrie Industrielle (CBWFQ - Bande garantie à 40% sans famine)
 class CM_INDUSTRIAL_TELEMETRY
  set dscp af21
  bandwidth percent 40

 ! Classe 3 : Trafic par défaut (Best Effort - 30% restant partagé équitablement)
 class class-default
  set dscp default
  fair-queue
exit

! ==================================================================
! ÉTAPE 3 : APPLICATION SUR LES INTERFACES ROUTEUR (SERVICE-POLICY)
! ==================================================================

R1-INDUS(config)# interface GigabitEthernet0/0.10
R1-INDUS(config-subif)# service-policy output PM_INDUSTRIAL_QOS
R1-INDUS(config-subif)# exit

R1-INDUS(config)# interface GigabitEthernet0/0.20
R1-INDUS(config-subif)# service-policy output PM_INDUSTRIAL_QOS
R1-INDUS(config-subif)# exit
```

---

## 4. Commandes de Diagnostic QoS Cisco IOS

```cisco
! Affichage des statistiques de files d'attente et paquets classifiés
show policy-map interface GigabitEthernet0/0.10
show policy-map interface GigabitEthernet0/0.20

! Vérification des class-maps
show class-map

! Vérification des policy-maps
show policy-map PM_INDUSTRIAL_QOS
```

### Exemple de sortie attendue :
```text
GigabitEthernet0/0.10 

  Service-policy output: PM_INDUSTRIAL_QOS

    Class-map: CM_CRITICAL_CONTROL (match-any)
      34 packets, 3128 bytes
      5 minute offered rate 0000 bps, drop rate 0000 bps
      Match: access-group name ACL_MATCH_CONTROL
      Priority: 30% (300000 kbps), burst bytes 7500000, drops 0

    Class-map: CM_INDUSTRIAL_TELEMETRY (match-any)
      512 packets, 47104 bytes
      5 minute offered rate 14000 bps, drop rate 0000 bps
      Match: access-group name ACL_MATCH_TELEMETRY
      Queueing
      Output Queue: Conversation 265
      Bandwidth 40% (400000 kbps)
      (pkts matched/bytes matched) 512/47104
      (depth/total drops/no-buffer drops) 0/0/0

    Class-map: class-default (match-default)
      18 packets, 1440 bytes
      5 minute offered rate 0000 bps, drop rate 0000 bps
      Match: any
      Queueing
      Flow Based Fair Queueing
      Maximum Number of Hashed Queues 256
      (total queued/total drops/no-buffer drops) 0/0/0
```

---

## 5. Script Associé

La configuration complète MQC (class-maps et policy-maps) est intégrée dans le script du routeur :
- Routeur R1 : [`R1_Router_Config.ios`](configs/R1_Router_Config.ios)
