package com.beautyconnect.repository;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.TimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Acces aux donnees pour l'entite {@link TimeSlot}.
 * Voir {@link UserRepository} pour l'explication generale des requetes derivees.
 */
public interface TimeSlotRepository extends JpaRepository<TimeSlot, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TimeSlot t WHERE t.id = :id")
    Optional<TimeSlot> findByIdForUpdate(@Param("id") Long id);

    List<TimeSlot> findByProfessionalAndAvailableTrueAndStartDateTimeAfterOrderByStartDateTimeAsc(
            ProfessionalProfile professional, LocalDateTime now);

    // SELECT * FROM time_slots WHERE professional_id = ? AND available = true ORDER BY start_date_time ASC
    // "OrderByStartDateTimeAsc" trie directement le resultat du plus proche au
    // plus lointain dans le temps : pratique pour l'affichage cote client.
    List<TimeSlot> findByProfessionalAndAvailableTrueOrderByStartDateTimeAsc(ProfessionalProfile professional);

    // Meme chose mais sans filtrer sur "available" : utilise cote pro pour voir
    // TOUS ses creneaux, y compris ceux deja reserves.
    List<TimeSlot> findByProfessionalOrderByStartDateTimeAsc(ProfessionalProfile professional);

    // Detection des doublons : ce professionnel a-t-il deja un creneau qui
    // commence exactement a cette date et heure ?
    boolean existsByProfessionalAndStartDateTime(ProfessionalProfile professional, LocalDateTime startDateTime);
}
