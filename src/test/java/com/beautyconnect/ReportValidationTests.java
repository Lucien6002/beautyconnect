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
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regles de creation d'un signalement (etape 4 de la roadmap admin) :
 *  - la cible doit exister ET etre visible publiquement : avis non masque
 *    d'un professionnel public, ou profil public (compte actif et valide) ;
 *  - un seul signalement EN_ATTENTE par auteur et par cible (anti-spam) ;
 *    un nouveau signalement redevient possible une fois le premier traite ;
 *  - motif obligatoire, 1000 caracteres maximum ;
 *  - motif, auteur et date sont conserves.
 *
 * Pourquoi verifier la cible ? Report.targetId est un simple Long, sans cle
 * etrangere (il designe soit un avis, soit un profil selon targetType) : la
 * base accepterait n'importe quel nombre. Seul le service peut garantir que
 * le signalement pointe vers quelque chose de reel.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReportValidationTests {

    private static final String EMAIL_SUFFIX = "@report.test.local";

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
    private User reporter;
    private User otherReporter;
    private ProfessionalProfile pro;
    private Review review;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        reporter = createClient("signaleur");
        otherReporter = createClient("autre-signaleur");
        pro = createProfessional("pro", true, true);
        review = createReview(otherReporter, pro, false);
    }

    @AfterEach
    void cleanUp() {
        List<User> testUsers = userRepository.findAll().stream()
                .filter(u -> u.getEmail().endsWith(EMAIL_SUFFIX)).toList();
        List<Long> ids = testUsers.stream().map(User::getId).toList();
        reportRepository.findAll().stream()
                .filter(r -> ids.contains(r.getReporter().getId()))
                .forEach(reportRepository::delete);
        reviewRepository.findAll().stream()
                .filter(r -> ids.contains(r.getClient().getId()))
                .forEach(reviewRepository::delete);
        testUsers.forEach(u -> profileRepository.findByUserId(u.getId()).ifPresent(profileRepository::delete));
        userRepository.deleteAll(testUsers);
    }

    // ---- Signalement d'un avis ----

    @Test
    void reportVisibleReview_isSaved_withReasonAuthorAndDate() throws Exception {
        reportReview(reporter, review.getId(), "Propos insultants")
                .andExpect(redirectedUrl("/professionnels/" + pro.getId()))
                .andExpect(flash().attributeExists("success"));

        List<Report> reports = reportsOf(reporter);
        assertThat(reports).hasSize(1);
        Report saved = reports.get(0);
        assertThat(saved.getTargetType()).isEqualTo(ReportTargetType.AVIS);
        assertThat(saved.getTargetId()).isEqualTo(review.getId());
        assertThat(saved.getReason()).isEqualTo("Propos insultants");
        assertThat(saved.getReporter().getId()).isEqualTo(reporter.getId());
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getStatus()).isEqualTo(ReportStatus.EN_ATTENTE);
    }

    @Test
    void reportUnknownReview_isRefused() throws Exception {
        reportReview(reporter, 999_999L, "Avis invente")
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).isEmpty();
    }

    @Test
    void reportHiddenReview_isRefused() throws Exception {
        Review hidden = createReview(otherReporter, createProfessional("pro-masque", true, true), true);

        reportReview(reporter, hidden.getId(), "Deja masque")
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).isEmpty();
    }

    // Un avis d'un professionnel desactive n'est plus visible nulle part.
    @Test
    void reportReviewOfDisabledProfessional_isRefused() throws Exception {
        Review ofDisabled = createReview(otherReporter, createProfessional("pro-desactive", true, false), false);

        reportReview(reporter, ofDisabled.getId(), "Plus visible")
                .andExpect(redirectedUrl("/client/tableau-bord"))
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).isEmpty();
    }

    // ---- Signalement d'un profil ----

    @Test
    void reportPublicProfile_isSaved() throws Exception {
        reportProfile(reporter, pro.getId(), "Faux salon")
                .andExpect(redirectedUrl("/professionnels/" + pro.getId()))
                .andExpect(flash().attributeExists("success"));

        List<Report> reports = reportsOf(reporter);
        assertThat(reports).hasSize(1);
        assertThat(reports.get(0).getTargetType()).isEqualTo(ReportTargetType.PROFIL_PROFESSIONNEL);
        assertThat(reports.get(0).getTargetId()).isEqualTo(pro.getId());
    }

    @Test
    void reportUnknownProfile_isRefused() throws Exception {
        reportProfile(reporter, 999_999L, "Profil invente")
                .andExpect(redirectedUrl("/client/tableau-bord"))
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).isEmpty();
    }

    @Test
    void reportNotValidatedProfile_isRefused() throws Exception {
        ProfessionalProfile pending = createProfessional("pro-attente", false, true);

        reportProfile(reporter, pending.getId(), "Pas encore public")
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).isEmpty();
    }

    @Test
    void reportDisabledProfile_isRefused() throws Exception {
        ProfessionalProfile disabled = createProfessional("pro-off", true, false);

        reportProfile(reporter, disabled.getId(), "Compte desactive")
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).isEmpty();
    }

    // ---- Anti-spam ----

    @Test
    void secondPendingReport_onSameTarget_isRefused() throws Exception {
        reportReview(reporter, review.getId(), "Premier signalement");

        reportReview(reporter, review.getId(), "Je signale encore")
                .andExpect(redirectedUrl("/professionnels/" + pro.getId()))
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).hasSize(1);
    }

    // Le profil et un de ses avis sont deux cibles distinctes : signaler
    // l'un ne bloque pas le signalement de l'autre.
    @Test
    void reportingProfile_doesNotBlockReportingOneOfItsReviews() throws Exception {
        reportProfile(reporter, pro.getId(), "Profil douteux");

        reportReview(reporter, review.getId(), "Avis douteux")
                .andExpect(flash().attributeExists("success"));

        assertThat(reportsOf(reporter)).hasSize(2);
    }

    @Test
    void anotherReporter_canReportSameTarget() throws Exception {
        User third = createClient("troisieme");
        reportProfile(reporter, pro.getId(), "Faux salon");

        reportProfile(third, pro.getId(), "Je confirme")
                .andExpect(flash().attributeExists("success"));

        assertThat(reportsOf(reporter)).hasSize(1);
        assertThat(reportsOf(third)).hasSize(1);
    }

    @Test
    void newReport_isAllowed_afterPreviousOneWasHandled() throws Exception {
        reportReview(reporter, review.getId(), "Premier signalement");
        Report first = reportsOf(reporter).get(0);
        first.setStatus(ReportStatus.REJETE);
        reportRepository.save(first);

        reportReview(reporter, review.getId(), "Le probleme continue")
                .andExpect(flash().attributeExists("success"));

        assertThat(reportsOf(reporter)).hasSize(2);
    }

    // ---- Validation du motif ----

    @Test
    void blankReason_isRefused() throws Exception {
        reportReview(reporter, review.getId(), "   ")
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).isEmpty();
    }

    @Test
    void reasonLongerThan1000Characters_isRefusedWithMessage() throws Exception {
        reportProfile(reporter, pro.getId(), "a".repeat(1001))
                .andExpect(flash().attributeExists("error"));

        assertThat(reportsOf(reporter)).isEmpty();
    }

    @Test
    void reasonOf1000Characters_isAccepted() throws Exception {
        reportProfile(reporter, pro.getId(), "a".repeat(1000))
                .andExpect(flash().attributeExists("success"));

        assertThat(reportsOf(reporter)).hasSize(1);
    }

    // ---- Outils ----

    private ResultActions reportReview(User author, Long reviewId, String reason) throws Exception {
        return mockMvc.perform(post("/signalements/avis/{id}", reviewId)
                        .with(user(new CustomUserDetails(author))).with(csrf())
                        .param("reason", reason))
                .andExpect(status().is3xxRedirection());
    }

    private ResultActions reportProfile(User author, Long professionalId, String reason) throws Exception {
        return mockMvc.perform(post("/signalements/profils/{id}", professionalId)
                        .with(user(new CustomUserDetails(author))).with(csrf())
                        .param("reason", reason))
                .andExpect(status().is3xxRedirection());
    }

    private List<Report> reportsOf(User author) {
        return reportRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getReporter().getId().equals(author.getId()))
                .toList();
    }

    private Review createReview(User author, ProfessionalProfile target, boolean hidden) {
        return reviewRepository.save(Review.builder()
                .client(author)
                .professional(target)
                .rating(2)
                .comment("Avis de test")
                .hidden(hidden)
                .build());
    }

    private User createClient(String name) {
        return userRepository.save(User.builder()
                .email(name + EMAIL_SUFFIX)
                .password("{noop}secret")
                .firstName(name)
                .lastName("Test")
                .role(Role.CLIENT)
                .enabled(true)
                .emailVerified(true)
                .build());
    }

    private ProfessionalProfile createProfessional(String name, boolean validated, boolean enabled) {
        User user = userRepository.save(User.builder()
                .email(name + EMAIL_SUFFIX)
                .password("{noop}secret")
                .firstName(name)
                .lastName("Test")
                .role(Role.PROFESSIONAL)
                .enabled(enabled)
                .emailVerified(true)
                .build());
        return profileRepository.save(ProfessionalProfile.builder()
                .user(user)
                .businessName("Salon " + name)
                .city("Paris")
                .targetGender(TargetGender.MIXTE)
                .validated(validated)
                .build());
    }
}
