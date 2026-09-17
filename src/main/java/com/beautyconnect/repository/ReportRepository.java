package com.beautyconnect.repository;

import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acces aux donnees pour l'entite {@link Report}.
 * Voir {@link UserRepository} pour l'explication generale des requetes derivees.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    // Signalements filtres par statut (ex: EN_ATTENTE pour la file de moderation admin).
    List<Report> findByStatusOrderByCreatedAtDesc(ReportStatus status);

    // Tous les signalements, tous statuts confondus.
    List<Report> findAllByOrderByCreatedAtDesc();
}
