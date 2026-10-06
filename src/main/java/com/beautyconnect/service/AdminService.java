package com.beautyconnect.service;

import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Role;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.UserRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
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
    private final PrestationRepository prestationRepository;
    private final EmailService emailService;
    private final SessionRegistry sessionRegistry;

    // Profils professionnels en attente de validation par un administrateur
    // (les plus anciennes demandes en premier).
    public List<ProfessionalProfile> getPendingProfessionals() {
        return professionalProfileRepository.findByValidatedWithUser(false);
    }

    // Liste filtree pour la page /admin/professionnels :
    // null = tous (en attente d'abord), true = valides, false = en attente.
    public List<ProfessionalProfile> getProfessionals(Boolean validated) {
        if (validated == null) {
            return professionalProfileRepository.findAllWithUser();
        }
        return professionalProfileRepository.findByValidatedWithUser(validated);
    }

    // Fiche complete d'un profil (User charge) pour que l'admin puisse
    // l'examiner avant de prendre sa decision.
    public ProfessionalProfile getProfessionalForReview(Long professionalId) {
        return professionalProfileRepository.findByIdWithUser(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil professionnel introuvable : " + professionalId));
    }

    // Toutes les prestations (actives ou non) declarees par le professionnel :
    // l'admin doit voir le catalogue complet pour juger le serieux du profil.
    public List<Prestation> getPrestationsForReview(ProfessionalProfile profile) {
        return prestationRepository.findByProfessional(profile);
    }

    // Valide un compte professionnel : il devient visible dans les resultats
    // de recherche publics (voir ProfessionalProfileRepository.search()).
    // Regles metier :
    //  - un profil deja valide ne peut pas l'etre une seconde fois (evite un
    //    double clic / un second mail de notification) ;
    //  - un compte desactive doit d'abord etre reactive : valider un compte
    //    qui ne peut pas se connecter n'a pas de sens.
    @Transactional
    public ProfessionalProfile validateProfessional(Long professionalId) {
        ProfessionalProfile profile = getProfessionalForReview(professionalId);
        if (profile.isValidated()) {
            throw new IllegalOperationException("Le profil \"" + profile.getBusinessName() + "\" est deja valide.");
        }
        if (!profile.getUser().isEnabled()) {
            throw new IllegalOperationException("Le compte de \"" + profile.getBusinessName()
                    + "\" est desactive : reactivez-le avant de valider le profil.");
        }
        profile.setValidated(true);
        professionalProfileRepository.save(profile);
        // L'echec d'envoi est journalise sans annuler la validation
        // (voir EmailService / app.mail.fail-silently).
        emailService.sendProfessionalValidated(profile);
        return profile;
    }

    // Retire la validation (profil incomplet, faux profil signale...) : le
    // profil disparait des resultats de recherche publics mais le compte
    // reste actif, le professionnel peut corriger son profil.
    @Transactional
    public ProfessionalProfile revokeValidation(Long professionalId) {
        ProfessionalProfile profile = getProfessionalForReview(professionalId);
        if (!profile.isValidated()) {
            throw new IllegalOperationException("Le profil \"" + profile.getBusinessName() + "\" n'est pas valide.");
        }
        profile.setValidated(false);
        return professionalProfileRepository.save(profile);
    }

    // Active ou desactive un compte (moderation, spam, faux profil...).
    // Un compte desactive :
    //  - ne peut plus se connecter (voir CustomUserDetails.isEnabled) ;
    //  - est deconnecte immediatement s'il etait connecte (sessions expirees) ;
    //  - s'il est professionnel, disparait de la recherche et de sa page
    //    publique, et ne peut plus recevoir de reservation
    //    (voir ProfessionalProfileRepository.search / findPublicById).
    @Transactional
    public User setUserEnabled(Long userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable : " + userId));
        if (!enabled && user.getRole() == Role.ADMIN) {
            throw new IllegalOperationException("Un compte administrateur ne peut pas etre desactive.");
        }
        user.setEnabled(enabled);
        userRepository.save(user);
        if (!enabled) {
            expireSessions(user.getEmail());
        }
        return user;
    }

    // Marque comme expirees toutes les sessions ouvertes de cet utilisateur :
    // a sa prochaine requete, Spring Security le deconnecte et le redirige
    // vers /connexion?desactive (voir SecurityConfig.sessionManagement).
    private void expireSessions(String email) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof UserDetails details && details.getUsername().equals(email))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }

    // Statistiques affichees sur le tableau de bord administrateur.
    public Metrics getMetrics() {
        return Metrics.builder()
                .totalClients(userRepository.countByRole(Role.CLIENT))
                .totalProfessionals(userRepository.countByRole(Role.PROFESSIONAL))
                .validatedProfessionals(professionalProfileRepository.countByValidated(true))
                .pendingProfessionals(professionalProfileRepository.countByValidated(false))
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
