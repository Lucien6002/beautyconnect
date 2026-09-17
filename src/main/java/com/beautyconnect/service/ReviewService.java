package com.beautyconnect.service;

import com.beautyconnect.dto.ReviewForm;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Review;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
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

    // Avis visibles publiquement (non masques par un admin).
    public List<Review> getVisibleReviews(ProfessionalProfile professional) {
        return reviewRepository.findByProfessionalAndHiddenFalseOrderByCreatedAtDesc(professional);
    }

    // Tous les avis, y compris masques : utilise par l'espace de moderation admin.
    public List<Review> getAllReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Review addReview(User client, ProfessionalProfile professional, ReviewForm form) {
        Review review = Review.builder()
                .client(client)
                .professional(professional)
                .rating(form.getRating())
                .comment(form.getComment())
                .hidden(false)
                .build();
        return reviewRepository.save(review);
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
        // un ; .mapToInt(Review::getRating) recupere seulement la note de
        // chaque avis ; .average() calcule la moyenne ; .orElse(0.0) fournit
        // une valeur par defaut si jamais le flux etait vide (securite en plus du isEmpty() ci-dessus).
        return reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
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
