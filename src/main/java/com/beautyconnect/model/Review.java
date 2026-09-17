package com.beautyconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Avis laisse par un client sur un professionnel, apres un rendez-vous termine.
 */
@Entity
@Table(name = "reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "professional_id", nullable = false)
    private ProfessionalProfile professional;

    // Pas de "optional = false" ici : contrairement aux autres relations,
    // l'avis peut exister meme si le rendez-vous d'origine a ete supprime
    // (on garde une trace de l'avis, on perd juste le lien vers le rendez-vous).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    // Note numerique (ex: 1 a 5 etoiles). La validation de la plage de valeurs
    // se fait cote formulaire (voir ReviewForm), pas au niveau de l'entite.
    @Column(nullable = false)
    private Integer rating;

    @Column(length = 1000)
    private String comment;

    /** Masque de la vue publique par l'administrateur suite a un signalement. */
    @Column(nullable = false)
    @Builder.Default
    private boolean hidden = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
