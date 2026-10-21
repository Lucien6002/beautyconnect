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
    private final PasswordEncoder passwordEncoder;
    private final com.beautyconnect.repository.PasswordResetTokenRepository passwordResetTokens;
    private final AuthTokenService authTokenService; // <-- AJOUTE CECI

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new com.beautyconnect.exception.ResourceNotFoundException("Compte introuvable"));
    }

    // Seuls prenom, nom et telephone sont modifiables : l'email (identifiant de
    // connexion verifie) et le role ne passent jamais par ce formulaire.
    @Transactional
    public User updatePersonalInfo(Long userId, com.beautyconnect.dto.PersonalInfoForm form) {
        User user = getById(userId);
        user.setFirstName(form.getFirstName().trim());
        user.setLastName(form.getLastName().trim());
        String phone = form.getPhone() == null ? "" : form.getPhone().trim();
        user.setPhone(phone.isEmpty() ? null : phone);
        return userRepository.save(user);
    }

    // Le mot de passe actuel doit etre correct, et le nouveau different de l'ancien.
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new com.beautyconnect.exception.ResourceNotFoundException("Compte introuvable"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new com.beautyconnect.exception.IllegalOperationException("Le mot de passe actuel est incorrect.");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new com.beautyconnect.exception.IllegalOperationException(
                    "Le nouveau mot de passe doit etre different de l'actuel.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        passwordResetTokens.deleteByUser(user);
        userRepository.save(user);
    }

    @Transactional
    public User registerClient(ClientRegistrationForm form) {
        checkEmailAvailable(form.getEmail());

        User user = User.builder()
                .email(form.getEmail().trim().toLowerCase(java.util.Locale.ROOT))
                .password(passwordEncoder.encode(form.getPassword()))
                .firstName(form.getFirstName())
                .lastName(form.getLastName())
                .phone(form.getPhone())
                .role(Role.CLIENT)
                .enabled(false) // <-- PASSE A FALSE
                .build();

        user = userRepository.save(user);
        authTokenService.generateAndSendActivationLink(user); // <-- ENVOIE LE MAIL
        return user;
    }

    @Transactional
    public User registerProfessional(ProfessionalRegistrationForm form) {
        checkEmailAvailable(form.getEmail());

        User user = User.builder()
                .email(form.getEmail().trim().toLowerCase(java.util.Locale.ROOT))
                .password(passwordEncoder.encode(form.getPassword()))
                .firstName(form.getFirstName())
                .lastName(form.getLastName())
                .phone(form.getPhone())
                .role(Role.PROFESSIONAL)
                .enabled(false) // <-- PASSE A FALSE
                .build();
        user = userRepository.save(user);

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

        authTokenService.generateAndSendActivationLink(user); // <-- ENVOIE LE MAIL
        return user;
    }

    // Methode privee : utilitaire interne a la classe, non exposee aux
    // controleurs. Leve une exception metier si l'email est deja pris.
    private void checkEmailAvailable(String email) {
        if (userRepository.existsByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new EmailAlreadyUsedException(email);
        }
    }
}
