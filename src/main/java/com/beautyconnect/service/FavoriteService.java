package com.beautyconnect.service;

import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Favorite;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.FavoriteRepository;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Gestion des professionnels favoris d'un client. */
@Service
@RequiredArgsConstructor
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final UserRepository userRepository;

    // Idempotent : ajouter deux fois le meme professionnel ne fait rien.
    // Seuls les profils publics (valides, compte actif) peuvent etre mis en favori.
    @Transactional
    public void add(Long clientId, Long professionalId) {
        User client = getClient(clientId);
        ProfessionalProfile professional = professionalProfileRepository.findPublicById(professionalId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil professionnel introuvable : " + professionalId));
        if (!favoriteRepository.existsByClientAndProfessional(client, professional)) {
            favoriteRepository.save(Favorite.builder().client(client).professional(professional).build());
        }
    }

    @Transactional
    public void remove(Long clientId, Long professionalId) {
        User client = getClient(clientId);
        professionalProfileRepository.findById(professionalId)
                .ifPresent(professional -> favoriteRepository.deleteByClientAndProfessional(client, professional));
    }

    @Transactional(readOnly = true)
    public boolean isFavorite(Long clientId, Long professionalId) {
        User client = getClient(clientId);
        return professionalProfileRepository.findById(professionalId)
                .map(professional -> favoriteRepository.existsByClientAndProfessional(client, professional))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<ProfessionalProfile> getFavorites(Long clientId) {
        return favoriteRepository.findVisibleByClient(getClient(clientId)).stream()
                .map(Favorite::getProfessional).toList();
    }

    private User getClient(Long clientId) {
        return userRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Compte introuvable"));
    }
}
