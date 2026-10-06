package com.beautyconnect.service;

import com.beautyconnect.model.Appointment;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Envoi des mails transactionnels : confirmation de rendez-vous (client)
 * et notification de validation du profil (professionnel).
 * En environnement de developpement (sans serveur SMTP disponible), l'echec
 * d'envoi est journalise plutot que de faire planter la demande metier
 * (voir app.mail.fail-silently).
 *
 * @Slf4j (Lombok) : genere automatiquement un champ "log" (un Logger) pretant
 * a etre utilise directement, sans avoir a ecrire
 * "private static final Logger log = LoggerFactory.getLogger(EmailService.class);" a la main.
 */
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

        send(to, subject, body);
    }

    // Prevenir le professionnel que son profil est desormais visible
    // publiquement (appele par AdminService.validateProfessional). Le User
    // doit etre deja charge (profil obtenu via findByIdWithUser).
    public void sendProfessionalValidated(ProfessionalProfile profile) {
        String to = profile.getUser().getEmail();
        String subject = "Votre profil BeautyConnect a ete valide";
        String body = """
                Bonjour %s,

                Bonne nouvelle : votre profil professionnel "%s" a ete valide par
                notre equipe. Il apparait desormais dans les resultats de recherche
                et les clients peuvent reserver vos creneaux.

                Pensez a garder vos prestations et disponibilites a jour.

                A bientot sur BeautyConnect !
                """.formatted(profile.getUser().getFirstName(), profile.getBusinessName());

        send(to, subject, body);
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Mail \"{}\" envoye a {}", subject, to);
        } catch (Exception ex) {
            if (!failSilently) {
                throw ex;
            }
            log.warn("Echec de l'envoi du mail a {} (SMTP non configure ?) : {}", to, ex.getMessage());
        }
    }
}