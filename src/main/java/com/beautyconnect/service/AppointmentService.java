package com.beautyconnect.service;

import com.beautyconnect.dto.AppointmentForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.*;
import com.beautyconnect.repository.AppointmentRepository;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.TimeSlotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Prise de rendez-vous, confirmation (avec envoi du mail), refus et annulation.
 * Voir {@link UserService} pour l'explication de @Service / @RequiredArgsConstructor.
 */
@Service
@RequiredArgsConstructor
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
        // Empeche de reserver un creneau appartenant a un AUTRE professionnel
        // que celui affiche sur la page (protection contre un ID trafique dans le formulaire).
        if (!slot.getProfessional().getId().equals(professional.getId())) {
            throw new IllegalOperationException("Ce creneau n'appartient pas a ce professionnel");
        }
        // Empeche le double-booking : un creneau deja pris par quelqu'un
        // d'autre ne peut pas etre repris.
        if (!slot.isAvailable()) {
            throw new IllegalOperationException("Ce creneau n'est plus disponible");
        }

        Prestation prestation = prestationRepository.findById(form.getPrestationId())
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable"));
        if (!prestation.getProfessional().getId().equals(professional.getId())) {
            throw new IllegalOperationException("Cette prestation n'appartient pas a ce professionnel");
        }

        // Marque immediatement le creneau comme indisponible, avant meme que
        // le rendez-vous ne soit confirme par le professionnel : cela evite
        // qu'un deuxieme client ne reserve le meme creneau entre-temps.
        slot.setAvailable(false);
        timeSlotRepository.save(slot);

        Appointment appointment = Appointment.builder()
                .client(client)
                .professional(professional)
                .prestation(prestation)
                .timeSlot(slot)
                .status(AppointmentStatus.EN_ATTENTE) // Le rendez-vous doit encore etre accepte par le professionnel.
                .notes(form.getNotes())
                .build();

        return appointmentRepository.save(appointment);
    }

    public List<Appointment> getAppointmentsForClient(User client) {
        return appointmentRepository.findByClientOrderByCreatedAtDesc(client);
    }

    public List<Appointment> getAppointmentsForProfessional(ProfessionalProfile professional) {
        return appointmentRepository.findByProfessionalOrderByCreatedAtDesc(professional);
    }

    // Le professionnel accepte le rendez-vous : on change le statut ET on
    // declenche l'envoi du mail de confirmation au client (cf. specifications).
    @Transactional
    public Appointment confirm(ProfessionalProfile professional, Long appointmentId) {
        Appointment appointment = getOwnedByProfessional(professional, appointmentId);
        appointment.setStatus(AppointmentStatus.CONFIRME);
        appointment = appointmentRepository.save(appointment);
        emailService.sendAppointmentConfirmation(appointment);
        return appointment;
    }

    @Transactional
    public Appointment refuse(ProfessionalProfile professional, Long appointmentId) {
        Appointment appointment = getOwnedByProfessional(professional, appointmentId);
        appointment.setStatus(AppointmentStatus.REFUSE);
        // Le creneau redevient disponible pour un autre client puisque ce
        // rendez-vous n'aura finalement pas lieu.
        releaseSlot(appointment);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment cancelByClient(User client, Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Rendez-vous introuvable"));
        if (!appointment.getClient().getId().equals(client.getId())) {
            throw new IllegalOperationException("Ce rendez-vous n'appartient pas a ce client");
        }
        appointment.setStatus(AppointmentStatus.ANNULE);
        releaseSlot(appointment);
        return appointmentRepository.save(appointment);
    }

    // Le professionnel marque le rendez-vous comme realise : cela debloque la
    // possibilite pour le client de laisser un avis (voir ReviewService).
    @Transactional
    public Appointment markCompleted(ProfessionalProfile professional, Long appointmentId) {
        Appointment appointment = getOwnedByProfessional(professional, appointmentId);
        appointment.setStatus(AppointmentStatus.TERMINE);
        return appointmentRepository.save(appointment);
    }

    private void releaseSlot(Appointment appointment) {
        TimeSlot slot = appointment.getTimeSlot();
        slot.setAvailable(true);
        timeSlotRepository.save(slot);
    }

    // Utilitaire commun a confirm/refuse/markCompleted : va chercher le
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
}
