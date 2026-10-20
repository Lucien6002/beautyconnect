package com.beautyconnect.service;

import com.beautyconnect.dto.PrestationForm;
import com.beautyconnect.dto.SearchCriteria;
import com.beautyconnect.dto.TimeSlotForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.TimeSlot;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Gestion de la vitrine professionnelle : profil, prestations (avec duree
 * et tarif), creneaux de disponibilite, et recherche publique.
 *
 * Voir {@link UserService} pour l'explication de @Service / @RequiredArgsConstructor.
 */
@Service
@RequiredArgsConstructor
public class ProfessionalService {

    private final ProfessionalProfileRepository professionalProfileRepository;
    private final PrestationRepository prestationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final com.beautyconnect.repository.AppointmentRepository appointmentRepository;
    private final com.beautyconnect.repository.PrestationPhotoRepository photoRepository;
    private final PhotoService photoService;

    @Transactional(readOnly = true)
    public List<Prestation> getPublicPrestationPreviews() {
        var previews = new java.util.ArrayList<Prestation>();
        for (var type : com.beautyconnect.model.ServiceType.values()) {
            previews.addAll(prestationRepository.findPublicPreviewsByType(type, org.springframework.data.domain.PageRequest.of(0, 2)));
        }
        if (previews.size() < 6) {
            for (var candidate : prestationRepository.findPublicPreviews(org.springframework.data.domain.PageRequest.of(0, 6))) {
                if (previews.size() == 6) break;
                if (previews.stream().noneMatch(p -> p.getId().equals(candidate.getId()))) previews.add(candidate);
            }
        }
        previews.forEach(p -> p.getPhotos().size());
        return previews;
    }

    // orElseThrow(...) : si findById ne trouve rien (Optional vide), on leve
    // une exception metier personnalisee plutot que de retourner null (ce qui
    // provoquerait un NullPointerException plus loin, difficile a diagnostiquer).
    public ProfessionalProfile getProfileOrThrow(Long id) {
        return professionalProfileRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profil professionnel introuvable : " + id));
    }

    // Variante pour les pages publiques et la reservation : un professionnel
    // dont le compte a ete desactive par l'admin est traite comme introuvable.
    public ProfessionalProfile getPublicProfileOrThrow(Long id) {
        return professionalProfileRepository.findPublicById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Profil professionnel introuvable : " + id));
    }

    public ProfessionalProfile getProfileByUserId(Long userId) {
        return professionalProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Aucun profil professionnel pour cet utilisateur"));
    }

    // Point d'entree du moteur de recherche public (voir SearchController).
    public List<ProfessionalProfile> search(SearchCriteria criteria) {
        return professionalProfileRepository.search(clean(criteria.getCity()), criteria.getGender(),
                clean(criteria.getName()), criteria.getType());
    }

