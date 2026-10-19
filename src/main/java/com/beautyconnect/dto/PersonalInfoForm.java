package com.beautyconnect.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Modification des informations personnelles d'un compte (client ou
 * professionnel). L'email n'est volontairement pas modifiable ici : il sert
 * d'identifiant de connexion et a ete verifie par mail a l'inscription.
 */
@Getter
@Setter
public class PersonalInfoForm {

    @NotBlank(message = "Le prenom est obligatoire")
    @Size(max = 100, message = "Le prenom ne doit pas depasser 100 caracteres")
    private String firstName;

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas depasser 100 caracteres")
    private String lastName;

    // Optionnel : vide accepte, sinon chiffres, espaces, + - . ( )
    @Pattern(regexp = "^$|^[0-9+().\\- ]{6,20}$", message = "Numero de telephone invalide")
    private String phone;
}
