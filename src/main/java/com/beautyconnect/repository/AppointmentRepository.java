package com.beautyconnect.repository;

import com.beautyconnect.model.Appointment;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Acces aux donnees pour l'entite {@link Appointment}.
 * Voir {@link UserRepository} pour l'explication generale des requetes derivees.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // Historique des rendez-vous d'un client, du plus recent au plus ancien.
    // JOIN FETCH : les templates affichent pro, client, prestation et creneau ;
    // sans chargement immediat, open-in-view=false provoque une LazyInitializationException.
    @Query("SELECT a FROM Appointment a JOIN FETCH a.professional JOIN FETCH a.client "
            + "JOIN FETCH a.prestation JOIN FETCH a.timeSlot "
            + "WHERE a.client = :client ORDER BY a.createdAt DESC")
    List<Appointment> findByClientOrderByCreatedAtDesc(@Param("client") User client);

    // Historique des rendez-vous recus par un professionnel.
    @Query("SELECT a FROM Appointment a JOIN FETCH a.professional JOIN FETCH a.client "
            + "JOIN FETCH a.prestation JOIN FETCH a.timeSlot "
            + "WHERE a.professional = :professional ORDER BY a.createdAt DESC")
    List<Appointment> findByProfessionalOrderByCreatedAtDesc(@Param("professional") ProfessionalProfile professional);

    // Combine 3 conditions (client + professionnel + statut) reliees par des
    // "AND" implicites (traduits du nom de la methode). Utilise par
    // AppointmentService pour verifier qu'un client a bien deja eu un
    // rendez-vous TERMINE avec ce professionnel avant de le laisser poster un avis.
    boolean existsByClientAndProfessionalAndStatus(User client, ProfessionalProfile professional,
                                                     com.beautyconnect.model.AppointmentStatus status);
}
