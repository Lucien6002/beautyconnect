package com.beautyconnect.service;

import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.User;
import com.beautyconnect.model.VerificationToken;
import com.beautyconnect.repository.UserRepository;
import com.beautyconnect.repository.VerificationTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private final VerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:contact@beautyconnect.fr}")
    private String from;

    @Transactional
    public void generateAndSendActivationLink(User user) {
        // Supprime les anciens jetons s'il y en a
        tokenRepository.deleteByUser(user);

        // Génère un jeton unique (UUID)
        String token = UUID.randomUUID().toString();

        VerificationToken vt = VerificationToken.builder()
                .user(user)
                .token(token)
                .expiryDate(LocalDateTime.now().plusHours(24)) // Valable 24 heures
                .build();
        tokenRepository.save(vt);

        // Crée le lien cliquable
        String activationLink = "http://localhost:8080/activer-compte?token=" + token;

        // Prépare l'e-mail
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(user.getEmail());
        message.setSubject("Activez votre compte BeautyConnect");
        message.setText("Bonjour " + user.getFirstName() + ",\n\n"
                + "Merci de vous être inscrit(e) sur BeautyConnect !\n"
                + "Pour activer votre compte et vérifier votre adresse e-mail, veuillez cliquer sur le lien ci-dessous :\n\n"
                + activationLink + "\n\n"
                + "Ce lien est valable 24 heures.\n\n"
                + "L'équipe BeautyConnect");

        mailSender.send(message);
    }

    @Transactional
    public void activateAccount(String token) {
        VerificationToken vt = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalOperationException("Lien d'activation invalide ou expiré."));

        if (vt.isExpired()) {
            tokenRepository.delete(vt);
            throw new IllegalOperationException("Ce lien a expiré. Veuillez vous réinscrire.");
        }

        // Active l'utilisateur
        User user = vt.getUser();
        user.setEnabled(true);
        userRepository.save(user);

        // Supprime le jeton (il ne sert plus)
        tokenRepository.delete(vt);
    }
}