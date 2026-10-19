package com.beautyconnect;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Report;
import com.beautyconnect.model.ReportDecision;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Decisions de moderation sur un signalement (etape 5 de la roadmap admin).
 *
 * L'admin voit la cible reelle (extrait de l'avis, nom du profil) et prend
 * une decision explicite :
 *  - masquer l'avis signale, puis clore le signalement ;
 *  - desactiver le compte du profil signale, puis clore ;
 *  - clore sans action (probleme regle autrement) ;
 *  - rejeter (signalement infonde).
 * Une decision qui agit sur la cible clot aussi les autres signalements en
 * attente sur cette meme cible. Rien n'est jamais masque automatiquement.
 * Chaque signalement clos garde la decision, l'admin et la date (journal).
 *
 * Pas de @Transactional sur la classe : la page admin est rendue avec
 * open-in-view=false, comme en production.
 */
@SpringBootTest
@ActiveProfiles("test")
class ModerationDecisionTests {

    private static final String EMAIL_SUFFIX = "@moderation-decision.test.local";

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
    private User adminUser;
    private User reporter;
    private User otherReporter;
    private ProfessionalProfile pro;
    private Review review;
    private Review otherReview;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        adminUser = createUser("admin", Role.ADMIN);
        reporter = createUser("signaleur", Role.CLIENT);
        otherReporter = createUser("autre-signaleur", Role.CLIENT);
        User author = createUser("auteur-avis", Role.CLIENT);
        pro = createProfessional("pro");
        review = reviewRepository.save(Review.builder().client(author).professional(pro)
                .rating(1).comment("Salon nul, personnel incompetent").build());
        otherReview = reviewRepository.save(Review.builder().client(author).professional(pro)
                .rating(5).comment("Autre avis sans probleme").build());
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

    // ---- Affichage de la cible ----