    public org.springframework.data.domain.Page<ProfessionalProfile> searchPage(SearchCriteria criteria) {
        if (com.beautyconnect.utils.LocationUtils.isValidPosition(criteria.getClientLatitude(), criteria.getClientLongitude())) {
            return professionalProfileRepository.searchPageNear(clean(criteria.getCity()), criteria.getGender(),
                    clean(criteria.getName()), criteria.getType(), Math.toRadians(criteria.getClientLatitude()),
                    criteria.getClientLongitude(), org.springframework.data.domain.PageRequest.of(criteria.getPage(), 6));
        }
        return professionalProfileRepository.searchPage(clean(criteria.getCity()), criteria.getGender(),
                clean(criteria.getName()), criteria.getType(),
                org.springframework.data.domain.PageRequest.of(criteria.getPage(), 6));
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @Transactional
    public ProfessionalProfile updateProfile(ProfessionalProfile profile, String businessName, String bio,
                                              String city, String address,
                                              com.beautyconnect.model.TargetGender targetGender) {
        // profile a ete charge par Hibernate plus haut dans la chaine d'appel
        // (dans le controleur) : modifier ses champs puis appeler save() suffit
        // a mettre a jour la ligne correspondante en base (pas besoin de
        // reconstruire un objet entier).
        profile.setBusinessName(businessName);
        profile.setBio(bio);
        profile.setCity(city);
        profile.setAddress(address);
        profile.setTargetGender(targetGender);
        return professionalProfileRepository.save(profile);
    }

    @Transactional
    public ProfessionalProfile updateProfile(ProfessionalProfile profile) {
        return professionalProfileRepository.save(profile);
    }

    public List<Prestation> getPrestations(ProfessionalProfile professional) {
        return prestationRepository.findByProfessional(professional);
    }

    public List<Prestation> getActivePrestations(ProfessionalProfile professional) {
        return prestationRepository.findByProfessionalAndActiveTrue(professional);
    }

    public List<Prestation> getRemovedPrestations(ProfessionalProfile professional) {
        return prestationRepository.findByProfessionalAndActiveFalse(professional);
    }

    public long countRemovedPrestations(ProfessionalProfile professional) {
        return prestationRepository.countByProfessionalAndActiveFalse(professional);
    }

    @Transactional
    public Prestation addPrestation(ProfessionalProfile professional, PrestationForm form) {
        Prestation prestation = Prestation.builder()
                .professional(professional)
                .name(form.getName())
                .description(form.getDescription())
                .type(form.getType())
                .price(form.getPrice())
                .durationMinutes(form.getDurationMinutes())
                .active(true)
                .build();
        return prestationRepository.save(prestation);
    }

    /** Une création et toutes ses photos dans la même transaction. */
    @Transactional
    public Prestation addPrestationWithPhotos(ProfessionalProfile professional, PrestationForm form,
            MultipartFile mainPhoto, List<MultipartFile> secondaryPhotos) {
        var secondary = secondaryPhotos == null ? List.<MultipartFile>of()
                : secondaryPhotos.stream().filter(file -> !file.isEmpty()).toList();
        boolean hasMain = mainPhoto != null && !mainPhoto.isEmpty();
        if (secondary.size() > 4) throw new IllegalOperationException("Choisissez au maximum quatre photos secondaires.");
        if (!secondary.isEmpty() && !hasMain) throw new IllegalOperationException("Ajoutez une photo principale avant les photos secondaires.");
        var prestation = addPrestation(professional, form);
        if (hasMain) photoService.addPrestationPhoto(professional.getUser().getId(), prestation.getId(), mainPhoto);
        for (var photo : secondary) photoService.addPrestationPhoto(professional.getUser().getId(), prestation.getId(), photo);
        return prestation;
    }

    @Transactional
    public void addPrestationImage(ProfessionalProfile professional, Long prestationId, MultipartFile file) {
        photoService.addPrestationPhoto(professional.getUser().getId(), prestationId, file);
    }

    @Transactional
    public void updatePrestationWithPhotos(ProfessionalProfile professional, Long id, PrestationForm form,
            MultipartFile mainFile, List<MultipartFile> secondaryFiles, List<Long> deleteIds, Long mainPhotoId) {
        updatePrestation(professional, id, form);
        photoService.updateGallery(professional.getUser().getId(), id, mainFile, secondaryFiles, deleteIds, mainPhotoId);
    }

    @Transactional
    public void updatePrestation(ProfessionalProfile professional, Long id, PrestationForm form) {
        var prestation = prestationRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable"));
        assertOwnership(professional, prestation.getProfessional());
        prestation.setName(form.getName().trim());
        prestation.setDescription(form.getDescription());
        prestation.setType(form.getType());
        prestation.setPrice(form.getPrice());
        prestation.setDurationMinutes(form.getDurationMinutes());
    }

    @Transactional
    public void removePrestation(ProfessionalProfile professional, Long prestationId) {
        Prestation prestation = prestationRepository.findById(prestationId)
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable : " + prestationId));
        // Verifie que le professionnel connecte est bien le proprietaire de
        // cette prestation, pour eviter qu'il modifie les donnees d'un autre
        // professionnel en devinant/forcant un ID dans l'URL.
        assertOwnership(professional, prestation.getProfessional());
        // "Suppression" douce (soft delete) : on desactive au lieu de
        // supprimer la ligne, pour ne pas casser l'historique des rendez-vous
        // deja pris sur cette prestation (qui referencent son ID).
        prestation.setActive(false);
        prestationRepository.save(prestation);
    }

    @Transactional
    public void permanentlyDeletePrestation(ProfessionalProfile professional, Long prestationId) {
        Prestation prestation = prestationRepository.findById(prestationId)
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable : " + prestationId));
        assertOwnership(professional, prestation.getProfessional());

        // Vérifier qu'il n'y a pas de rendez-vous sur cette prestation
        if (appointmentRepository.existsByPrestationId(prestationId)) {
            throw new IllegalOperationException("Impossible de supprimer définitivement : des rendez-vous existent sur cette prestation.");
        }

        if (prestation.isActive()) throw new IllegalOperationException("Retirez d'abord la prestation avant de la supprimer définitivement.");
        photoRepository.findByPrestationOrderByDisplayOrderAsc(prestation).forEach(photo ->
                photoService.deleteAfterCommit(photo.getPublicId(), photo.getPhotoUrl()));

        // Supprimer la prestation définitivement
        prestationRepository.delete(prestation);
    }

    // Inverse de removePrestation : la prestation retiree redevient visible
    // sur la vitrine publique et reservable. Possible car le retrait est une
    // suppression douce (la ligne est conservee en base).
    @Transactional
    public Prestation restorePrestation(ProfessionalProfile professional, Long prestationId) {
        Prestation prestation = prestationRepository.findById(prestationId)
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable : " + prestationId));
        assertOwnership(professional, prestation.getProfessional());
        if (prestation.isActive()) {
            throw new IllegalOperationException("La prestation \"" + prestation.getName() + "\" est deja active.");
        }
        prestation.setActive(true);
        return prestationRepository.save(prestation);
    }

    public List<TimeSlot> getAvailableTimeSlots(ProfessionalProfile professional) {
        return timeSlotRepository.findByProfessionalAndAvailableTrueAndStartDateTimeAfterOrderByStartDateTimeAsc(professional, LocalDateTime.now());
    }

    // Creneaux a venir uniquement (disponibles ou reserves) : les creneaux passes
    // ne sont plus affiches dans la gestion des creneaux du professionnel.
    public List<TimeSlot> getAllTimeSlots(ProfessionalProfile professional) {
        return timeSlotRepository.findByProfessionalAndStartDateTimeAfterOrderByStartDateTimeAsc(professional, LocalDateTime.now());
    }

    // Refuse un creneau qui existe deja pour ce professionnel (meme jour et
    // meme heure, qu'il soit disponible ou deja reserve). Les secondes sont
    // ignorees : le formulaire saisit l'heure a la minute pres.
    @Transactional
    public TimeSlot addTimeSlot(ProfessionalProfile professional, TimeSlotForm form) {
        LocalDateTime start = form.getStartDateTime().truncatedTo(ChronoUnit.MINUTES);
        if (timeSlotRepository.existsByProfessionalAndStartDateTime(professional, start)) {
            throw new IllegalOperationException("Vous avez deja un creneau le "
                    + start.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm")) + ".");
        }
        TimeSlot slot = TimeSlot.builder()
                .professional(professional)
                .startDateTime(start)
                .available(true)
                .build();
        return timeSlotRepository.save(slot);
    }

    @Transactional
    public void removeTimeSlot(ProfessionalProfile professional, Long timeSlotId) {
        TimeSlot slot = timeSlotRepository.findByIdForUpdate(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau introuvable : " + timeSlotId));
        assertOwnership(professional, slot.getProfessional());
        // Un creneau reserve ne peut pas etre supprime par le pro : seul le
        // client peut annuler son rendez-vous (ce qui libere le creneau).
        if (!slot.isAvailable()) {
            throw new IllegalOperationException("Ce creneau est reserve : seul le client peut annuler son rendez-vous.");
        }
        // Ici on supprime vraiment (contrairement a removePrestation) : un
        // creneau non reserve n'est reference par aucun rendez-vous, sa
        // suppression est donc sans danger pour l'integrite des donnees.
        if (appointmentRepository.existsByTimeSlotId(timeSlotId)) {
            throw new IllegalOperationException("Ce créneau appartient à l'historique des rendez-vous et ne peut pas être supprimé.");
        }
        timeSlotRepository.delete(slot);
    }

    // Regle de securite "au niveau metier" (en plus de Spring Security) :
    // empeche un professionnel A de modifier/supprimer une ressource
    // appartenant a un professionnel B, meme s'il connait/devine son ID.
    private void assertOwnership(ProfessionalProfile expected, ProfessionalProfile actual) {
        if (!expected.getId().equals(actual.getId())) {
            throw new ResourceNotFoundException("Cette ressource n'appartient pas a ce professionnel");
        }
    }
}
