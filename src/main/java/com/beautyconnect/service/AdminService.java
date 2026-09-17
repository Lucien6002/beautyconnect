package com.beautyconnect.service;

import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Role;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.UserRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Outils d'administration : validation des comptes professionnels,
 * activation/desactivation de comptes, et metriques d'usage globales.
 * Voir {@link UserService} pour l'explication de @Service / @RequiredArgsConstructor.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final ProfessionalProfileRepository professionalProfileRepository;
    private final UserRepository userRepository;

    // Profils professionnels en attente de validation par un administrateur.
    public List<ProfessionalProfile> getPendingProfessionals() {
        return professionalProfileRepository.findByValidatedFalse();
    }

    public List<ProfessionalProfile> getAllProfessionals() {
        return professionalProfileRepository.findAll();
    }

    // Valide un compte professionnel : il devient visible dans les resultats
    // de recherche publics (voir ProfessionalProfileRepository.search()).
    @Transactional
    public void validateProfessional(Long professionalId) {
        ProfessionalProfile profile = professionalProfileRepository.findById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil professionnel introuvable : " + professionalId));
        profile.setValidated(true);
        professionalProfileRepository.save(profile);
    }

    // Active ou desactive un compte (moderation, spam, faux profil...) : un
    // compte desactive ne peut plus se connecter (voir CustomUserDetails).
    @Transactional
    public void setUserEnabled(Long userId, boolean enabled) {
        var user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));
        user.setEnabled(enabled);
        userRepository.save(user);
    }

    // Statistiques affichees sur le tableau de bord administrateur.
    public Metrics getMetrics() {
        return Metrics.builder()
                .totalClients(userRepository.countByRole(Role.CLIENT))
                .totalProfessionals(userRepository.countByRole(Role.PROFESSIONAL))
                .validatedProfessionals(professionalProfileRepository.findByValidatedTrue().size())
                .pendingProfessionals(professionalProfileRepository.findByValidatedFalse().size())
                .build();
    }

    // Classe interne (static nested class) : plutot que de creer un fichier
    // Metrics.java a part pour un objet uniquement utilise ici, on la
    // declare directement dans AdminService puisqu'elle n'a de sens que dans
    // ce contexte (regroupement logique, pas de reutilisation ailleurs).
    @Getter
    @Builder
    public static class Metrics {
        private long totalClients;
        private long totalProfessionals;
        private long validatedProfessionals;
        private long pendingProfessionals;
    }
}
