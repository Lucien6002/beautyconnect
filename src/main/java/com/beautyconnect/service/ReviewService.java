package com.beautyconnect.service;

import com.beautyconnect.dto.ReviewForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Appointment;
import com.beautyconnect.model.AppointmentStatus;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Review;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.AppointmentRepository;
import com.beautyconnect.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestion des avis clients laisses sur les professionnels.
 * Voir {@link UserService} pour l'explication de @Service / @RequiredArgsConstructor.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final AppointmentRepository appointmentRepository;

    // Avis visibles publiquement (non masques par un admin).
    public List<Review> getVisibleReviews(ProfessionalProfile professional) {
        return reviewRepository.findByProfessionalAndHiddenFalseOrderByCreatedAtDesc(professional);
    }

    // Tous les avis, y compris masques : utilise par l'espace de moderation admin.
    public List<Review> getAllReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc();
    }

    // Depot d'un avis. Deux cas :
    //  - avis libre (pas de rendez-vous) : autorise, mais un seul par client
    //    et par professionnel, pour eviter qu'un meme compte noie la moyenne ;
    //  - avis verifie (appointmentId renseigne) : le rendez-vous doit etre
    //    TERMINE, appartenir au client connecte et concerner ce
    //    professionnel ; un seul avis par rendez-vous.
    // Toute violation leve une IllegalOperationException, transformee en
    // message flash par ReviewController.
    @Transactional
    public Review addReview(User client, ProfessionalProfile professional, ReviewForm form) {
        Appointment appointment = form.getAppointmentId() == null
                ? checkFreeReviewAllowed(client, professional)
                : loadReviewableAppointment(client, professional, form.getAppointmentId());

        Review review = Review.builder()
                .client(client)
                .professional(professional)
                .appointment(appointment)
                .rating(form.getRating())
                .comment(form.getComment())
                .hidden(false)
                .build();
        try {
            // saveAndFlush : force l'INSERT ici pour intercepter la violation
            // de l'index unique (deux envois simultanes du meme avis verifie)
            // et la transformer en message clair plutot qu'en page 500.
            return reviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException ex) {
            throw new IllegalOperationException("Vous avez deja laisse un avis pour ce rendez-vous.");
        }
    }

    // Avis libre : refuse si le client a deja un avis libre chez ce professionnel.
    // Retourne null (aucun rendez-vous a rattacher a l'avis).
    private Appointment checkFreeReviewAllowed(User client, ProfessionalProfile professional) {
        if (reviewRepository.existsByClientAndProfessionalAndAppointmentIsNull(client, professional)) {
            throw new IllegalOperationException("Vous avez deja laisse un avis a ce professionnel.");
        }
        return null;
    }

    // Avis verifie : le rendez-vous doit etre une preuve valable de prestation.
    // Meme message pour "inexistant", "d'un autre client" et "d'un autre
    // professionnel" : on ne revele pas a un tiers l'existence d'un rendez-vous.
    private Appointment loadReviewableAppointment(User client, ProfessionalProfile professional, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .filter(a -> a.getClient().getId().equals(client.getId()))
                .filter(a -> a.getProfessional().getId().equals(professional.getId()))
                .orElseThrow(() -> new IllegalOperationException("Ce rendez-vous ne vous permet pas de laisser un avis."));
        if (appointment.getStatus() != AppointmentStatus.TERMINE) {
            throw new IllegalOperationException("Vous pourrez laisser un avis une fois la prestation terminee.");
        }
        if (reviewRepository.existsByAppointmentId(appointmentId)) {
            throw new IllegalOperationException("Vous avez deja laisse un avis pour ce rendez-vous.");
        }
        return appointment;
    }

    // Calcule la moyenne des notes affichee sur la vitrine du professionnel.
    // Calcul fait en Java (via l'API Stream) plutot qu'en SQL (AVG(...)) ici
    // car on reutilise la meme liste deja filtree (avis visibles uniquement).
    public double getAverageRating(ProfessionalProfile professional) {
        List<Review> reviews = getVisibleReviews(professional);
        if (reviews.isEmpty()) {
            return 0.0;
        }
        // .stream() transforme la liste en flux d'elements traitables un par
        // un ; .mapToInt(r -> r.getRating()) recupere seulement la note de
        // chaque avis ; .average() calcule la moyenne ; .orElse(0.0) fournit
        // une valeur par defaut si jamais le flux etait vide (securite en plus du isEmpty() ci-dessus).
        return reviews.stream().mapToInt(r -> r.getRating()).average().orElse(0.0);
    }

    // Masque un avis suite a un signalement traite par l'administrateur
    // (l'avis reste en base, mais n'apparait plus dans getVisibleReviews()).
    @Transactional
    public void hide(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Avis introuvable : " + reviewId));
        review.setHidden(true);
        reviewRepository.save(review);
    }
}
