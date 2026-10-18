package com.beautyconnect.config;

import com.beautyconnect.model.*;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.TimeSlotRepository;
import com.beautyconnect.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@org.springframework.context.annotation.Profile("dev & !prod")
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final ProfessionalProfileRepository professionalProfileRepository;
    private final PrestationRepository prestationRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Override
    @Transactional
    public void run(String... args) {
        // 1. Compte Administrateur (Thomas)
        if (userRepository.countByRole(Role.ADMIN) == 0) {
            User admin = User.builder()
                    .email(adminEmail.toLowerCase())
                    .password(passwordEncoder.encode(adminPassword))
                    .firstName("Thomas")
                    .lastName("Admin")
                    .role(Role.ADMIN)
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            userRepository.save(admin);
            log.info("Admin créé : {}", adminEmail);
        }

        // 2. Compte Professionnel (Sarah) avec prestations et créneaux
        if (!userRepository.existsByEmail("sarah@beautyconnect.local")) {
            User proUser = User.builder()
                    .email("sarah@beautyconnect.local")
                    .password(passwordEncoder.encode("Pro12345!"))
                    .firstName("Sarah")
                    .lastName("Coiffure")
                    .phone("0601020304")
                    .role(Role.PROFESSIONAL)
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            userRepository.save(proUser);

            ProfessionalProfile profile = ProfessionalProfile.builder()
                    .user(proUser)
                    .businessName("Sarah Coiffure & Ongles")
                    .bio("Passionnée de coiffure et nail art depuis 3 ans. Spécialiste des soins naturels et coupes modernes pour étudiantes.")
                    .city("Paris")
                    .address("12 rue des Fleurs")
                    .targetGender(TargetGender.FEMME)
                    .coordinatesPublic(true)
                    .latitude(48.8566)
                    .longitude(2.3522)
                    .validated(true)
                    .build();
            professionalProfileRepository.save(profile);

            prestationRepository.save(Prestation.builder()
                    .professional(profile)
                    .name("Coupe & Brushing")
                    .description("Shampoing, coupe sur mesure et brushing soigné")
                    .type(ServiceType.COIFFURE)
                    .price(new BigDecimal("30.00"))
                    .durationMinutes(45)
                    .active(true)
                    .build());

            prestationRepository.save(Prestation.builder()
                    .professional(profile)
                    .name("Pose Vernis Semi-Permanent")
                    .description("Manucure complète et pose de vernis longue durée")
                    .type(ServiceType.ONGLERIE)
                    .price(new BigDecimal("25.00"))
                    .durationMinutes(40)
                    .active(true)
                    .build());

            LocalDateTime demain = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0);
            timeSlotRepository.save(TimeSlot.builder()
                    .professional(profile)
                    .startDateTime(demain)
                    .available(true)
                    .build());

            timeSlotRepository.save(TimeSlot.builder()
                    .professional(profile)
                    .startDateTime(demain.plusHours(4))
                    .available(true)
                    .build());

            log.info("Professionnel Sarah créé !");
        } else {
            // CORRECTION SANS PIÈGE LAZY :
            // On cherche Sarah directement par son email, puis son profil par son ID
            userRepository.findByEmail("sarah@beautyconnect.local").ifPresent(sarahUser -> {
                professionalProfileRepository.findByUserId(sarahUser.getId()).ifPresent(p -> {
                    if (p.getLatitude() == null) {
                        p.setLatitude(48.8566);
                        p.setLongitude(2.3522);
                        p.setCoordinatesPublic(true);
                        professionalProfileRepository.save(p);
                        log.info("Coordonnées GPS de Sarah mises à jour avec succès !");
                    }
                });
            });
        }

        // 3. Compte Professionnel 2 (Chloé) à Lyon
        if (!userRepository.existsByEmail("chloe@beautyconnect.local")) {
            User chloeUser = User.builder()
                    .email("chloe@beautyconnect.local")
                    .password(passwordEncoder.encode("Pro12345!"))
                    .firstName("Chloé")
                    .lastName("Styliste")
                    .phone("0602030405")
                    .role(Role.PROFESSIONAL)
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            userRepository.save(chloeUser);

            ProfessionalProfile chloeProfile = ProfessionalProfile.builder()
                    .user(chloeUser)
                    .businessName("Chloé Coiffure Lyon")
                    .bio("Spécialiste soins capillaires et coloration.")
                    .city("Lyon")
                    .address("5 rue de la République")
                    .targetGender(TargetGender.MIXTE)
                    .coordinatesPublic(true)
                    .latitude(45.7640)
                    .longitude(4.8357)
                    .validated(true)
                    .build();
            professionalProfileRepository.save(chloeProfile);

            log.info("Professionnel Chloé créé à Lyon !");
        }

        // 4. Compte Cliente (Léa)
        if (!userRepository.existsByEmail("lea@beautyconnect.local")) {
            User client = User.builder()
                    .email("lea@beautyconnect.local")
                    .password(passwordEncoder.encode("Client123!"))
                    .firstName("Léa")
                    .lastName("Etudiante")
                    .phone("0611223344")
                    .role(Role.CLIENT)
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            userRepository.save(client);
            log.info("Cliente Léa créée !");
        }
    }
}