package com.beautyconnect.repository;

import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportStatus;
import com.beautyconnect.model.ReportTargetType;
import com.beautyconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Acces aux donnees pour l'entite {@link Report}.
 * Voir {@link UserRepository} pour l'explication generale des requetes derivees.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    // Signalements filtres par statut (ex: EN_ATTENTE pour la file de moderation admin).
    // JOIN FETCH charge l'auteur du signalement dans la meme requete : la
    // relation est LAZY et open-in-view=false, les pages admin ne pourraient
    // pas lire reporter.fullName apres la fermeture de la session Hibernate.
    @Query("SELECT r FROM Report r JOIN FETCH r.reporter WHERE r.status = :status ORDER BY r.createdAt DESC")
    List<Report> findByStatusOrderByCreatedAtDesc(@Param("status") ReportStatus status);

    // Tous les signalements, tous statuts confondus. Charge aussi l'admin qui
    // a decide (LEFT : null tant que le signalement est en attente), affiche
    // dans le journal de moderation de admin/reports.html.
    @Query("SELECT r FROM Report r JOIN FETCH r.reporter LEFT JOIN FETCH r.handledBy ORDER BY r.createdAt DESC")
    List<Report> findAllByOrderByCreatedAtDesc();

    // Anti-spam : cet auteur a-t-il deja un signalement de ce statut
    // (EN_ATTENTE) sur cette cible ? Voir ReportService.create.
    boolean existsByReporterAndTargetTypeAndTargetIdAndStatus(User reporter, ReportTargetType targetType,
                                                              Long targetId, ReportStatus status);

    // Tous les signalements d'un statut donne sur une meme cible : une
    // decision qui agit sur la cible (masquer l'avis, desactiver le compte)
    // clot aussi les autres signalements en attente sur elle.
    List<Report> findByTargetTypeAndTargetIdAndStatus(ReportTargetType targetType, Long targetId, ReportStatus status);
}
