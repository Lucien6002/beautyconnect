package com.beautyconnect.config;

import com.beautyconnect.model.*;
import com.beautyconnect.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("prod")
public class ProductionAdminInitializer implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;

    public ProductionAdminInitializer(UserRepository users, PasswordEncoder encoder,
                                      @Value("${app.admin.email}") String email,
                                      @Value("${app.admin.password}") String password) {
        this.users = users; this.encoder = encoder; this.email = email; this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (users.countByRole(Role.ADMIN) > 0) return;
        if (email.isBlank() || !email.contains("@") || password.length() < 12) {
            throw new IllegalStateException("Premier démarrage production : fournir ADMIN_EMAIL et ADMIN_PASSWORD (12 caractères minimum).");
        }
        if (users.existsByEmail(email.trim().toLowerCase(java.util.Locale.ROOT))) {
            throw new IllegalStateException("L'adresse administrateur est déjà utilisée par un autre compte.");
        }
        users.save(User.builder().email(email.trim().toLowerCase(java.util.Locale.ROOT))
                .password(encoder.encode(password)).firstName("Administration").lastName("BeautyConnect")
                .role(Role.ADMIN).enabled(true).emailVerified(true).build());
    }
}
