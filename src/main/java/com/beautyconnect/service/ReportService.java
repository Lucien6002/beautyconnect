package com.beautyconnect.service;

import com.beautyconnect.dto.ReportForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportDecision;
import com.beautyconnect.model.ReportStatus;
import com.beautyconnect.model.ReportTargetType;
import com.beautyconnect.model.Review;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.ReportRepository;
import com.beautyconnect.repository.ReviewRepository;
import com.beautyconnect.repository.UserRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Signalements d'avis ou de profils professionnels, geres par l'administrateur.
 * Voir {@link UserService} pour l'explication de @Service / @RequiredArgsConstructor.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final ReviewRepository reviewRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    // Pour desactiver un compte signale : AdminService.setUserEnabled
    // deconnecte aussi immediatement l'utilisateur (sessions expirees).
    private final AdminService adminService;
    private final UserRepository userRepository;

    // Cree un signalement. targetType/targetId identifient generiquement ce
    // qui est signale (un avis ou un profil professionnel), voir Report.java.
    //
    // targetId est un simple Long, sans cle etrangere : la base accepterait
    // n'importe quel nombre, meme un avis qui n'a jamais existe. C'est donc
    // ici qu'on garantit que la cible est reelle, avant l'insertion :
    //  - la cible doit exister et etre visible publiquement (on ne signale
    //    que ce qu'on peut voir) ;
    //  - anti-spam : un seul signalement EN_ATTENTE par auteur et par cible.
    //    Une fois le premier traite ou rejete, l'auteur peut signaler a nouveau.
    // Les violations levent une IllegalOperationException, affichee en
    // message flash par ReportController.
    @Transactional
    public Report create(User reporter, ReportTargetType targetType, Long targetId, ReportForm form) {
        if (!isPubliclyVisible(targetType, targetId)) {
            throw new IllegalOperationException(targetType == ReportTargetType.AVIS
                    ? "Cet avis n'existe pas ou n'est plus visible."
                    : "Ce profil n'existe pas ou n'est plus visible.");
        }
        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, targetType, targetId, ReportStatus.EN_ATTENTE)) {
            throw new IllegalOperationException(
                    "Vous avez deja signale cet element : votre signalement est en cours d'examen.");
        }
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reason(form.getReason().trim())
                .status(ReportStatus.EN_ATTENTE)
                .build();
        return reportRepository.save(report);
    }

    // Identifiant du professionnel dont la fiche publique contient la cible
    // (le profil lui-meme, ou le professionnel de l'avis), si cette fiche est
    // visible. Sert a ReportController pour renvoyer le client la ou il etait.
    @Transactional(readOnly = true)
    public Optional<Long> findPublicProfessionalIdOf(ReportTargetType targetType, Long targetId) {
        Optional<Long> professionalId = targetType == ReportTargetType.AVIS
                ? reviewRepository.findById(targetId).map(review -> review.getProfessional().getId())
                : Optional.of(targetId);
        return professionalId.filter(id -> professionalProfileRepository.findPublicById(id).isPresent());
    }

    // Avis : existe, n'est pas masque, et son professionnel est public.
    // Profil : compte actif et profil valide (meme regle que la fiche publique).
    private boolean isPubliclyVisible(ReportTargetType targetType, Long targetId) {
        if (targetType == ReportTargetType.AVIS) {
            return reviewRepository.findById(targetId)
                    .filter(review -> !review.isHidden())
                    .flatMap(review -> professionalProfileRepository.findPublicById(review.getProfessional().getId()))
                    .isPresent();
        }
        return professionalProfileRepository.findPublicById(targetId).isPresent();
    }

    // File d'attente de moderation : signalements pas encore traites.
    public List<Report> getPending() {
        return reportRepository.findByStatusOrderByCreatedAtDesc(ReportStatus.EN_ATTENTE);
    }

    // ---- Moderation par l'administrateur ----

    // Applique la decision de l'admin. Tout se fait dans UNE transaction :
    // si l'action sur la cible echoue (avis disparu, mauvais type...), le
    // statut du signalement n'est pas modifie non plus. On n'enregistre
    // jamais une decision a moitie.
    // Une decision qui agit sur la cible (masquer, desactiver) clot aussi
    // les autres signalements en attente sur cette cible : le probleme est
    // regle pour tous. TRAITER et REJETER ne concernent que ce signalement.
    // Chaque signalement clos garde la trace de la decision, de l'admin et
    // de la date (journal de moderation).
    @Transactional
    public Report decide(Long reportId, ReportDecision decision, User admin) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable : " + reportId));
        if (report.getStatus() != ReportStatus.EN_ATTENTE) {
            throw new IllegalOperationException("Le signalement #" + reportId + " a deja ete traite.");
        }

        List<Report> closed = switch (decision) {
            case MASQUER_AVIS -> {
                hideReportedReview(report);
                yield pendingReportsOnSameTarget(report);
            }
            case DESACTIVER_PROFIL -> {
                disableReportedProfile(report);
                yield pendingReportsOnSameTarget(report);
            }
            case TRAITER, REJETER -> List.of(report);
        };
        ReportStatus status = decision == ReportDecision.REJETER ? ReportStatus.REJETE : ReportStatus.TRAITE;
        // getReferenceById : reference geree par cette transaction vers le
        // compte admin, sans recharger l'utilisateur (admin vient de la session).
        User handledBy = userRepository.getReferenceById(admin.getId());
        LocalDateTime now = LocalDateTime.now();
        closed.forEach(r -> {
            r.setStatus(status);
            r.setDecision(decision);
            r.setHandledBy(handledBy);
            r.setHandledAt(now);
        });
        reportRepository.saveAll(closed);
        return report;
    }

    private void hideReportedReview(Report report) {
        if (report.getTargetType() != ReportTargetType.AVIS) {
            throw new IllegalOperationException("Ce signalement ne concerne pas un avis.");
        }
        Review review = reviewRepository.findById(report.getTargetId())
                .orElseThrow(() -> new IllegalOperationException(
                        "L'avis signale n'existe plus : rejetez ou cloturez le signalement."));
        review.setHidden(true);
        reviewRepository.save(review);
    }

    private void disableReportedProfile(Report report) {
        if (report.getTargetType() != ReportTargetType.PROFIL_PROFESSIONNEL) {
            throw new IllegalOperationException("Ce signalement ne concerne pas un profil professionnel.");
        }
        ProfessionalProfile profile = professionalProfileRepository.findById(report.getTargetId())
                .orElseThrow(() -> new IllegalOperationException(
                        "Le profil signale n'existe plus : rejetez ou cloturez le signalement."));
        adminService.setUserEnabled(profile.getUser().getId(), false);
    }

    private List<Report> pendingReportsOnSameTarget(Report report) {
        return reportRepository.findByTargetTypeAndTargetIdAndStatus(
                report.getTargetType(), report.getTargetId(), ReportStatus.EN_ATTENTE);
    }

    // Liste de moderation : chaque signalement accompagne de sa cible reelle
    // (extrait de l'avis, nom du profil), pour que l'admin decide en
    // connaissance de cause sans chercher l'avis ou le profil ailleurs.
    // readOnly + construction de vues simples a l'interieur de la
    // transaction : les relations LAZY (auteur de l'avis, compte du profil)
    // sont lues ici, pas pendant le rendu Thymeleaf (open-in-view=false).
    @Transactional(readOnly = true)
    public List<ReportView> getAllForModeration() {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toView)
                .toList();
    }

    private ReportView toView(Report report) {
        boolean pending = report.getStatus() == ReportStatus.EN_ATTENTE;
        ReportView.ReportViewBuilder view = ReportView.builder().report(report).targetFound(false)
                .targetTitle("Cible introuvable").targetExcerpt("Supprimee depuis le signalement.")
                .targetState("Introuvable");

        if (report.getTargetType() == ReportTargetType.AVIS) {
            reviewRepository.findById(report.getTargetId()).ifPresent(review -> view
                    .targetFound(true)
                    .targetTitle("Avis " + review.getRating() + "/5 de " + review.getClient().getFullName()
                            + " sur " + review.getProfessional().getBusinessName())
                    .targetExcerpt(excerpt(review.getComment()))
                    .targetState(review.isHidden() ? "Masque" : "Visible")
                    .professionalId(review.getProfessional().getId())
                    .canHideReview(pending && !review.isHidden()));
        } else {
            professionalProfileRepository.findById(report.getTargetId()).ifPresent(profile -> view
                    .targetFound(true)
                    .targetTitle(profile.getBusinessName() + " (" + profile.getCity() + ")")
                    .targetExcerpt("Compte de " + profile.getUser().getFullName() + " - " + profile.getUser().getEmail())
                    .targetState(!profile.getUser().isEnabled() ? "Compte desactive"
                            : profile.isValidated() ? "Visible" : "Non valide")
                    .professionalId(profile.getId())
                    .canDisableProfile(pending && profile.getUser().isEnabled()));
        }
        return view.build();
    }

    private static String excerpt(String comment) {
        if (comment == null || comment.isBlank()) {
            return "(sans commentaire)";
        }
        return comment.length() <= 200 ? comment : comment.substring(0, 200) + "...";
    }

    /**
     * Signalement + description de sa cible, prete a afficher dans
     * admin/reports.html. Classe interne (voir AdminService.Metrics pour
     * l'explication) : elle n'a de sens que pour la page de moderation.
     */
    @Getter
    @Builder
    public static class ReportView {
        private Report report;
        /** false si l'avis ou le profil a ete supprime depuis le signalement. */
        private boolean targetFound;
        private String targetTitle;
        private String targetExcerpt;
        /** Visible, Masque, Non valide, Compte desactive ou Introuvable. */
        private String targetState;
        /** Professionnel concerne, pour le lien vers sa fiche admin (null si introuvable). */
        private Long professionalId;
        private boolean canHideReview;
        private boolean canDisableProfile;
    }
}
