package com.beautyconnect.service;

import com.beautyconnect.dto.ClientRegistrationForm;
import com.beautyconnect.dto.ProfessionalRegistrationForm;
import com.beautyconnect.exception.EmailAlreadyUsedException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Role;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Inscription des comptes Client et Professionnel.
 *
 * @Service : marque cette classe comme un "bean" gere par Spring (Spring la
 * cree automatiquement et l'injecte partout ou elle est demandee, ici dans
 * AuthController par exemple).
 *
 * @RequiredArgsConstructor (Lombok) : genere un constructeur qui prend en
 * parametre tous les champs "final" ci-dessous. Combine a @Service, c'est ce
 * qu'on appelle de "l'injection de dependances par constructeur" : Spring
 * fournit automatiquement une instance de UserRepository, de
 * ProfessionalProfileRepository et de PasswordEncoder au moment de creer
 * UserService, sans qu'on ait besoin d'ecrire "new UserRepository()" nulle part.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    // PasswordEncoder (bean fourni par SecurityConfig, implementation BCrypt) :
    // permet de hacher le mot de passe avant de le stocker en base. On ne
    // stocke JAMAIS un mot de passe en clair.
    private final PasswordEncoder passwordEncoder;

    // @Transactional : toutes les operations sur la base de donnees faites
    // dans cette methode sont executees comme un tout indivisible (soit tout
    // est enregistre, soit rien ne l'est en cas d'erreur au milieu).
    @Transactional
    public User registerClient(ClientRegistrationForm form) {
        checkEmailAvailable(form.getEmail());

        // User.builder()...build() : construction "fluide" de l'objet grace a
        // l'annotation Lombok @Builder presente sur l'entite User. Plus lisible
        // qu'un constructeur avec 8 parametres positionnels.
        User user = User.builder()
                .email(form.getEmail().toLowerCase()) // normalisation : evite que "A@x.com" et "a@x.com" soient traites comme deux comptes différents
                .password(passwordEncoder.encode(form.getPassword())) // hachage du mot de passe
                .firstName(form.getFirstName())
                .lastName(form.getLastName())
                .phone(form.getPhone())
                .role(Role.CLIENT)
                .enabled(true)
                .build();

        return userRepository.save(user);
    }

    @Transactional
    public User registerProfessional(ProfessionalRegistrationForm form) {
        checkEmailAvailable(form.getEmail());

        User user = User.builder()
                .email(form.getEmail().toLowerCase())
                .password(passwordEncoder.encode(form.getPassword()))
                .firstName(form.getFirstName())
                .lastName(form.getLastName())
                .phone(form.getPhone())
                .role(Role.PROFESSIONAL)
                .enabled(true)
                .build();
        user = userRepository.save(user);

        // Le profil doit etre valide par l'administrateur avant d'apparaitre
        // dans les resultats de recherche publics.
        ProfessionalProfile profile = ProfessionalProfile.builder()
                .user(user)
                .businessName(form.getBusinessName())
                .bio(form.getBio())
                .city(form.getCity())
                .address(form.getAddress())
                .targetGender(form.getTargetGender())
                .validated(false)
                .build();
        professionalProfileRepository.save(profile);

        return user;
    }

    // Methode privee : utilitaire interne a la classe, non exposee aux
    // controleurs. Leve une exception metier si l'email est deja pris.
    private void checkEmailAvailable(String email) {
        if (userRepository.existsByEmail(email.toLowerCase())) {
            throw new EmailAlreadyUsedException(email);
        }
    }
}
