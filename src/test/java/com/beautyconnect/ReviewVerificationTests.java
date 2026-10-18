package com.beautyconnect;

import com.beautyconnect.model.Appointment;
import com.beautyconnect.model.AppointmentStatus;
import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Review;
import com.beautyconnect.model.Role;
import com.beautyconnect.model.ServiceType;
import com.beautyconnect.model.TargetGender;
import com.beautyconnect.model.TimeSlot;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.AppointmentRepository;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.ReviewRepository;
import com.beautyconnect.repository.TimeSlotRepository;
import com.beautyconnect.repository.UserRepository;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
 * Regles de depot d'un avis (etape 3 de la roadmap admin) :
 *  - avis libre (sans rendez-vous) autorise, mais un seul par client et par professionnel ;
 *  - avis lie a un rendez-vous = avis verifie, accepte seulement si le
 *    rendez-vous est TERMINE, appartient au client connecte et concerne ce
 *    professionnel ; un seul avis par rendez-vous ;
 *  - note de 1 a 5 et commentaire de 1000 caracteres maximum.
 *
 * Chaque refus doit renvoyer le client sur la fiche du professionnel avec un
 * message d'erreur, et ne rien enregistrer en base.
 */
@SpringBootTest
@ActiveProfiles("test")
class ReviewVerificationTests {

