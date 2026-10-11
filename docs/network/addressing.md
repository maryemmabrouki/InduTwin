# PLAN D'ADRESSAGE IP & MATRICE DE PORTS — INDUTWIN (PHASE 6)

## 1. Segmentation Logique (Sous-réseaux & VLANs)

| Segment Réseau | VLAN ID | Sous-réseau IPv4 | Masque CIDR | Passerelle par défaut | Usage / Description |
| :--- | :---: | :---: | :---: | :---: | :--- |
| **VLAN INDUSTRIAL** | `10` | `192.168.10.0` | `/24` (`255.255.255.0`) | `192.168.10.1` | Réseau d'atelier dédié aux automates, capteurs et machines simulées |
| **VLAN SUPERVISION** | `20` | `192.168.20.0` | `/24` (`255.255.255.0`) | `192.168.20.1` | Réseau de contrôle SCADA, serveurs RMI, Dashboard et base de données |

---

## 2. Table d'Attribution des Adresses IP Fixes

| Équipement | Rôle Industriel | VLAN | Adresse IP | Masque | Passerelle | Interface |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| **R1 (Sub-if 10)** | Passerelle Ateliers | 10 | `192.168.10.1` | `255.255.255.0` | N/A | `g0/0.10` |
| **R1 (Sub-if 20)** | Passerelle Supervision| 20 | `192.168.20.1` | `255.255.255.0` | N/A | `g0/0.20` |
| **SW1 (Management)**| Switch Distribution | 20 | `192.168.20.2` | `255.255.255.0` | `192.168.20.1` | `VLAN 20 (SVI)` |
| **M01** | Presse Hydraulique | 10 | `192.168.10.11` | `255.255.255.0` | `192.168.10.1` | `Fa0/1` |
| **M02** | Four Industriel | 10 | `192.168.10.12` | `255.255.255.0` | `192.168.10.1` | `Fa0/2` |
| **M03** | Robot Industriel | 10 | `192.168.10.13` | `255.255.255.0` | `192.168.10.1` | `Fa0/3` |
| **SUPERVISOR-PC** | Dashboard SCADA & RMI| 20 | `192.168.20.10` | `255.255.255.0` | `192.168.20.1` | `Fa0/10` |
| **RMI-CLIENT** | Client distant audit | 20 | `192.168.20.20` | `255.255.255.0` | `192.168.20.1` | `Fa0/11` |

---

## 3. Adressage Multicast & Ports Applicatifs

### Groupe Multicast
- **Adresse IP Multicast** : `224.1.1.1`
- **Plage IANA** : Classe D (`224.0.0.0/4`), spécifiquement *User-defined Multicast Applications* (`224.0.1.0` - `238.255.255.255`).
- **Adresse MAC Multicast correspondante** : `01:00:5E:01:01:01`
- **Port d'écoute UDP** : `5555`

### Ports de Services Réseau
| Port | Protocole | Service / Rôle | Sens du Flux | Justification Métier |
| :---: | :---: | :--- | :--- | :--- |
| **`5555`** | **UDP** | Bus Multicast Télémétrie & Alarmes | Machines ➔ Groupe `224.1.1.1` ➔ Superviseur | Diffusion haute fréquence temps réel (One-to-Many) |
| **`5556`** | **TCP** | Canal de Contrôle Industriel | Superviseur (VLAN 20) ➔ Machines (VLAN 10) | Acheminement fiable avec accusé de réception formel (`ACK`) |
| **`1099`** | **TCP** | Registre Java RMI (Supervision distante) | Client RMI (VLAN 20) ➔ Superviseur (VLAN 20) | Interrogation distante d'états et d'historique SQLite |
