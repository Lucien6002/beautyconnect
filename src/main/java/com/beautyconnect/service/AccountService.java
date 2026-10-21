package com.beautyconnect.service;

import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.Appointment;
import com.beautyconnect.model.AppointmentStatus;
import com.beautyconnect.model.Role;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.FavoriteRepository;
import com.beautyconnect.repository.UserRepository;
import com.beautyconnect.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Suppression du compte d'un client.
 *
 * Le compte est anonymise plutot qu'efface physiquement : les rendez-vous et
 * avis passes restent lies a un utilisateur (cles etrangeres, historique et
 * statistiques du professionnel), mais ne contiennent plus aucune donnee
 * personnelle et le compte ne peut plus se connecter. L'adresse email est
 * liberee : elle peut etre reutilisee pour une nouvelle inscription.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentService appointmentService;
    private final FavoriteRepository favoriteRepository;
    private final VerificationTokenRepository tokenRepository;
    private final com.beautyconnect.repository.PasswordResetTokenRepository passwordResetTokens;

    @Transactional
    public void deleteClientAccount(Long userId, String password) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalOperationException("Compte introuvable."));
        if (user.getRole() != Role.CLIENT) {
            throw new IllegalOperationException("Seul un compte client peut etre supprime ici.");
        }
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalOperationException("Mot de passe incorrect.");
        }

        // Les rendez-vous a venir sont annules : les creneaux redeviennent
        // disponibles pour les autres clients.
        LocalDateTime now = LocalDateTime.now();
        for (Appointment a : appointmentService.getAppointmentsForClient(user)) {
            boolean active = a.getStatus() == AppointmentStatus.EN_ATTENTE || a.getStatus() == AppointmentStatus.CONFIRME;
            if (active && a.getTimeSlot().getStartDateTime().isAfter(now)) {
                appointmentService.cancelByClient(user, a.getId());
            }
        }

        favoriteRepository.deleteByClient(user);
        tokenRepository.deleteByUser(user);
        passwordResetTokens.deleteByUser(user);

        user.setEmail("compte-supprime-" + user.getId() + "-" + UUID.randomUUID() + "@deleted.invalid");
        user.setFirstName("Compte");
        user.setLastName("supprimé");
        user.setPhone(null);
        user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        user.setEnabled(false);
        user.setEmailVerified(false);
        userRepository.save(user);
    }
}
