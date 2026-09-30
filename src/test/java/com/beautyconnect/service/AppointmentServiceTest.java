package com.beautyconnect.service;

import com.beautyconnect.dto.AppointmentForm;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.*;
import com.beautyconnect.repository.AppointmentRepository;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.TimeSlotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private EmailService emailService; // On le mocke pour ne pas envoyer de vrais mails pendant les tests

    @Mock
    private TimeSlotRepository timeSlotRepository;

    @Mock
    private PrestationRepository prestationRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    @Test
    void confirm_ShouldThrowException_WhenAppointmentIsAlreadyCancelled() {
        // 1. Préparation des fausses données (Arrange)
        ProfessionalProfile pro = ProfessionalProfile.builder().id(1L).build();

        Appointment cancelledAppointment = Appointment.builder()
                .id(100L)
                .professional(pro)
                .status(AppointmentStatus.ANNULE) // Le RDV est déjà annulé !
                .build();

        // Quand le service cherche le RDV en base, on lui renvoie notre faux RDV annulé
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(cancelledAppointment));

        // 2 & 3. Exécution et Vérification (Act & Assert)
        // On vérifie qu'une IllegalOperationException est bien levée si le pro essaie de confirmer
        assertThrows(IllegalOperationException.class, () -> {
            appointmentService.confirm(pro, 100L);
        });
    }

    @Test
    void book_ShouldThrowException_WhenTimeSlotIsInThePast() {
        // Arrange : On crée un créneau qui date d'hier
        ProfessionalProfile pro = ProfessionalProfile.builder().id(1L).validated(true).build();
        User user = User.builder().id(2L).build();

        TimeSlot pastSlot = TimeSlot.builder()
                .id(10L)
                .professional(pro)
                .available(true)
                .startDateTime(LocalDateTime.now().minusDays(1)) // HIER
                .build();

        AppointmentForm form = new AppointmentForm();
        form.setTimeSlotId(10L);
        form.setPrestationId(20L);

        when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(pastSlot));

        // Act & Assert : On vérifie que ça plante avec le bon message
        IllegalOperationException exception = assertThrows(IllegalOperationException.class, () -> {
            appointmentService.book(user, pro, form);
        });

        // On vérifie que c'est bien notre nouvelle règle qui a bloqué
        assert(exception.getMessage().contains("dans le passé"));
    }

    @Test
    void book_ShouldThrowException_WhenProfessionalIsNotValidated() {
        // Arrange : Un pro NON validé
        ProfessionalProfile pro = ProfessionalProfile.builder().id(1L).validated(false).build();
        User user = User.builder().id(2L).build();

        TimeSlot futureSlot = TimeSlot.builder()
                .id(10L)
                .professional(pro)
                .available(true)
                .startDateTime(LocalDateTime.now().plusDays(1)) // DEMAIN
                .build();

        AppointmentForm form = new AppointmentForm();
        form.setTimeSlotId(10L);

        when(timeSlotRepository.findById(10L)).thenReturn(Optional.of(futureSlot));

        // Act & Assert
        IllegalOperationException exception = assertThrows(IllegalOperationException.class, () -> {
            appointmentService.book(user, pro, form);
        });

        assert(exception.getMessage().contains("pas disponible"));
    }
}