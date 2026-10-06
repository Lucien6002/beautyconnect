package com.beautyconnect;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportStatus;
import com.beautyconnect.model.ReportTargetType;
import com.beautyconnect.model.Review;
import com.beautyconnect.model.Role;
import com.beautyconnect.model.TargetGender;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.ReportRepository;
import com.beautyconnect.repository.ReviewRepository;
import com.beautyconnect.repository.UserRepository;
import com.beautyconnect.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pages de moderation admin (avis et signalements) rendues avec
 * spring.jpa.open-in-view=false.
 *
 * Pas de @Transactional sur la classe : une transaction de test garderait la
 * session Hibernate ouverte pendant le rendu Thymeleaf et masquerait les
 * LazyInitializationException sur Review.client, Review.professional et
 * Report.reporter (relations LAZY). Les donnees sont nettoyees a la main.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminModerationPagesTests {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfessionalProfileRepository profileRepository;
    @Autowired
    private ReviewRepository reviewRepository;
    @Autowired
    private ReportRepository reportRepository;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // Supprime d'abord les avis et signalements (cles etrangeres vers les
    // comptes), puis les comptes de test en @moderation.test.local.
    @AfterEach
    void cleanUp() {
        reportRepository.findAll().stream()
                .filter(r -> r.getReason().startsWith("[test]"))
                .forEach(reportRepository::delete);
        reviewRepository.findAll().stream()
                .filter(r -> r.getComment() != null && r.getComment().startsWith("[test]"))
                .forEach(reviewRepository::delete);
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().endsWith("@moderation.test.local"))
                .forEach(u -> {
                    profileRepository.findByUserId(u.getId()).ifPresent(profileRepository::delete);
                    userRepository.delete(u);
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reviewsPage_showsClientAndProfessionalNames() throws Exception {
        User client = createClient("client-avis@moderation.test.local", "Camille");
        ProfessionalProfile pro = createProfessional("pro-avis@moderation.test.local");
        reviewRepository.save(Review.builder()
                .client(client)
                .professional(pro)
                .rating(4)
                .comment("[test] Tres bonne coupe")
                .build());

        mockMvc.perform(get("/admin/avis"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Salon pro-avis@moderation.test.local")))
                .andExpect(content().string(containsString("Camille")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reportsPage_showsReporterName() throws Exception {
        User reporter = createClient("client-signal@moderation.test.local", "Dominique");
        reportRepository.save(Report.builder()
                .reporter(reporter)
                .targetType(ReportTargetType.PROFIL_PROFESSIONNEL)
                .targetId(1L)
                .reason("[test] Faux profil")
                .status(ReportStatus.EN_ATTENTE)
                .build());

        mockMvc.perform(get("/admin/signalements"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Dominique")))
                .andExpect(content().string(containsString("[test] Faux profil")));
    }

    private User createClient(String email, String firstName) {
        return userRepository.save(User.builder()
                .email(email)
                .password("{noop}secret")
                .firstName(firstName)
                .lastName("Test")
                .role(Role.CLIENT)
                .build());
    }

    private ProfessionalProfile createProfessional(String email) {
        User user = userRepository.save(User.builder()
                .email(email)
                .password("{noop}secret")
                .firstName("Prenom")
                .lastName("Nom")
                .role(Role.PROFESSIONAL)
                .build());
        return profileRepository.save(ProfessionalProfile.builder()
                .user(user)
                .businessName("Salon " + email)
                .city("Paris")
                .targetGender(TargetGender.MIXTE)
                .validated(true)
                .build());
    }
}