    @Test
    void reportsPage_showsReviewExcerptAndProfessional() throws Exception {
        report(reporter, ReportTargetType.AVIS, review.getId(), "Insultes");

        mockMvc.perform(get("/admin/signalements").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Salon nul, personnel incompetent")))
                .andExpect(content().string(containsString("Salon pro")))
                .andExpect(content().string(containsString("Insultes")))
                .andExpect(content().string(containsString("Masquer l'avis")));
    }

    @Test
    void reportsPage_showsProfileNameAndDisableAction() throws Exception {
        report(reporter, ReportTargetType.PROFIL_PROFESSIONNEL, pro.getId(), "Faux salon");

        mockMvc.perform(get("/admin/signalements").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Salon pro")))
                .andExpect(content().string(containsString("Desactiver le compte")));
    }

    // Une cible supprimee depuis le signalement ne doit pas faire planter la page.
    @Test
    void reportsPage_handlesMissingTarget() throws Exception {
        report(reporter, ReportTargetType.AVIS, 999_999L, "Avis disparu");

        mockMvc.perform(get("/admin/signalements").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cible introuvable")));
    }

    // ---- Decisions ----

    @Test
    void hideReview_hidesIt_andClosesAllPendingReportsOnThatReview() throws Exception {
        Report first = report(reporter, ReportTargetType.AVIS, review.getId(), "Insultes");
        Report second = report(otherReporter, ReportTargetType.AVIS, review.getId(), "Je confirme");
        Report unrelated = report(reporter, ReportTargetType.AVIS, otherReview.getId(), "Autre chose");

        decide(first, "masquer-avis").andExpect(flash().attributeExists("success"));

        assertThat(reviewRepository.findById(review.getId()).orElseThrow().isHidden()).isTrue();
        assertThat(statusOf(first)).isEqualTo(ReportStatus.TRAITE);
        assertThat(statusOf(second)).isEqualTo(ReportStatus.TRAITE);
        assertThat(statusOf(unrelated)).isEqualTo(ReportStatus.EN_ATTENTE);
        assertThat(reviewRepository.findById(otherReview.getId()).orElseThrow().isHidden()).isFalse();
    }

    @Test
    void disableProfile_disablesAccount_andClosesReport() throws Exception {
        Report report = report(reporter, ReportTargetType.PROFIL_PROFESSIONNEL, pro.getId(), "Faux salon");

        decide(report, "desactiver-profil").andExpect(flash().attributeExists("success"));

        assertThat(userRepository.findById(pro.getUser().getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(statusOf(report)).isEqualTo(ReportStatus.TRAITE);
    }

    @Test
    void treatWithoutAction_closesOnlyThisReport_andLeavesTargetVisible() throws Exception {
        Report report = report(reporter, ReportTargetType.AVIS, review.getId(), "Insultes");
        Report other = report(otherReporter, ReportTargetType.AVIS, review.getId(), "Je confirme");

        decide(report, "traiter").andExpect(flash().attributeExists("success"));

        assertThat(statusOf(report)).isEqualTo(ReportStatus.TRAITE);
        assertThat(statusOf(other)).isEqualTo(ReportStatus.EN_ATTENTE);
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().isHidden()).isFalse();
    }

    @Test
    void reject_closesReport_andLeavesTargetVisible() throws Exception {
        Report report = report(reporter, ReportTargetType.AVIS, review.getId(), "Pas d'accord");

        decide(report, "rejeter").andExpect(flash().attributeExists("success"));

        assertThat(statusOf(report)).isEqualTo(ReportStatus.REJETE);
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().isHidden()).isFalse();
    }

    // ---- Decisions refusees ----

    @Test
    void decisionOnAlreadyHandledReport_isRefused() throws Exception {
        Report report = report(reporter, ReportTargetType.AVIS, review.getId(), "Insultes");
        decide(report, "rejeter");

        decide(report, "masquer-avis").andExpect(flash().attributeExists("error"));

        assertThat(statusOf(report)).isEqualTo(ReportStatus.REJETE);
        assertThat(reviewRepository.findById(review.getId()).orElseThrow().isHidden()).isFalse();
    }

    @Test
    void hideReview_onProfileReport_isRefused() throws Exception {
        Report report = report(reporter, ReportTargetType.PROFIL_PROFESSIONNEL, pro.getId(), "Faux salon");

        decide(report, "masquer-avis").andExpect(flash().attributeExists("error"));

        assertThat(statusOf(report)).isEqualTo(ReportStatus.EN_ATTENTE);
    }

    @Test
    void disableProfile_onReviewReport_isRefused() throws Exception {
        Report report = report(reporter, ReportTargetType.AVIS, review.getId(), "Insultes");

        decide(report, "desactiver-profil").andExpect(flash().attributeExists("error"));

        assertThat(statusOf(report)).isEqualTo(ReportStatus.EN_ATTENTE);
        assertThat(userRepository.findById(pro.getUser().getId()).orElseThrow().isEnabled()).isTrue();
    }

    // Cible disparue : l'action echoue et le signalement reste en attente
    // (rien n'est enregistre a moitie). L'admin peut encore le rejeter.
    @Test
    void hideMissingReview_isRefused_andReportStaysPending() throws Exception {
        Report report = report(reporter, ReportTargetType.AVIS, 999_999L, "Avis disparu");

        decide(report, "masquer-avis").andExpect(flash().attributeExists("error"));
        assertThat(statusOf(report)).isEqualTo(ReportStatus.EN_ATTENTE);

        decide(report, "rejeter").andExpect(flash().attributeExists("success"));
        assertThat(statusOf(report)).isEqualTo(ReportStatus.REJETE);
    }

    @Test
    void unknownReport_returns404() throws Exception {
        mockMvc.perform(post("/admin/signalements/{id}/traiter", 999_999L).with(admin()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ---- Journal de moderation ----

    @Test
    void decision_isLoggedWithAdminAndDate_onEveryClosedReport() throws Exception {
        Report first = report(reporter, ReportTargetType.AVIS, review.getId(), "Insultes");
        Report second = report(otherReporter, ReportTargetType.AVIS, review.getId(), "Je confirme");

        decide(first, "masquer-avis");

        for (Report closed : List.of(first, second)) {
            Report saved = reportRepository.findAllByOrderByCreatedAtDesc().stream()
                    .filter(r -> r.getId().equals(closed.getId())).findFirst().orElseThrow();
            assertThat(saved.getDecision()).isEqualTo(ReportDecision.MASQUER_AVIS);
            assertThat(saved.getHandledBy().getId()).isEqualTo(adminUser.getId());
            assertThat(saved.getHandledAt()).isNotNull();
        }
    }

    @Test
    void rejection_isLoggedAsRejection() throws Exception {
        Report report = report(reporter, ReportTargetType.AVIS, review.getId(), "Pas d'accord");

        decide(report, "rejeter");

        Report saved = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(saved.getDecision()).isEqualTo(ReportDecision.REJETER);
        assertThat(saved.getHandledAt()).isNotNull();
    }

    @Test
    void pendingReport_hasNoLogEntry() {
        Report report = report(reporter, ReportTargetType.AVIS, review.getId(), "Insultes");

        Report saved = reportRepository.findById(report.getId()).orElseThrow();
        assertThat(saved.getDecision()).isNull();
        assertThat(saved.getHandledAt()).isNull();
    }

    @Test
    void reportsPage_showsWhoDecidedWhat() throws Exception {
        Report report = report(reporter, ReportTargetType.AVIS, review.getId(), "Insultes");
        decide(report, "masquer-avis");

        mockMvc.perform(get("/admin/signalements").with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Avis masque")))
                .andExpect(content().string(containsString("par admin Test")));
    }

    // ---- Outils ----

    private ResultActions decide(Report report, String action) throws Exception {
        return mockMvc.perform(post("/admin/signalements/{id}/" + action, report.getId()).with(admin()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/signalements"));
    }

    // Vrai compte admin (CustomUserDetails) : le controleur l'enregistre
    // comme auteur de la decision dans le journal de moderation.
    private RequestPostProcessor admin() {
        return user(new CustomUserDetails(adminUser));
    }

    private ReportStatus statusOf(Report report) {
        return reportRepository.findById(report.getId()).orElseThrow().getStatus();
    }

    // Insertion directe (sans passer par ReportService) : permet de creer
    // aussi un signalement dont la cible a disparu depuis.
    private Report report(User author, ReportTargetType type, Long targetId, String reason) {
        return reportRepository.save(Report.builder()
                .reporter(author)
                .targetType(type)
                .targetId(targetId)
                .reason(reason)
                .status(ReportStatus.EN_ATTENTE)
                .build());
    }

    private User createUser(String name, Role role) {
        return userRepository.save(User.builder()
                .email(name + EMAIL_SUFFIX)
                .password("{noop}secret")
                .firstName(name)
                .lastName("Test")
                .role(role)
                .enabled(true)
                .emailVerified(true)
                .build());
    }

    private ProfessionalProfile createProfessional(String name) {
        return profileRepository.save(ProfessionalProfile.builder()
                .user(createUser(name, Role.PROFESSIONAL))
                .businessName("Salon " + name)
                .city("Paris")
                .targetGender(TargetGender.MIXTE)
                .validated(true)
                .build());
    }
}
