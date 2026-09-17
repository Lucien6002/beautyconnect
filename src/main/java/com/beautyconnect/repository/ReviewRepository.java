package com.beautyconnect.repository;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acces aux donnees pour l'entite {@link Review}.
 * Voir {@link UserRepository} pour l'explication generale des requetes derivees.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    // Avis publics d'un professionnel : on exclut ceux masques par un admin
    // suite a un signalement (hidden = true), voir AdminService.
    List<Review> findByProfessionalAndHiddenFalseOrderByCreatedAtDesc(ProfessionalProfile professional);

    // Tous les avis, y compris masques : utilise dans l'espace de moderation admin.
    List<Review> findAllByOrderByCreatedAtDesc();
}
