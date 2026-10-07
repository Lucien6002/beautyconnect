package com.beautyconnect.service;

import com.beautyconnect.dto.AppointmentForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.*;
import com.beautyconnect.repository.AppointmentRepository;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prise de rendez-vous (confirmee immediatement, avec envoi du mail) et annulation par le client.
 * Voir {@link UserService} pour l'explication de @Service / @RequiredArgsConstructor.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final PrestationRepository prestationRepository;
    private final EmailService emailService;

    /**
     * Cree un rendez-vous pour un client, apres avoir verifie que le creneau
     * et la prestation choisis sont valides et disponibles.
     */
    @Transactional
    public Appointment book(User client, ProfessionalProfile professional, AppointmentForm form) {
        TimeSlot slot = timeSlotRepository.findById(form.getTimeSlotId())
                .orElseThrow(() -> new ResourceNotFoundException("Creneau introuvable"));

        if (!slot.getProfessional().getId().equals(professional.getId())) {
            throw new IllegalOperationException("Ce creneau n'appartient pas a ce professionnel");
        }
        if (!slot.isAvailable()) {
            throw new IllegalOperationException("Ce creneau n'est plus disponible");
        }


        // Vérifier que le créneau n'est pas dans le passé
        if (!slot.getStartDateTime().isAfter(LocalDateTime.now())) {
            throw new IllegalOperationException("Ce créneau est dans le passé et ne peut plus être réservé.");
        }

        // Vérifier que le pro est validé par l'admin ET que son compte est actif
        if (!professional.isValidated() || !professional.getUser().isEnabled()) {
            throw new IllegalOperationException("Ce professionnel n'est pas disponible pour le moment.");
        }

        Prestation prestation = prestationRepository.findById(form.getPrestationId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable"));

        if (!prestation.getProfessional().getId().equals(professional.getId())) {
            throw new IllegalOperationException("Cette prestation n'appartient pas a ce professionnel");
        }

        // Vérifier que la prestation n'a pas été désactivée par le pro
        if (!prestation.isActive()) {
            throw new IllegalOperationException("Cette prestation n'est plus proposée par le professionnel.");
        }

        // Marque immediatement le creneau comme indisponible
        slot.setAvailable(false);
        timeSlotRepository.save(slot);

        Appointment appointment = Appointment.builder()
                .client(client)
                .professional(professional)
                .prestation(prestation)
                .timeSlot(slot)
                .status(AppointmentStatus.CONFIRME)
                .notes(form.getNotes())
                .build();

        // Note sur la concurrence : Si deux clients arrivent EXACTEMENT à la même milliseconde
        // et passent les 'if' ci-dessus, la base de données PostgreSQL va rejeter le deuxième
        // car la colonne 'time_slot_id' de la table 'appointments' est UNIQUE (voir entité Appointment).
        // Cela garantit "un seul gagnant concurrent".
        appointment = appointmentRepository.save(appointment);
        // Le rendez-vous est confirme des la reservation (le creneau ouvert par le
        // pro equivaut a "salon ouvert") : mail de confirmation immediat. Un echec
        // d'envoi ne doit pas annuler la reservation deja enregistree.
        try {
            emailService.sendAppointmentConfirmation(appointment);
        } catch (Exception ex) {
            log.warn("Mail de confirmation non envoye pour le rendez-vous {} : {}", appointment.getId(), ex.getMessage());
        }
        return appointment;
    }

    public List<Appointment> getAppointmentsForClient(User client) {
        return appointmentRepository.findByClientOrderByCreatedAtDesc(client);
    }

    public List<Appointment> getAppointmentsForProfessional(ProfessionalProfile professional) {
        return appointmentRepository.findByProfessionalOrderByCreatedAtDesc(professional);
    }

    @Transactional
    public Appointment cancelByClient(User client, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Rendez-vous introuvable"));
        if (!appointment.getClient().getId().equals(client.getId())) {
            throw new IllegalOperationException("Ce rendez-vous n'appartient pas a ce client");
        }
        validateTransition(appointment.getStatus(), AppointmentStatus.ANNULE);
        appointment.setStatus(AppointmentStatus.ANNULE);
        releaseSlot(appointment);
        return appointmentRepository.save(appointment);
    }

    // Le professionnel marque le rendez-vous comme realise : cela debloque la
    // possibilite pour le client de laisser un avis (voir ReviewService).
    @Transactional
    public Appointment markCompleted(ProfessionalProfile professional, Long appointmentId) {
        Appointment appointment = getOwnedByProfessional(professional, appointmentId);
        validateTransition(appointment.getStatus(), AppointmentStatus.TERMINE);
        appointment.setStatus(AppointmentStatus.TERMINE);
        return appointmentRepository.save(appointment);
    }

    private void releaseSlot(Appointment appointment) {
        TimeSlot slot = appointment.getTimeSlot();
        slot.setAvailable(true);
        timeSlotRepository.save(slot);
    }

    // Utilitaire commun a markCompleted : va chercher le
    // rendez-vous et verifie au passage qu'il appartient bien au
    // professionnel connecte (meme logique de securite que dans ProfessionalService).
    private Appointment getOwnedByProfessional(ProfessionalProfile professional, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Rendez-vous introuvable"));
        if (!appointment.getProfessional().getId().equals(professional.getId())) {
            throw new IllegalOperationException("Ce rendez-vous n'appartient pas a ce professionnel");
        }
        return appointment;
    }

    private void validateTransition(AppointmentStatus currentStatus, AppointmentStatus targetStatus) {
        boolean isValid = switch (currentStatus) {
            case EN_ATTENTE -> targetStatus == AppointmentStatus.CONFIRME
                    || targetStatus == AppointmentStatus.REFUSE
                    || targetStatus == AppointmentStatus.ANNULE;
            case CONFIRME   -> targetStatus == AppointmentStatus.TERMINE
                    || targetStatus == AppointmentStatus.ANNULE;
            case REFUSE, ANNULE, TERMINE -> false; // États finaux, on ne bouge plus !
        };

        if (!isValid) {
            throw new IllegalOperationException("Impossible de passer le rendez-vous de l'état "
                    + currentStatus + " à " + targetStatus);
        }
    }


}
