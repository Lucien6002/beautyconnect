package com.beautyconnect.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO (Data Transfer Object) = objet simple utilise pour transporter des
 * donnees entre le formulaire HTML et le controleur, SANS etre une entite
 * JPA (voir package model). On evite volontairement de lier directement un
 * formulaire a l'entite User : cela empeche par exemple qu'un champ cache
 * "role=ADMIN" ajoute malicieusement dans le formulaire HTML ne permette de
 * s'auto-attribuer les droits administrateur (l'entite User n'est construite
 * "a la main" que dans UserService, avec le role force a CLIENT).
 *
 * Les annotations @NotBlank, @Email, @Size... viennent de Bean Validation
 * (norme Jakarta). Associees a @Valid sur le parametre du controleur (voir
 * AuthController), elles verifient automatiquement chaque champ AVANT
 * l'execution du code du controleur, et le "message" fourni est celui
 * affiche a l'utilisateur si la regle n'est pas respectee.
 */
@Getter
@Setter
public class ClientRegistrationForm {

    @NotBlank(message = "Le prenom est obligatoire") // refuse null, "" et les chaines composees uniquement d'espaces
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide") // verifie un format "quelquechose@quelquechose.xx"
    private String email;

    private String phone; // pas de validation : champ optionnel

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caracteres")
    private String password;
}
