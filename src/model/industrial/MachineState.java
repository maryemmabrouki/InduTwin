package model.industrial;

/**
 * Machine à états finis représentant le cycle de vie opérationnel d'un équipement industriel.
 */
public enum MachineState {
    STOPPED,
    STARTING,
    RUNNING,
    WARNING,
    CRITICAL,
    FAILURE,
    MAINTENANCE,
    OFFLINE;

    /**
     * Vérifie si la transition vers l'état cible est valide selon les règles de sécurité industrielle.
     */
    public boolean canTransitionTo(MachineState target) {
        if (this == target) {
            return true;
        }

        switch (this) {
            case STOPPED:
                return target == STARTING || target == RUNNING || target == MAINTENANCE || target == OFFLINE || target == FAILURE;
            case STARTING:
                return target == RUNNING || target == FAILURE || target == STOPPED || target == OFFLINE;
            case RUNNING:
                return target == WARNING || target == CRITICAL || target == FAILURE || target == STOPPED || target == OFFLINE;
            case WARNING:
                return target == RUNNING || target == CRITICAL || target == FAILURE || target == STOPPED || target == OFFLINE;
            case CRITICAL:
                return target == FAILURE || target == WARNING || target == STOPPED || target == OFFLINE;
            case FAILURE:
                return target == MAINTENANCE || target == STOPPED || target == OFFLINE;
            case MAINTENANCE:
                return target == STARTING || target == STOPPED || target == OFFLINE;
            case OFFLINE:
                // Une machine qui revient en ligne repasse par son état de récupération ou STOPPED/RUNNING
                return target == STARTING || target == RUNNING || target == STOPPED || target == WARNING;
            default:
                return false;
        }
    }
}
