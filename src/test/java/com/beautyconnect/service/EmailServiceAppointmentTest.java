package com.beautyconnect.service;

import com.beautyconnect.model.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class EmailServiceAppointmentTest {

    private final JavaMailSender sender = mock(JavaMailSender.class);
    private final EmailService emailService = new EmailService(sender, "noreply@beautyconnect.test", false);

    private Appointment appointment() {
        User proUser = User.builder().id(1L).email("pro@test.fr").firstName("Paul").lastName("Pro").role(Role.PROFESSIONAL).build();
        ProfessionalProfile pro = ProfessionalProfile.builder().id(1L).user(proUser).businessName("Salon Paul").city("Paris").build();
        User client = User.builder().id(2L).email("client@test.fr").firstName("Lea").lastName("Martin").phone("0612345678").role(Role.CLIENT).build();
        Prestation prestation = Prestation.builder().name("Coupe").build();
        TimeSlot slot = TimeSlot.builder().startDateTime(LocalDateTime.of(2026, 11, 3, 14, 30)).build();
        return Appointment.builder().client(client).professional(pro).prestation(prestation).timeSlot(slot)
                .notes("Premiere visite").status(AppointmentStatus.CONFIRME).build();
    }

    @Test
    void professionalNotification_IsSentToTheProfessionalWithClientAndSlotDetails() {
        emailService.sendAppointmentNotificationToProfessional(appointment());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();
        assertThat(message.getTo()).containsExactly("pro@test.fr");
        assertThat(message.getText()).contains("Lea Martin", "0612345678", "Coupe", "03/11/2026 a 14:30", "Premiere visite");
    }

    @Test
    void clientConfirmation_IsSentToTheClient() {
        emailService.sendAppointmentConfirmation(appointment());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("client@test.fr");
    }
}
