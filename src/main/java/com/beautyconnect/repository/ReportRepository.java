package com.beautyconnect.repository;

import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportStatus;
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

    // Tous les signalements, tous statuts confondus (auteur charge, voir ci-dessus).
    @Query("SELECT r FROM Report r JOIN FETCH r.reporter ORDER BY r.createdAt DESC")
    List<Report> findAllByOrderByCreatedAtDesc();
}
