package com.beautyconnect.repository;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Review;
import com.beautyconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Acces aux donnees pour l'entite {@link Review}.
 * Voir {@link UserRepository} pour l'explication generale des requetes derivees.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    interface RatingSummary { Long getProfessionalId(); Double getAverage(); }

    @org.springframework.data.jpa.repository.Query("SELECT r.professional.id AS professionalId, AVG(r.rating) AS average FROM Review r WHERE r.hidden = false AND r.professional.id IN :ids GROUP BY r.professional.id")
    List<RatingSummary> averageRatings(@org.springframework.data.repository.query.Param("ids") List<Long> ids);

    // Avis publics d'un professionnel : on exclut ceux masques par un admin
    // suite a un signalement (hidden = true), voir AdminService.
    // La fiche lit r.client.firstName après fermeture de la session Hibernate.
    @Query("SELECT r FROM Review r JOIN FETCH r.client WHERE r.professional = :professional AND r.hidden = false ORDER BY r.createdAt DESC")
    List<Review> findByProfessionalAndHiddenFalseOrderByCreatedAtDesc(
            @org.springframework.data.repository.query.Param("professional") ProfessionalProfile professional);

    // Tous les avis, y compris masques : utilise dans l'espace de moderation admin.
    // JOIN FETCH charge le client et le professionnel dans la meme requete :
    // ces relations sont LAZY et open-in-view=false, la page admin/reviews.html
    // ne pourrait pas les lire apres la fermeture de la session Hibernate.
    @Query("SELECT r FROM Review r JOIN FETCH r.client JOIN FETCH r.professional ORDER BY r.createdAt DESC")
    List<Review> findAllByOrderByCreatedAtDesc();

    @Query("SELECT r.appointment.id FROM Review r WHERE r.client = :client AND r.appointment IS NOT NULL")
    java.util.Set<Long> reviewedAppointmentIds(@org.springframework.data.repository.query.Param("client") User client);

    // Un avis existe-t-il deja pour ce rendez-vous ? (un seul avis par rendez-vous)
    boolean existsByAppointmentId(Long appointmentId);

    // Le client a-t-il deja laisse un avis libre (sans rendez-vous) a ce
    // professionnel ? Les avis masques par l'admin comptent aussi : sinon un
    // client dont l'avis abusif a ete masque pourrait simplement le reposter.
    boolean existsByClientAndProfessionalAndAppointmentIsNull(User client, ProfessionalProfile professional);
}
