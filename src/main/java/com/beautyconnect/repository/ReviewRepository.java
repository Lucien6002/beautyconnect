package com.beautyconnect.repository;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
    // JOIN FETCH charge le client et le professionnel dans la meme requete :
    // ces relations sont LAZY et open-in-view=false, la page admin/reviews.html
    // ne pourrait pas les lire apres la fermeture de la session Hibernate.
    @Query("SELECT r FROM Review r JOIN FETCH r.client JOIN FETCH r.professional ORDER BY r.createdAt DESC")
    List<Review> findAllByOrderByCreatedAtDesc();
}
