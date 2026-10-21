package com.beautyconnect.service;

import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.*;
import com.beautyconnect.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.LocalDateTime;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
public class PasswordResetService {
    public static final String INVALID_LINK = "Ce lien est invalide ou expiré. Demandez un nouveau lien.";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final JavaMailSender sender;
    private final SessionRegistry sessions;
    @Value("${app.mail.from}") private String from;
    @Value("${app.base-url}") private String baseUrl;
    @Value("${app.security.password-reset-expiration-minutes:15}") private int expirationMinutes;

    @Transactional
    public void request(String email) {
        var user = users.findByEmailForUpdate(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        // Ne réactive jamais un compte suspendu ou qui attend sa vérification.
        if (!eligible(user)) return;
        var now = LocalDateTime.now();
        var previous = tokens.findByUser(user).orElse(null);
        if (previous != null && previous.getCreatedAt().isAfter(now.minusMinutes(1))) return;
        tokens.deleteByUser(user);
        tokens.flush();
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(PasswordResetToken.builder().user(user).tokenHash(hash(raw))
                .createdAt(now).expiresAt(now.plusMinutes(expirationMinutes)).build());
        var mail = new SimpleMailMessage();
        mail.setFrom(from); mail.setTo(user.getEmail());
        mail.setSubject("Réinitialisez votre mot de passe BeautyConnect");
        mail.setText("Bonjour,\n\nPour choisir un nouveau mot de passe, ouvrez ce lien :\n\n"
                + baseUrl.replaceAll("/+$", "") + "/reinitialiser-mot-de-passe?token=" + raw
                + "\n\nCe lien est valable " + expirationMinutes + " minutes et ne fonctionne qu’une fois."
                + "\nSi vous n’avez pas fait cette demande, ignorez ce message. Votre mot de passe reste inchangé.");
        afterCommit(() -> {
            try { sender.send(mail); }
            catch (RuntimeException ex) { log.warn("Envoi du mail de réinitialisation indisponible ; une nouvelle demande reste possible après une minute."); }
        });
    }

    @Transactional(readOnly = true)
    public boolean isValid(String raw) {
        if (!wellFormed(raw)) return false;
        return tokens.findByTokenHash(hash(raw))
                .filter(t -> t.getExpiresAt().isAfter(LocalDateTime.now()) && eligible(t.getUser())).isPresent();
    }

    @Transactional
    public void reset(String raw, String password) {
        if (!wellFormed(raw)) throw new IllegalOperationException(INVALID_LINK);
        if (password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new IllegalOperationException("Utilisez au moins 8 caractères et au maximum 72 octets.");
        String digest = hash(raw);
        var candidate = tokens.findByTokenHash(digest).orElseThrow(() -> new IllegalOperationException(INVALID_LINK));
        // Même ordre de verrous que la demande : compte puis jeton, pour sérialiser les remplacements.
        var user = users.findByIdForUpdate(candidate.getUser().getId()).orElseThrow(() -> new IllegalOperationException(INVALID_LINK));
        var token = tokens.findByHashForUpdate(digest).orElseThrow(() -> new IllegalOperationException(INVALID_LINK));
        if (!eligible(user) || !token.getExpiresAt().isAfter(LocalDateTime.now())) throw new IllegalOperationException(INVALID_LINK);
        user.setPassword(encoder.encode(password));
        tokens.delete(token);
        // Aucune connexion automatique ; les anciennes sessions deviennent inutilisables.
        String email = user.getEmail();
        afterCommit(() -> sessions.getAllPrincipals().stream()
                .filter(p -> p instanceof UserDetails d && d.getUsername().equals(email))
                .flatMap(p -> sessions.getAllSessions(p, false).stream()).forEach(s -> s.expireNow()));
    }

    private static boolean eligible(User user) { return user != null && user.isEnabled() && user.isEmailVerified(); }
    private static boolean wellFormed(String raw) { return raw != null && raw.matches("[A-Za-z0-9_-]{43}"); }
    private static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 indisponible", ex); }
    }
    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { action.run(); }
        });
    }
}
