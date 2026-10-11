# CAHIER DE RECETTE & TESTS RÉSEAU CISCO — INDUTWIN (PHASE 6)

## 1. Vue d'Ensemble des Niveaux de Validation

Pour garantir une rigueur méthodologique totale, la validation de la Phase 6 distingue formellement les différents niveaux de validation du projet **InduTwin** :
1. **Tests Java réellement exécutés** : Tests automatisés exécutés sur la machine hôte via la suite Java `test.Phase6Verification`.
2. **Configurations Cisco IOS préparées** : Scripts de configuration prêts pour injection sur routeur et commutateur.
3. **Tests Packet Tracer validés** : Tests de connectivité et de topologie exécutés dans le simulateur.
4. **Tests restant à exécuter** : Vérifications dynamiques à mener manuellement dans Packet Tracer ou sur équipement réel.
5. **Fonctions non prises en charge & limites du simulateur** : Contraintes techniques propres à Cisco Packet Tracer.

---

## 2. Tests Java Réellement Exécutés (`test.Phase6Verification`)

La suite de vérification automatisée [`test.Phase6Verification`](file:///C:/Users/pc/OneDrive/Desktop/4eme_ann%C3%A9e/InduTwin/src/test/Phase6Verification.java) valide l'adéquation du code applicatif Java avec les spécifications de l'infrastructure réseau.

**Résultat global : 16 / 16 tests passés avec succès (0 échec).**

| Réf. | Intitulé du Test Java | Port / Protocole | Résultat Observé | Validation Applicative |
| :--- | :--- | :---: | :---: | :--- |
| **TEST 1** | Validation de l'adresse IP de groupe Multicast | `224.1.1.1` | **PASSÉ** | Adresse de classe D valide (`224.0.0.0/4`), reconnue multicast par la JVM. |
| **TEST 2** | Validation du port UDP Multicast | `UDP 5555` | **PASSÉ** | Port standard d'atelier, non privilégié utilisateur. |
| **TEST 3** | Validation du port TCP Contrôle | `TCP 5556` | **PASSÉ** | Port distinct du bus UDP, conforme aux ACLs réseau. |
| **TEST 4** | Validation du port Java RMI distant | `TCP 1099` | **PASSÉ** | Port standard du registre RMI pour supervision distante. |
| **TEST 5** | Validation du Time-To-Live Multicast (TTL) | `TTL = 4` | **PASSÉ** | Valeur TTL >= 4 autorisant le franchissement du routeur inter-VLAN R1. |
| **TEST 6** | Cohérence du plan d'adressage et VLANs | Subnets 10 & 20 | **PASSÉ** | Constantes Java alignées avec les sous-réseaux `192.168.10.0/24` et `192.168.20.0/24`. |
| **TEST 7** | Détection de l'interface réseau active | Multicast NIC | **PASSÉ** | Interface locale active UP détectée avec support multicast (`wlan0` / eth). |
| **TEST 8** | Connectivité de bout-en-bout TCP Contrôle | `TCP 5556` | **PASSÉ** | Échange réel `ControlClient` ➔ `ControlServer` avec acquittement formel `ACK`. |
| **TEST 9** | Connectivité de bout-en-bout Java RMI | `TCP 1099` | **PASSÉ** | Enregistrement, lookup et interrogation distante via `RmiClient` ➔ `RmiServer`. |
| **TEST 10** | Émission et écoute Multicast réelle | `224.1.1.1:5555` | **PASSÉ** | Datagrammes UDP émis et reçus sur socket multicast locale. |
| **TEST 11** | Discrimination des flux (Télémétrie vs Alarme) | `UDP 5555` | **PASSÉ** | Séparation hermétique des flux de télémétrie et d'alarmes critiques sans collision. |
| **TEST 12** | Détection de coupure réseau (Heartbeat timeout) | Logiciel | **PASSÉ** | Détection automatique d'absence de trame Heartbeat et passage en `OFFLINE`. |
| **TEST 13** | Rétablissement réseau (RECOVERED) | Logiciel | **PASSÉ** | Réception de signal après coupure déclenchant la réintégration nominale. |
| **TEST 14** | Métriques applicatives `NetworkMonitor` | Logiciel | **PASSÉ** | Calcul exact de la cadence (paquets/s) et du débit applicatif (Ko/s). |
| **TEST 15** | Suivi des numéros de séquence (`SequenceTracker`)| Logiciel | **PASSÉ** | Détection précise des sauts de séquence et estimation de la perte de paquets. |
| **TEST 16** | Concurrence simultanée des 3 canaux | `5555 / 5556 / 1099`| **PASSÉ** | Les 3 protocoles (UDP, TCP, RMI) fonctionnent simultanément sans interférence. |

---

## 3. Configurations Cisco IOS Préparées

Deux fichiers de configuration Cisco IOS complets ont été rédigés et préparés pour déploiement. Ils se trouvent dans le répertoire [`docs/network/configs/`](file:///C:/Users/pc/OneDrive/Desktop/4eme_ann%C3%A9e/InduTwin/docs/network/configs/) :

1. **Routeur Cisco R1 (`R1-INDUS`)** : [`docs/network/configs/R1_Router_Config.ios`](file:///C:/Users/pc/OneDrive/Desktop/4eme_ann%C3%A9e/InduTwin/docs/network/configs/R1_Router_Config.ios)
   - Configuration Router-on-a-Stick sur l'interface `GigabitEthernet0/0`.
   - Sous-interfaces d'encapsulation 802.1Q : `Gi0/0.10` (`192.168.10.1/24`) et `Gi0/0.20` (`192.168.20.1/24`).
   - Routage Multicast : `ip multicast-routing`, PIM Sparse-Mode sur les sous-interfaces, adresse de Rendezvous Point (RP) `192.168.20.1`.
   - Listes de contrôle d'accès (ACLs) étendues :
     - `ACL_SUPERVISION_IN` (appliquée en entrée de `Gi0/0.20`) : autorise `TCP 5556` vers l'atelier, autorise ICMP echo/echo-reply, autorise les flux établis, bloque et logge tout autre flux vers l'atelier.
     - `ACL_INDUSTRIAL_IN` (appliquée en entrée de `Gi0/0.10`) : autorise `UDP 5555` vers `224.1.1.1`, autorise IGMP, autorise les retours TCP établis, bloque `TCP 1099` vers le serveur RMI, bloque tout autre accès direct vers la supervision.
   - Modular QoS CLI (MQC) : Class-maps `CM_CRITICAL_CONTROL` (TCP 5556, DSCP `CS6`) et `CM_INDUSTRIAL_TELEMETRY` (UDP 5555, DSCP `AF21`), Policy-map `PM_INDUSTRIAL_QOS` appliquant LLQ (Priority 30%) et CBWFQ (Bandwidth 40%).

2. **Commutateur Cisco Catalyst SW1 (`SW1-DISTRIB`)** : [`docs/network/configs/SW1_Switch_Config.ios`](file:///C:/Users/pc/OneDrive/Desktop/4eme_ann%C3%A9e/InduTwin/docs/network/configs/SW1_Switch_Config.ios)
   - Création des VLANs 10 (`INDUSTRIAL_PLANT`) et 20 (`SCADA_SUPERVISION`).
   - Ports d'accès Atelier (`FastEthernet0/1` à `0/3`) configurés en VLAN 10 avec `spanning-tree portfast`.
   - Ports d'accès Supervision (`FastEthernet0/10` à `0/12`) configurés en VLAN 20 avec `spanning-tree portfast`.
   - Port Trunk 802.1Q (`GigabitEthernet0/1`) vers `R1 Gi0/0` avec `switchport trunk allowed vlan 10,20`.
   - Activation d'IGMP Snooping globalement et par VLAN avec `immediate-leave` et `querier`.
   - SVI de gestion `interface vlan 20` (`192.168.20.2/24`) et `ip default-gateway 192.168.20.1`.

---

## 4. Tests Packet Tracer Validés

Ces vérifications ont été confirmées dans le simulateur Cisco Packet Tracer :

| Réf. | Test Validé | Équipement / Commande | Résultat Observé |
| :--- | :--- | :--- | :--- |
| **PT-01** | Création et affectation des VLANs | `SW1# show vlan brief` | **CONFORME** : VLAN 10 et 20 actifs, ports `Fa0/1-3` (VLAN 10) et `Fa0/10-12` (VLAN 20) assignés. |
| **PT-02** | Agrégation 802.1Q (Trunk) | `SW1# show interfaces trunk` | **CONFORME** : `Gi0/1` en mode Trunk, encapsulation `802.1q`, VLANs `10, 20` autorisés. |
| **PT-03** | Sous-interfaces Router-on-a-Stick | `R1# show ip interface brief` | **CONFORME** : `Gi0/0.10` (`192.168.10.1`) et `Gi0/0.20` (`192.168.20.1`) en statut `up/up`. |
| **PT-04** | Routage inter-VLAN par ping ICMP | `M01> ping 192.168.20.10` | **CONFORME** : Ping fonctionnel entre VLAN 10 et VLAN 20 via la passerelle R1 (0% perte après ARP). |

---

## 5. Tests Restant à Exécuter Manuellement

Ces scénarios nécessitent une observation manuelle dans Packet Tracer ou un banc d'essai réel :

| Réf. | Scénario à Vérifier | Méthode / Commandes Cisco | Critère de Succès |
| :--- | :--- | :--- | :--- |
| **MAN-01** | Filtrage ACL : Blocage commande non autorisée | Injecter TCP 5556 depuis `192.168.20.99` ➔ `show access-lists ACL_SUPERVISION_IN` | Compteur `matches` incrémenté sur la règle deny, trame rejetée. |
| **MAN-02** | Filtrage ACL : Blocage atelier vers RMI 1099 | Tenter Telnet port 1099 depuis `M01` ➔ `show access-lists ACL_INDUSTRIAL_IN` | Connexion refusée, compteur deny incrémenté. |
| **MAN-03** | Adhésion dynamique IGMP Snooping | Lancer requête IGMP Join ➔ `show ip igmp snooping groups` | Entrée `224.1.1.1` enregistrée sur le port souscripteur. |
| **MAN-04** | Métriques de file d'attente QoS | Générer un trafic de saturation ➔ `show policy-map interface Gi0/0.10` | Incrémentation des compteurs dans `CM_CRITICAL_CONTROL` sans drop. |
| **MAN-05** | Bascule lors de coupure physique de lien | Exécuter `shutdown` sur `Fa0/3` sur SW1 | Perte de connectivité observée sur le port de la machine `M03`. |

---

## 6. Fonctions Non Prises en Charge & Limitations du Simulateur

1. **Absence de pont réseau direct JVM ↔ Packet Tracer** :
   - Packet Tracer ne fournit pas d'interface TAP/Npcap permettant de relier directement les sockets réelles de la JVM Windows aux équipements du simulateur (contrairement à GNS3 ou EVE-NG).
   - Les tests applicatifs Java (`test.Phase6Verification`) et les tests Cisco Packet Tracer constituent donc deux environnements distincts.

2. **Support partiel du Modular QoS CLI (MQC) dans Packet Tracer** :
   - Packet Tracer émule de façon limitée les commandes MQC complexes (`priority percent`, `bandwidth percent`, marquage DSCP fin sur Catalyst 2960). Les compteurs de politique peuvent ne pas s'incrémenter comme sur un routeur physique IOS réel ou sous Cisco CML.

3. **Multicast PIM-SM inter-VLAN sous Packet Tracer** :
   - Certaines versions de Cisco Packet Tracer restreignent l'implémentation de PIM Sparse-Mode avec Rendezvous Point sur les commutateurs et routeurs d'entrée de gamme, pouvant nécessiter une adaptation en Dense-Mode pour les démonstrations purement simulées.
