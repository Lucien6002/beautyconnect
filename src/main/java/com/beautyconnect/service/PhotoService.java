package com.beautyconnect.service;

import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.*;
import com.beautyconnect.repository.*;
import com.beautyconnect.service.storage.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhotoService {
    private final ProfessionalProfileRepository profiles;
    private final UserRepository users;
    private final PrestationRepository prestations;
    private final PrestationPhotoRepository photos;
    private final ImageStorageService storage;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Prestation ownedPrestation(Long userId, Long id) {
        var prestation = prestations.findById(id).orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable"));
        if (!prestation.getProfessional().getUser().getId().equals(userId)) throw new ResourceNotFoundException("Prestation introuvable");
        // Charge la galerie avant de rendre la vue (open-in-view=false).
        prestation.getPhotos().size();
        return prestation;
    }

    @Transactional
    public void addPrestationPhoto(Long userId, Long id, MultipartFile file) {
        var prestation = ownedPrestation(userId, id);
        entityManager.lock(prestation, LockModeType.PESSIMISTIC_WRITE);
        if (photos.countByPrestation(prestation) >= 5) throw new IllegalOperationException("Maximum 5 photos par prestation.");
        var result = upload(file);
        int order = photos.findByPrestationOrderByDisplayOrderAsc(prestation).stream().mapToInt(PrestationPhoto::getDisplayOrder).max().orElse(-1) + 1;
        photos.save(PrestationPhoto.builder().prestation(prestation).photoUrl(result.secureUrl()).publicId(result.publicId()).displayOrder(order).build());
    }

    @Transactional
    public void deletePrestationPhoto(Long userId, Long id, Long photoId) {
        var prestation = ownedPrestation(userId, id);
        var photo = photos.findById(photoId).orElseThrow(() -> new ResourceNotFoundException("Photo introuvable"));
        if (!photo.getPrestation().getId().equals(id)) throw new ResourceNotFoundException("Photo introuvable");
        deleteAfterCommit(photo.getPublicId(), photo.getPhotoUrl());
        // orphanRemoval supprime la ligne ; retirer aussi l'enfant de la collection
        // évite que la cascade du parent le réenregistre lors du flush Hibernate.
        prestation.getPhotos().removeIf(existing -> existing.getId().equals(photoId));
    }

    @Transactional
    public void chooseMainPhoto(Long userId, Long id, Long photoId) {
        var prestation = ownedPrestation(userId, id);
        entityManager.lock(prestation, LockModeType.PESSIMISTIC_WRITE);
        var gallery = photos.findByPrestationOrderByDisplayOrderAsc(prestation);
        var selected = gallery.stream().filter(photo -> photo.getId().equals(photoId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Photo introuvable"));
        gallery.remove(selected);
        gallery.addFirst(selected);
        for (int i = 0; i < gallery.size(); i++) gallery.get(i).setDisplayOrder(i);
    }

    @Transactional
    public void profilePhoto(Long userId, boolean professional, MultipartFile file) {
        if (professional) {
            var profile = profiles.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Profil introuvable"));
            entityManager.lock(profile, LockModeType.PESSIMISTIC_WRITE);
            var result = file == null ? null : upload(file);
            deleteAfterCommit(profile.getProfilePhotoPublicId(), profile.getProfilePhotoUrl());
            profile.setProfilePhotoPublicId(result == null ? null : result.publicId());
            profile.setProfilePhotoUrl(result == null ? null : result.secureUrl());
        } else {
            var user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Compte introuvable"));
            entityManager.lock(user, LockModeType.PESSIMISTIC_WRITE);
            var result = file == null ? null : upload(file);
            deleteAfterCommit(user.getProfilePhotoPublicId(), user.getProfilePhotoUrl());
            user.setProfilePhotoPublicId(result == null ? null : result.publicId());
            user.setProfilePhotoUrl(result == null ? null : result.secureUrl());
        }
    }

    private ImageUploadResult upload(MultipartFile file) {
        var result = storage.upload(file);
        // Si la transaction échoue, ne laisse pas le nouveau fichier orphelin.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) removeFile(result.publicId());
            }
        });
        return result;
    }

    public void deleteAfterCommit(String publicId, String url) {
        String id = publicId != null ? publicId : url != null && url.startsWith("/uploads/") ? "local:" + url.substring(9) : null;
        if (id == null) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { removeFile(id); }
        });
    }

    private void removeFile(String id) {
        try { storage.delete(id); }
        catch (ImageDeleteException ex) { log.warn("Nettoyage photo à reprendre pour {} : {}", id, ex.getMessage()); }
    }
}
