package com.beautyconnect.service;

import com.beautyconnect.dto.PrestationForm;
import com.beautyconnect.dto.SearchCriteria;
import com.beautyconnect.dto.TimeSlotForm;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.TimeSlot;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.TimeSlotRepository;
import com.beautyconnect.utils.LocationUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        List<ProfessionalProfile> results = professionalProfileRepository.search(city, criteria.getGender(), criteria.getName(), criteria.getType());

        // 1. On vérifie si le client nous a bien envoyé ses coordonnées GPS
        if (criteria.getClientLatitude() != null && criteria.getClientLongitude() != null) {

            double clientLat = criteria.getClientLatitude();
            double clientLon = criteria.getClientLongitude();

            // 2. On trie la liste
            results.sort((pro1, pro2) -> {

                // On calcule la distance pour le Pro 1.
                // S'il n'a pas de coordonnées en base, on dit qu'il est à une distance infinie (Double.MAX_VALUE) pour le mettre à la fin.
                double dist1 = (pro1.getLatitude() != null && pro1.getLongitude() != null)
                        ? LocationUtils.calculateDistance(clientLat, clientLon, pro1.getLatitude(), pro1.getLongitude())
                        : Double.MAX_VALUE;

                // On fait pareil pour le Pro 2
                double dist2 = (pro2.getLatitude() != null && pro2.getLongitude() != null)
                        ? LocationUtils.calculateDistance(clientLat, clientLon, pro2.getLatitude(), pro2.getLongitude())
                        : Double.MAX_VALUE;

                // On compare les deux (celui qui a la plus petite distance passera devant)
                return Double.compare(dist1, dist2);
            });
        }

        return results;
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

    public List<TimeSlot> getAvailableTimeSlots(ProfessionalProfile professional) {
        return timeSlotRepository.findByProfessionalAndAvailableTrueOrderByStartDateTimeAsc(professional);
    }

    public List<TimeSlot> getAllTimeSlots(ProfessionalProfile professional) {
        return timeSlotRepository.findByProfessionalOrderByStartDateTimeAsc(professional);
    }

    @Transactional
    public TimeSlot addTimeSlot(ProfessionalProfile professional, TimeSlotForm form) {
        TimeSlot slot = TimeSlot.builder()
                .professional(professional)
                .startDateTime(form.getStartDateTime())
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
