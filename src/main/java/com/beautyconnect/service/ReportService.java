package com.beautyconnect.service;

import com.beautyconnect.dto.ReportForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportStatus;
import com.beautyconnect.model.ReportTargetType;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.ReportRepository;
import com.beautyconnect.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public List<Report> getAll() {
        return reportRepository.findAllByOrderByCreatedAtDesc();
    }

    // Change le statut d'un signalement (ex: TRAITE ou REJETE) une fois que
    // l'administrateur l'a examine.
    @Transactional
    public Report updateStatus(Long reportId, ReportStatus status) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable : " + reportId));
        report.setStatus(status);
        return reportRepository.save(report);
    }
}
