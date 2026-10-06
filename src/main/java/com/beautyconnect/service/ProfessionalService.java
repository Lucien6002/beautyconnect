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
        // Un champ ville vide/blanc dans le formulaire est traite comme
        // "aucun filtre" (null) plutot que comme "chercher les professionnels
        // dont la ville est une chaine vide", ce qui n'aurait aucun sens.
        String city = (criteria.getCity() == null || criteria.getCity().isBlank()) ? null : criteria.getCity().trim();
        return professionalProfileRepository.search(city, criteria.getGender(), criteria.getType());
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
        return timeSlotRepository.findByProfessionalAndAvailableTrueOrderByStartDateTimeAsc(professional);
    }

    public List<TimeSlot> getAllTimeSlots(ProfessionalProfile professional) {
        return timeSlotRepository.findByProfessionalOrderByStartDateTimeAsc(professional);
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
        TimeSlot slot = timeSlotRepository.findById(timeSlotId)
                .orElseThrow(() -> new ResourceNotFoundException("Creneau introuvable : " + timeSlotId));
        assertOwnership(professional, slot.getProfessional());
        // Ici on supprime vraiment (contrairement a removePrestation) : un
        // creneau non reserve n'est reference par aucun rendez-vous, sa
        // suppression est donc sans danger pour l'integrite des donnees.
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
