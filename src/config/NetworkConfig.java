package config;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Configuration centralisée du réseau Multicast.
 * Évite les valeurs hardcodées dispersées et gère la sélection de l'interface réseau.
 */
public final class NetworkConfig {

    public static final String MULTICAST_GROUP = "224.1.1.1";
    public static final int PORT = 5555;
    public static final int BUFFER_SIZE = 1024;
    public static final int DEFAULT_STREAM_DELAY_MS = 60;

    // Constantes temporelles industrielles (Phase 2)
    public static final int TELEMETRY_INTERVAL_MS = 1000;
    public static final int HEARTBEAT_INTERVAL_MS = 2000;
    public static final int HEARTBEAT_TIMEOUT_MS = 6000;

    // Constantes de contrôle point-à-point TCP et supervision distante RMI (Phase 4)
    public static final int TCP_CONTROL_PORT = 5556;
    public static final int TCP_TIMEOUT_MS = 3000;
    public static final int RMI_PORT = 1099;
    public static final String RMI_SERVICE_NAME = "IndustrialSupervisionService";

    // Infrastructure Réseau Cisco & Segmentation VLANs (Phase 6)
    public static final int VLAN_INDUSTRIAL_ID = 10;
    public static final String VLAN_INDUSTRIAL_NAME = "INDUSTRIAL_PLANT";
    public static final String VLAN_INDUSTRIAL_SUBNET = "192.168.10.0/24";
    public static final String ROUTER_IP_INDUSTRIAL = "192.168.10.1";

    public static final int VLAN_SUPERVISION_ID = 20;
    public static final String VLAN_SUPERVISION_NAME = "SCADA_SUPERVISION";
    public static final String VLAN_SUPERVISION_SUBNET = "192.168.20.0/24";
    public static final String ROUTER_IP_SUPERVISION = "192.168.20.1";

    public static final int MULTICAST_TTL = 4; // Traversée inter-VLAN du routeur R1

    private NetworkConfig() {
        // Classe utilitaire non instanciable
    }

    /**
     * Retourne l'adresse InetAddress du groupe Multicast.
     */
    public static InetAddress getGroupAddress() throws Exception {
        return InetAddress.getByName(MULTICAST_GROUP);
    }

    /**
     * Retourne le socket address (groupe + port) pour l'API moderne de joinGroup.
     */
    public static InetSocketAddress getGroupSocketAddress() throws Exception {
        return new InetSocketAddress(getGroupAddress(), PORT);
    }

    /**
     * Détecte intelligemment la meilleure interface réseau locale supportant le Multicast.
     * Privilégie les interfaces physiques actives (Wi-Fi, Ethernet) ayant une adresse IPv4.
     */
    public static NetworkInterface getPreferredNetworkInterface() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return null;
            }

            for (NetworkInterface netIf : Collections.list(interfaces)) {
                if (netIf.isUp() && !netIf.isLoopback() && netIf.supportsMulticast()) {
                    // Vérifier si elle possède une adresse IPv4 valide
                    boolean hasIpv4 = Collections.list(netIf.getInetAddresses()).stream()
                            .anyMatch(addr -> !addr.isLoopbackAddress() && addr.getAddress().length == 4);
                    if (hasIpv4) {
                        return netIf;
                    }
                }
            }

            // Fallback : première interface active supportant le multicast
            interfaces = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netIf : Collections.list(interfaces)) {
                if (netIf.isUp() && netIf.supportsMulticast()) {
                    return netIf;
                }
            }
        } catch (SocketException e) {
            System.err.println("[NetworkConfig] Impossible d'énumérer les interfaces réseau: " + e.getMessage());
        }
        return null;
    }
}
