package com.beautyconnect.service;

import com.beautyconnect.model.Appointment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

/**
 * Envoi du mail de confirmation de rendez-vous.
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

    // Constructeur explicite (au lieu de @RequiredArgsConstructor) car on a
    // besoin d'annotations @Value sur les parametres pour injecter des
    // valeurs issues de application.properties (app.mail.from, app.mail.fail-silently),
    // ce que Lombok ne sait pas generer automatiquement.
    public EmailService(JavaMailSender mailSender,
                         @Value("${app.mail.from}") String from,
                         @Value("${app.mail.fail-silently:true}") boolean failSilently) {
        this.mailSender = mailSender;
        this.from = from;
        this.failSilently = failSilently;
    }

    public void sendAppointmentConfirmation(Appointment appointment) {
        String to = appointment.getClient().getEmail();
        String subject = "Confirmation de votre rendez-vous BeautyConnect";
        // Text block Java (""" ... """) : permet d'ecrire un texte multi-lignes
        // sans concatener des chaines avec des "+" ni des "\n" partout.
        // .formatted(...) remplace ensuite les %s dans l'ordre.
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
            // Si failSilently est false (configuration explicite), on laisse
            // l'exception remonter normalement. Sinon (comportement par
            // defaut en dev), on logue juste un avertissement : la
            // confirmation du rendez-vous en base a deja reussi, ce n'est
            // pas parce que l'email echoue (pas de serveur SMTP local) que
            // toute l'operation doit etre annulee pour l'utilisateur.
            if (!failSilently) {
                throw ex;
            }
            log.warn("Echec de l'envoi du mail de confirmation a {} (SMTP non configure ?) : {}", to, ex.getMessage());
        }
    }
}
