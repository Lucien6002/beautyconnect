package com.beautyconnect.config;

import com.beautyconnect.model.Role;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Cree un compte administrateur par defaut au premier demarrage,
 * si aucun n'existe encore (identifiants configurables via
 * app.admin.email / app.admin.password, voir application.properties).
 *
 * CommandLineRunner : interface Spring Boot dont la methode run(...) est
 * appelee UNE SEULE FOIS, automatiquement, juste apres que l'application ait
 * fini de demarrer (tous les beans sont prets, y compris la connexion a la
 * base de donnees). Pratique pour ce genre de taches d'initialisation.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // @Value("${...}") : injecte directement la valeur correspondante lue
    // dans application.properties (ou dans les variables d'environnement
    // ADMIN_EMAIL / ADMIN_PASSWORD qui la surchargent, voir ce fichier de
    // configuration : app.admin.email=${ADMIN_EMAIL:admin@beautyconnect.local}).
    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    public void run(String... args) {
        // Ne cree un admin que s'il n'en existe encore aucun : evite de
        // dupliquer le compte a chaque redemarrage de l'application.
        if (userRepository.countByRole(Role.ADMIN) == 0) {
            User admin = User.builder()
                    .email(adminEmail.toLowerCase())
                    .password(passwordEncoder.encode(adminPassword)) // jamais en clair, meme pour ce compte genere automatiquement
                    .firstName("Admin")
                    .lastName("BeautyConnect")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .build();
            userRepository.save(admin);
            log.info("Compte administrateur initial cree : {}", adminEmail);
        }
    }
}
