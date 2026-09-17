package com.beautyconnect.service;

import com.beautyconnect.dto.ReportForm;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportStatus;
import com.beautyconnect.model.ReportTargetType;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Signalements d'avis ou de profils professionnels, geres par l'administrateur.
 * Voir {@link UserService} pour l'explication de @Service / @RequiredArgsConstructor.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;

    // Cree un signalement. targetType/targetId identifient generiquement ce
    // qui est signale (un avis ou un profil professionnel), voir Report.java.
    @Transactional
    public Report create(User reporter, ReportTargetType targetType, Long targetId, ReportForm form) {
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reason(form.getReason())
                .status(ReportStatus.EN_ATTENTE)
                .build();
        return reportRepository.save(report);
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
