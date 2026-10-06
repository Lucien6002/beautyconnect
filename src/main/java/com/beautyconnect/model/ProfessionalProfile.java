package com.beautyconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Vitrine professionnelle associee a un {@link User} de role PROFESSIONAL.
 * Contient les informations affichees publiquement (bio, ville, tarifs via
 * les {@link Prestation}, clientele visee...).
 *
 * (Voir {@link User} pour le detail des annotations Lombok @Getter/@Setter/
 * @NoArgsConstructor/@AllArgsConstructor/@Builder, identiques sur toutes les entites.)
 */
@Entity
@Table(name = "professional_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfessionalProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // @OneToOne : chaque ProfessionalProfile appartient a exactement un User, et
    // inversement (relation 1-1). FetchType.LAZY signifie que Hibernate ne va PAS
    // charger le User automatiquement des qu'on charge le profil : il ne le
    // chargera que si on appelle explicitement getUser() (requete SQL supplementaire
    // executee a ce moment-la). Cela evite de charger des donnees inutiles.
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true) // colonne de cle etrangere (foreign key) vers users.id
    private User user;

    @Column(name = "business_name", nullable = false)
    private String businessName;

    // length = 2000 : la colonne SQL est dimensionnee pour accueillir jusqu'a 2000
    // caracteres (evite de tronquer une bio un peu longue).
    @Column(length = 2000)
    private String bio;

    /** Filtre de recherche geolocalise "simple" : ville / quartier en texte libre. */
    @Column(nullable = false)
    private String city;

    private String address;

    private Double latitude;
    private Double longitude;

    @Column(name = "coordinates_public", nullable = false)
    @Builder.Default
    private boolean coordinatesPublic = false;

    @Column(name = "profile_photo_url")
    private String profilePhotoUrl;

    /** Filtre de recherche selon le sexe de la clientele visee par le professionnel. */
    @Enumerated(EnumType.STRING)
    @Column(name = "target_gender", nullable = false)
    @Builder.Default
    private TargetGender targetGender = TargetGender.MIXTE;

    /** Validation du compte par l'administrateur avant apparition dans les resultats publics. */
    @Column(nullable = false)
    @Builder.Default
    private boolean validated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
