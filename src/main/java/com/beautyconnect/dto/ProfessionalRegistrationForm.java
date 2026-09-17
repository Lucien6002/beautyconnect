package com.beautyconnect.dto;

import com.beautyconnect.model.TargetGender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Formulaire d'inscription professionnelle : cumule les champs de compte
 * (identiques a {@link ClientRegistrationForm}) et les champs de vitrine
 * professionnelle (voir {@link com.beautyconnect.model.ProfessionalProfile}).
 * Voir ClientRegistrationForm pour l'explication generale des DTO et de
 * Bean Validation (@NotBlank, @Email...).
 */
@Getter
@Setter
public class ProfessionalRegistrationForm {

    @NotBlank(message = "Le prenom est obligatoire")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    private String lastName;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Format d'email invalide")
    private String email;

    private String phone;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, message = "Le mot de passe doit contenir au moins 8 caracteres")
    private String password;

    @NotBlank(message = "Le nom de l'activite est obligatoire")
    private String businessName;

    private String bio;

    @NotBlank(message = "La ville est obligatoire")
    private String city;

    private String address;

    // @NotNull (et pas @NotBlank, reserve aux String) : ici le champ est une
    // enum (TargetGender), on verifie juste qu'une valeur a ete choisie.
    @NotNull(message = "La clientele visee est obligatoire")
    private TargetGender targetGender;
}
