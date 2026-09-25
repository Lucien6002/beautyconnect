package com.beautyconnect.service;

import com.beautyconnect.model.Appointment;
import com.beautyconnect.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class EmailService {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'a' HH:mm");

    private final JavaMailSender mailSender;
    private final String from;
    private final boolean failSilently;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.mail.from}") String from,
                        @Value("${app.mail.fail-silently:true}") boolean failSilently) {
        this.mailSender = mailSender;
        this.from = from;
        this.failSilently = failSilently;
    }

    public void sendWelcomeEmail(User user) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Bienvenue sur BeautyConnect !");
        message.setText("Bonjour " + user.getFirstName() + ",\n\n"
                + "Votre compte a bien été créé. Vous pouvez dès maintenant vous connecter et profiter de nos services.\n\n"
                + "L'équipe BeautyConnect");
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Echec envoi mail bienvenue : {}", e.getMessage());
        }
    }

    public void sendAppointmentConfirmation(Appointment appointment) {
        String to = appointment.getClient().getEmail();
        String subject = "Confirmation de votre rendez-vous BeautyConnect";
        String body = """
                Bonjour %s,

                Votre rendez-vous a ete confirme par %s.

                Prestation : %s
                Date : %s
                Lieu : %s, %s

                A bientot sur BeautyConnect !
                """.formatted(
                appointment.getClient().getFirstName(),
                appointment.getProfessional().getBusinessName(),
                appointment.getPrestation().getName(),
                appointment.getTimeSlot().getStartDateTime().format(FORMAT),
                appointment.getProfessional().getAddress() != null ? appointment.getProfessional().getAddress() : "",
                appointment.getProfessional().getCity()
        );

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Mail de confirmation envoye a {}", to);
        } catch (Exception ex) {
            if (!failSilently) {
                throw ex;
            }
            log.warn("Echec de l'envoi du mail de confirmation a {} : {}", to, ex.getMessage());
        }
    }
}