    private static final String EMAIL_SUFFIX = "@review.test.local";

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfessionalProfileRepository profileRepository;
    @Autowired
    private PrestationRepository prestationRepository;
    @Autowired
    private TimeSlotRepository timeSlotRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;
    @Autowired
    private ReviewRepository reviewRepository;

    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;
    private User client;
    private User otherClient;
    private ProfessionalProfile pro;
    private ProfessionalProfile otherPro;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        client = createClient("client");
        otherClient = createClient("autre-client");
        pro = createProfessional("pro");
        otherPro = createProfessional("autre-pro");
    }

    // Ordre impose par les cles etrangeres : avis -> rendez-vous -> creneaux
    // et prestations -> profils -> comptes. Seules les donnees de ce test
    // (emails en @review.test.local) sont supprimees.
    @AfterEach
    void cleanUp() {
        List<User> testUsers = userRepository.findAll().stream()
                .filter(u -> u.getEmail().endsWith(EMAIL_SUFFIX)).toList();
        List<Long> ids = testUsers.stream().map(User::getId).toList();
        reviewRepository.findAll().stream()
                .filter(r -> ids.contains(r.getClient().getId()))
                .forEach(reviewRepository::delete);
        appointmentRepository.findAll().stream()
                .filter(a -> ids.contains(a.getClient().getId()))
                .forEach(appointmentRepository::delete);
        testUsers.forEach(u -> profileRepository.findByUserId(u.getId()).ifPresent(profile -> {
            timeSlotRepository.findAll().stream()
                    .filter(s -> s.getProfessional().getId().equals(profile.getId()))
                    .forEach(timeSlotRepository::delete);
            prestationRepository.findAll().stream()
                    .filter(p -> p.getProfessional().getId().equals(profile.getId()))
                    .forEach(prestationRepository::delete);
            profileRepository.delete(profile);
        }));
        userRepository.deleteAll(testUsers);
    }

    // ---- Avis libre (sans rendez-vous) ----

    @Test
    void freeReview_isAccepted_andNotVerified() throws Exception {
        submit(client, pro, 4, "Tres bon accueil", null)
                .andExpect(redirectedUrl("/professionnels/" + pro.getId()))
                .andExpect(flash().attributeExists("success"));

        List<Review> reviews = reviewsOf(pro);
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getAppointment()).isNull();
    }

    @Test
    void secondFreeReview_onSameProfessional_isRefused() throws Exception {
        submit(client, pro, 5, "Premier avis", null);

        submit(client, pro, 1, "Je reviens noter encore", null)
                .andExpect(redirectedUrl("/professionnels/" + pro.getId()))
                .andExpect(flash().attributeExists("error"));

        assertThat(reviewsOf(pro)).hasSize(1);
    }

    @Test
    void freeReview_onAnotherProfessional_isAccepted() throws Exception {
        submit(client, pro, 5, "Avis pro 1", null);
        submit(client, otherPro, 3, "Avis pro 2", null)
                .andExpect(flash().attributeExists("success"));

        assertThat(reviewsOf(pro)).hasSize(1);
        assertThat(reviewsOf(otherPro)).hasSize(1);
    }

    // ---- Avis verifie (lie a un rendez-vous) ----

    @Test
    void reviewOnOwnCompletedAppointment_isAccepted_andLinked() throws Exception {
        Appointment done = appointment(client, pro, AppointmentStatus.TERMINE);

        submit(client, pro, 5, "Coupe parfaite", done.getId())
                .andExpect(flash().attributeExists("success"));

        List<Review> reviews = reviewsOf(pro);
        assertThat(reviews).hasSize(1);
        assertThat(reviews.get(0).getAppointment().getId()).isEqualTo(done.getId());
    }

    // Un avis verifie ne compte pas dans la limite d'un avis libre : le
    // client qui revient plusieurs fois peut noter chaque prestation.
    @Test
    void verifiedReviews_areAllowedInAdditionToFreeReview() throws Exception {
        Appointment first = appointment(client, pro, AppointmentStatus.TERMINE);
        Appointment second = appointment(client, pro, AppointmentStatus.TERMINE);

        submit(client, pro, 4, "Avis libre", null);
        submit(client, pro, 5, "Premiere visite", first.getId()).andExpect(flash().attributeExists("success"));
        submit(client, pro, 5, "Deuxieme visite", second.getId()).andExpect(flash().attributeExists("success"));

        assertThat(reviewsOf(pro)).hasSize(3);
    }

    @Test
    void secondReview_onSameAppointment_isRefused() throws Exception {
        Appointment done = appointment(client, pro, AppointmentStatus.TERMINE);
        submit(client, pro, 5, "Premier avis", done.getId());

        submit(client, pro, 1, "Doublon", done.getId())
                .andExpect(flash().attributeExists("error"));

        assertThat(reviewsOf(pro)).hasSize(1);
    }

    @ParameterizedTest(name = "rendez-vous {0} : avis refuse")
    @EnumSource(value = AppointmentStatus.class, names = {"EN_ATTENTE", "CONFIRME", "REFUSE", "ANNULE"})
    void reviewOnNotCompletedAppointment_isRefused(AppointmentStatus status) throws Exception {
        Appointment notDone = appointment(client, pro, status);

        submit(client, pro, 5, "Trop tot", notDone.getId())
                .andExpect(flash().attributeExists("error"));

        assertThat(reviewsOf(pro)).isEmpty();
    }

    @Test
    void reviewOnAnotherClientsAppointment_isRefused() throws Exception {
        Appointment notMine = appointment(otherClient, pro, AppointmentStatus.TERMINE);

        submit(client, pro, 1, "J'emprunte le rendez-vous d'un autre", notMine.getId())
                .andExpect(flash().attributeExists("error"));

        assertThat(reviewsOf(pro)).isEmpty();
    }

    @Test
    void reviewWithAppointmentOfAnotherProfessional_isRefused() throws Exception {
        Appointment elsewhere = appointment(client, otherPro, AppointmentStatus.TERMINE);

        submit(client, pro, 1, "Mauvais professionnel", elsewhere.getId())
                .andExpect(flash().attributeExists("error"));

        assertThat(reviewsOf(pro)).isEmpty();
        assertThat(reviewsOf(otherPro)).isEmpty();
    }

    @Test
    void reviewWithUnknownAppointment_isRefused() throws Exception {
        submit(client, pro, 5, "Rendez-vous invente", 999_999L)
                .andExpect(redirectedUrl("/professionnels/" + pro.getId()))
                .andExpect(flash().attributeExists("error"));

        assertThat(reviewsOf(pro)).isEmpty();
    }

    // ---- Validation du formulaire ----

    @Test
    void ratingOutOfRange_isRefused() throws Exception {
        submit(client, pro, 0, "Zero", null).andExpect(flash().attributeExists("error"));
        submit(client, pro, 6, "Six", null).andExpect(flash().attributeExists("error"));

        assertThat(reviewsOf(pro)).isEmpty();
    }

    @Test
    void commentLongerThan1000Characters_isRefusedWithMessage() throws Exception {
        submit(client, pro, 3, "a".repeat(1001), null)
                .andExpect(redirectedUrl("/professionnels/" + pro.getId()))
                .andExpect(flash().attributeExists("error"));

        assertThat(reviewsOf(pro)).isEmpty();
    }

    @Test
    void commentOf1000Characters_isAccepted() throws Exception {
        submit(client, pro, 3, "a".repeat(1000), null).andExpect(flash().attributeExists("success"));

        assertThat(reviewsOf(pro)).hasSize(1);
    }

    // ---- Garde-fou en base ----

    // Meme si le controle applicatif etait contourne (deux requetes
    // simultanees), la base refuse un second avis sur le meme rendez-vous.
    @Test
    void database_refusesTwoReviewsOnSameAppointment() {
        Appointment done = appointment(client, pro, AppointmentStatus.TERMINE);
        reviewRepository.saveAndFlush(review(done));

        assertThatThrownBy(() -> reviewRepository.saveAndFlush(review(done)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---- Moderation admin ----

    @Test
    void adminReviewsPage_showsVerifiedAndFreeBadges() throws Exception {
        Appointment done = appointment(client, pro, AppointmentStatus.TERMINE);
        submit(client, pro, 5, "Avis verifie", done.getId());
        submit(otherClient, pro, 2, "Avis libre", null);

        mockMvc.perform(get("/admin/avis").with(user("admin" + EMAIL_SUFFIX).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Verifie")))
                .andExpect(content().string(containsString("Libre")));
    }

    // ---- Outils ----

    private ResultActions submit(User author, ProfessionalProfile target, int rating, String comment,
                                 Long appointmentId) throws Exception {
        var request = post("/avis/professionnels/{id}", target.getId())
                .with(user(new CustomUserDetails(author)))
                .with(csrf())
                .param("rating", String.valueOf(rating))
                .param("comment", comment);
        if (appointmentId != null) {
            request.param("appointmentId", String.valueOf(appointmentId));
        }
        return mockMvc.perform(request).andExpect(status().is3xxRedirection());
    }

    private List<Review> reviewsOf(ProfessionalProfile target) {
        return reviewRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(r -> r.getProfessional().getId().equals(target.getId()))
                .toList();
    }

    private Review review(Appointment appointment) {
        return Review.builder()
                .client(client)
                .professional(pro)
                .appointment(appointment)
                .rating(5)
                .comment("Base")
                .build();
    }

    // Un creneau par rendez-vous : un creneau actif ne peut servir qu'a un
    // seul rendez-vous (index unique active_time_slot_id).
    private Appointment appointment(User owner, ProfessionalProfile professional, AppointmentStatus status) {
        Prestation prestation = prestationRepository.save(Prestation.builder()
                .professional(professional)
                .name("Coupe")
                .type(ServiceType.COIFFURE)
                .price(new BigDecimal("20"))
                .durationMinutes(30)
                .active(true)
                .build());
        TimeSlot slot = timeSlotRepository.save(TimeSlot.builder()
                .professional(professional)
                .startDateTime(LocalDateTime.now().minusDays(1))
                .available(false)
                .build());
        boolean active = status != AppointmentStatus.REFUSE && status != AppointmentStatus.ANNULE;
        return appointmentRepository.save(Appointment.builder()
                .client(owner)
                .professional(professional)
                .prestation(prestation)
                .timeSlot(slot)
                .activeTimeSlotId(active ? slot.getId() : null)
                .status(status)
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

    private ProfessionalProfile createProfessional(String name) {
        User user = userRepository.save(User.builder()
                .email(name + EMAIL_SUFFIX)
                .password("{noop}secret")
                .firstName(name)
                .lastName("Test")
                .role(Role.PROFESSIONAL)
                .enabled(true)
                .emailVerified(true)
                .build());
        return profileRepository.save(ProfessionalProfile.builder()
                .user(user)
                .businessName("Salon " + name)
                .city("Paris")
                .targetGender(TargetGender.MIXTE)
                .validated(true)
                .build());
    }
}
