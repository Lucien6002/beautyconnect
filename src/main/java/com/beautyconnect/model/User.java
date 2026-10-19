package com.beautyconnect.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Compte utilisateur commun aux 3 acteurs (Client, Professionnel, Administrateur).
 * Le role determine les fonctionnalites et pages accessibles.
 *
 * Un compte Professionnel est complete par un {@link ProfessionalProfile} (1-1).
 *
 * Note pedagogique sur les annotations Lombok utilisees ci-dessous (elles
 * generent du code automatiquement a la compilation, donc invisible dans ce
 * fichier mais bien present dans le .class final) :
 * - {@code @Getter} / {@code @Setter} : generent tous les getX()/setX() pour
 *   chaque champ, pour eviter de les ecrire a la main.
 * - {@code @NoArgsConstructor} : genere un constructeur vide "public User() {}",
 *   obligatoire pour que Hibernate puisse instancier l'entite depuis la BDD.
 * - {@code @AllArgsConstructor} : genere un constructeur avec tous les champs.
 * - {@code @Builder} : genere un "constructeur fluide" (ex: User.builder().email(...).build())
 *   pratique pour construire un objet lisible sans se tromper dans l'ordre des parametres.
 */
@Entity // Indique a Hibernate/JPA que cette classe correspond a une table en base.
@Table(name = "users") // Nom exact de la table SQL associee.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id // Cle primaire de la table.
    @GeneratedValue(strategy = GenerationType.IDENTITY) // L'ID est auto-incremente par PostgreSQL (colonne SERIAL/IDENTITY).
    private Long id;

    // "unique = true" cree une contrainte d'unicite en base : impossible d'avoir
    // deux comptes avec le meme email (verifie aussi cote service, voir UserService).
    @Column(nullable = false, unique = true)
    private String email;

    // Mot de passe stocke SOUS FORME HACHEE (BCrypt, voir SecurityConfig), jamais en clair.
    @Column(nullable = false)
    private String password;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    // Pas de "nullable = false" : ce champ est optionnel.
    private String phone;

    // @Enumerated(EnumType.STRING) : stocke le role en base sous forme de texte
    // ("CLIENT", "PROFESSIONAL", "ADMIN") plutot que sous forme de nombre (0,1,2),
    // ce qui rend la base de donnees lisible directement et evite les erreurs si
    // l'ordre des valeurs de l'enum Role change un jour.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /** Permet a l'administrateur de desactiver un compte (spam, faux profil...). */
    @Column(nullable = false)
    @Builder.Default // Sans cette annotation, le Builder mettrait "false" par defaut (valeur par defaut de Java pour un boolean) au lieu de "true".
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // @PrePersist : methode appelee automatiquement par Hibernate juste avant
    // le tout premier INSERT en base pour cette entite. Ici, on en profite pour
    // fixer la date de creation sans avoir a y penser a chaque appel de service.
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Methode utilitaire simple (pas liee a JPA), utilisee dans les templates
    // Thymeleaf pour afficher "Prenom Nom" sans recalculer la concatenation partout.
    public String getFullName() {
        return firstName + " " + lastName;
    }
}
