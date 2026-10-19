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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controle d'acces cote serveur de l'espace administrateur.
 *
 * Masquer les liens admin dans la navbar (sec:authorize) ne protege rien :
 * un client peut taper l'URL ou envoyer le formulaire a la main. Seule la
 * regle "/admin/**" -> hasRole("ADMIN") de SecurityConfig bloque l'acces.
 * Ces tests verrouillent cette regle : si elle est supprimee ou mal
 * ordonnee, ils echouent.
 *
 * Pour chaque action POST refusee, on verifie aussi que rien n'a change en
 * base (un 403 renvoye APRES l'execution de l'action serait une faille).
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminAccessControlTests {

    private static final String EMAIL_SUFFIX = "@access.test.local";
    private static final String TEST_MARKER = "[acces]";

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

    // Aucun envoi SMTP, et on peut verifier qu'aucun mail de validation ne part.
    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    // Donnees ciblees par les routes admin.
    private ProfessionalProfile pendingPro;
    private ProfessionalProfile validatedPro;
    private User disabledClient;
    private Review review;
    private Report report;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        pendingPro = createProfessional("pro-attente" + EMAIL_SUFFIX, false);
        validatedPro = createProfessional("pro-valide" + EMAIL_SUFFIX, true);
        User client = createClient("client" + EMAIL_SUFFIX, true);
        disabledClient = createClient("client-desactive" + EMAIL_SUFFIX, false);
        review = reviewRepository.save(Review.builder()
                .client(client)
                .professional(validatedPro)
                .rating(5)
                .comment(TEST_MARKER + " Avis de test")
                .build());
        report = reportRepository.save(Report.builder()
                .reporter(client)
                .targetType(ReportTargetType.AVIS)
                .targetId(review.getId())
                .reason(TEST_MARKER + " Signalement de test")
                .status(ReportStatus.EN_ATTENTE)
                .build());
    }

    // Avis et signalements d'abord (cles etrangeres vers les comptes), puis
    // les comptes de test. Les donnees du DataInitializer restent intactes.
    @AfterEach
    void cleanUp() {
        reportRepository.findAll().stream()
                .filter(r -> r.getReason().startsWith(TEST_MARKER))
                .forEach(reportRepository::delete);
        reviewRepository.findAll().stream()
                .filter(r -> r.getComment() != null && r.getComment().startsWith(TEST_MARKER))
                .forEach(reviewRepository::delete);
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().endsWith(EMAIL_SUFFIX))
                .forEach(u -> {
                    profileRepository.findByUserId(u.getId()).ifPresent(profileRepository::delete);
                    userRepository.delete(u);
                });
    }

    // ---- Roles non admin : CLIENT et PROFESSIONAL ----

    @ParameterizedTest(name = "role {0} : pages admin interdites")
    @ValueSource(strings = {"CLIENT", "PROFESSIONAL"})
    void nonAdmin_cannotOpenAdminPages(String role) throws Exception {
        for (String url : adminPages()) {
            mockMvc.perform(get(url).with(asRole(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @ParameterizedTest(name = "role {0} : actions admin interdites et sans effet")
    @ValueSource(strings = {"CLIENT", "PROFESSIONAL"})
    void nonAdmin_cannotRunAdminActions_andNothingChanges(String role) throws Exception {
        for (String url : adminActions()) {
            mockMvc.perform(post(url).with(asRole(role)).with(csrf()))
                    .andExpect(status().isForbidden());
        }
        assertNothingChanged();
    }

    // ---- Visiteur non connecte ----

    @Test
    void anonymous_isRedirectedToLogin_onAdminPages() throws Exception {
        for (String url : adminPages()) {
            mockMvc.perform(get(url))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/connexion"));
        }
    }

    @Test
    void anonymous_isRedirectedToLogin_onAdminActions_andNothingChanges() throws Exception {
        for (String url : adminActions()) {
            mockMvc.perform(post(url).with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/connexion"));
        }
        assertNothingChanged();
    }

    // Sans jeton CSRF, meme un admin est refuse : protege contre un site
    // tiers qui ferait poster le navigateur d'un admin connecte.
    @Test
    void admin_withoutCsrfToken_isRefused() throws Exception {
        for (String url : adminActions()) {
            mockMvc.perform(post(url).with(asRole("ADMIN")))
                    .andExpect(status().isForbidden());
        }
        assertNothingChanged();
    }

    // ---- Controle positif : l'admin passe bien ----

    // Garantit que les tests ci-dessus ne passent pas simplement parce que
    // tout serait bloque, y compris pour l'administrateur.
    @Test
    void admin_canOpenAllAdminPages() throws Exception {
        for (String url : adminPages()) {
            mockMvc.perform(get(url).with(asRole("ADMIN")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void admin_canHideReview() throws Exception {
        mockMvc.perform(post("/admin/avis/{id}/masquer", review.getId()).with(asRole("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(reviewRepository.findById(review.getId()).orElseThrow().isHidden()).isTrue();
    }

    // ---- Outils ----

    private List<String> adminPages() {
        return List.of(
                "/admin/tableau-bord",
                "/admin/professionnels",
                "/admin/professionnels?statut=en-attente",
                "/admin/professionnels/" + pendingPro.getId(),
                "/admin/avis",
                "/admin/signalements");
    }

    private List<String> adminActions() {
        return List.of(
                "/admin/professionnels/" + pendingPro.getId() + "/valider",
                "/admin/professionnels/" + validatedPro.getId() + "/retirer-validation",
                "/admin/utilisateurs/" + validatedPro.getUser().getId() + "/desactiver",
                "/admin/utilisateurs/" + disabledClient.getId() + "/activer",
                "/admin/avis/" + review.getId() + "/masquer",
                "/admin/signalements/" + report.getId() + "/traiter",
                "/admin/signalements/" + report.getId() + "/masquer-avis",
                "/admin/signalements/" + report.getId() + "/desactiver-profil",
                "/admin/signalements/" + report.getId() + "/rejeter");
    }

    private RequestPostProcessor asRole(String role) {
        return user(role.toLowerCase() + EMAIL_SUFFIX).roles(role);
    }

    // Chaque action admin refusee doit laisser la base exactement en l'etat.
    private void assertNothingChanged() {
        assertThat(profileRepository.findById(pendingPro.getId()).orElseThrow().isValidated()).isFalse();
        assertThat(profileRepository.findById(validatedPro.getId()).orElseThrow().isValidated()).isTrue();
        assertThat(userRepository.findById(validatedPro.getUser().getId()).orElseThrow().isEnabled()).isTrue();
        assertThat(userRepository.findById(disabledClient.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().isHidden()).isFalse();
        assertThat(reportRepository.findById(report.getId()).orElseThrow().getStatus()).isEqualTo(ReportStatus.EN_ATTENTE);
        verify(emailService, never()).sendProfessionalValidated(any());
    }

    private User createClient(String email, boolean enabled) {
        return userRepository.save(User.builder()
                .email(email)
                .password("{noop}secret")
                .firstName("Client")
                .lastName("Test")
                .role(Role.CLIENT)
                .enabled(enabled)
                .build());
    }

    private ProfessionalProfile createProfessional(String email, boolean validated) {
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
                .validated(validated)
                .build());
    }
}
