package com.beautyconnect.model;

/**
 * Decision prise par l'administrateur sur un signalement en attente
 * (voir ReportService.decide). Enregistree dans Report.decision pour que
 * l'historique de moderation reste comprehensible : le statut TRAITE seul
 * ne dit pas si l'avis a ete masque ou si rien n'a ete fait.
 */
public enum ReportDecision {
    /** Clore sans toucher a la cible (probleme regle autrement). */
    TRAITER("Cloture sans action"),
    /** Masquer l'avis signale, puis clore. */
    MASQUER_AVIS("Avis masque"),
    /** Desactiver le compte du profil signale, puis clore. */
    DESACTIVER_PROFIL("Compte desactive"),
    /** Signalement infonde : clore sans action. */
    REJETER("Rejete");

    private final String label;

    ReportDecision(String label) {
        this.label = label;
    }

    /** Libelle affiche dans l'espace admin. */
    public String getLabel() {
        return label;
    }
}
