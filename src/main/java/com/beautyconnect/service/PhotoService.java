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

    /** Toutes les modifications de galerie sont validées et enregistrées ensemble. */
    @Transactional
    public void updateGallery(Long userId, Long id, MultipartFile mainFile,
            java.util.List<MultipartFile> secondaryFiles, java.util.List<Long> deleteIds, Long mainPhotoId) {
        var prestation = ownedPrestation(userId, id);
        entityManager.lock(prestation, LockModeType.PESSIMISTIC_WRITE);
        var existing = new java.util.ArrayList<>(prestation.getPhotos());
        var removals = deleteIds == null ? new java.util.HashSet<Long>() : new java.util.HashSet<>(deleteIds);
        var existingIds = existing.stream().map(PrestationPhoto::getId).collect(java.util.stream.Collectors.toSet());
        if (!existingIds.containsAll(removals) || (mainPhotoId != null && !existingIds.contains(mainPhotoId))) {
            throw new ResourceNotFoundException("Photo introuvable pour cette prestation");
        }
        boolean replaceMain = mainFile != null && !mainFile.isEmpty();
        var additions = secondaryFiles == null ? java.util.List.<MultipartFile>of()
                : secondaryFiles.stream().filter(file -> !file.isEmpty()).toList();
        if (replaceMain && !existing.isEmpty()) removals.add(existing.getFirst().getId());
        int remaining = existing.size() - removals.size();
        if (additions.size() > 4 || remaining + additions.size() + (replaceMain ? 1 : 0) > 5) {
            throw new IllegalOperationException("Maximum 5 photos : supprimez une photo ou remplacez la principale avant d'en ajouter.");
        }
        if (!additions.isEmpty() && remaining == 0 && !replaceMain) {
            throw new IllegalOperationException("Ajoutez une photo principale avant les photos secondaires.");
        }
        for (var photo : existing) {
            if (removals.contains(photo.getId())) {
                deleteAfterCommit(photo.getPublicId(), photo.getPhotoUrl());
                prestation.getPhotos().remove(photo);
            }
        }
        var ordered = new java.util.ArrayList<>(prestation.getPhotos());
        if (replaceMain) ordered.addFirst(attachPhoto(prestation, mainFile));
        else if (mainPhotoId != null && !removals.contains(mainPhotoId)) {
            var selected = ordered.stream().filter(photo -> photo.getId().equals(mainPhotoId)).findFirst().orElseThrow();
            ordered.remove(selected); ordered.addFirst(selected);
        }
        for (var file : additions) ordered.add(attachPhoto(prestation, file));
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setDisplayOrder(i);
    }

    private PrestationPhoto attachPhoto(Prestation prestation, MultipartFile file) {
        var result = upload(file);
        var photo = photos.save(PrestationPhoto.builder().prestation(prestation).photoUrl(result.secureUrl())
                .publicId(result.publicId()).displayOrder(prestation.getPhotos().size()).build());
        prestation.getPhotos().add(photo);
        return photo;
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
