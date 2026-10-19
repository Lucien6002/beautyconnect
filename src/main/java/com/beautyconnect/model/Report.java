package com.beautyconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Signalement effectue par un utilisateur (avis abusif, faux profil...),
 * a traiter par l'administrateur.
 */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    // Indique si "targetId" ci-dessous designe un avis (AVIS) ou un profil
    // professionnel (PROFIL_PROFESSIONNEL). Voir ReportTargetType.
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReportTargetType targetType;

    /** Identifiant de l'avis ou du profil professionnel signale. */
    // Remarque : ce n'est PAS une relation JPA (@ManyToOne) mais un simple Long,
    // car la cible peut etre soit un Review soit un ProfessionalProfile selon
    // targetType : une seule colonne "generique" est plus simple qu'une relation
    // polymorphe complexe pour ce cas d'usage.
    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ReportStatus status = ReportStatus.EN_ATTENTE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // ---- Journal de moderation (renseigne quand l'admin decide) ----
    // Les trois champs restent null tant que le signalement est EN_ATTENTE.

    /** Decision prise par l'administrateur (voir ReportService.decide). */
    @Enumerated(EnumType.STRING)
    @Column(name = "decision")
    private ReportDecision decision;

    /** Administrateur qui a pris la decision. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by_id")
    private User handledBy;

    /** Date et heure de la decision. */
    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
