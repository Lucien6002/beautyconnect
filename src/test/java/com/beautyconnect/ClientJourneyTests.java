package com.beautyconnect;

import com.beautyconnect.dto.*;
import com.beautyconnect.exception.*;
import com.beautyconnect.model.*;
import com.beautyconnect.repository.*;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = "app.base-url=https://beautyconnect.example")
@ActiveProfiles("test")
class ClientJourneyTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ProfessionalProfileRepository profiles;
    @Autowired PrestationRepository prestations;
    @Autowired TimeSlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired VerificationTokenRepository tokens;
    @Autowired AppointmentService service;
    @Autowired AuthTokenService activation;
    @Autowired ProfessionalService professionals;
    @Autowired NearestProfessionalService nearest;
    @MockitoBean JavaMailSender sender;
    @MockitoBean EmailService mail;
    @MockitoBean RoutingClient routing;
    MockMvc mvc;
    User client;
    ProfessionalProfile pro;
    Prestation prestation;
    TimeSlot slot;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        client = users.save(account("client@journey.test", Role.CLIENT));
        pro = professional("pro@journey.test", true);
        prestation = prestations.save(Prestation.builder().professional(pro).name("Coupe")
                .type(ServiceType.COIFFURE).price(new BigDecimal("20")).durationMinutes(30).active(true).build());
        slot = slots.save(TimeSlot.builder().professional(pro).startDateTime(LocalDateTime.now().plusDays(7)).available(true).build());
    }
    @AfterEach void cleanup() {
        appointments.deleteAll(); tokens.deleteAll(); slots.deleteAll(); prestations.deleteAll(); profiles.deleteAll(); users.deleteAll();
    }
    private User account(String email, Role role) {
        return User.builder().email(email).firstName("Prénom").lastName("Nom").password("hashed")
                .role(role).enabled(true).emailVerified(true).build();
    }
    private ProfessionalProfile professional(String email, boolean validated) {
        return profiles.save(ProfessionalProfile.builder().user(users.save(account(email, Role.PROFESSIONAL)))
                .businessName(email).city("Paris").validated(validated).coordinatesPublic(true).latitude(48.8566).longitude(2.3522).build());
    }
    private AppointmentForm form() {
        var form = new AppointmentForm(); form.setTimeSlotId(slot.getId()); form.setPrestationId(prestation.getId()); return form;
    }
    private Appointment saved(AppointmentStatus status) {
        return appointments.save(Appointment.builder().client(client).professional(pro).prestation(prestation)
                .timeSlot(slot).status(status).activeTimeSlotId(status == AppointmentStatus.REFUSE || status == AppointmentStatus.ANNULE ? null : slot.getId()).build());
    }

    @Test void cancellationAndRefusalAllowRebookingAndKeepHistory() {
        Appointment first = service.book(client, pro, form());
        service.cancelByClient(client, first.getId());
        Appointment second = service.book(client, pro, form());
        service.refuse(pro, second.getId());
        Appointment third = service.book(client, pro, form());
        assertThat(appointments.count()).isEqualTo(3);
        assertThat(appointments.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.ANNULE);
        assertThat(third.getActiveTimeSlotId()).isEqualTo(slot.getId());
        assertThat(slots.findById(slot.getId()).orElseThrow().isAvailable()).isFalse();
    }

    @Test void concurrentBookingHasOneWinnerAndBusinessError() throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> book = () -> {
                start.await();
                try { service.book(client, pro, form()); return true; }
                catch (IllegalOperationException ex) { assertThat(ex.getMessage()).contains("disponible"); return false; }
            };
            var a = executor.submit(book); var b = executor.submit(book); start.countDown();
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
        assertThat(appointments.count()).isEqualTo(1);
    }

    @ParameterizedTest @EnumSource(AppointmentStatus.class)
    void transitionsAreEnforcedForEveryStartingState(AppointmentStatus state) {
        Appointment a = saved(state);
        if (state == AppointmentStatus.EN_ATTENTE) service.confirm(pro, a.getId());
        else assertThatThrownBy(() -> service.confirm(pro, a.getId())).isInstanceOf(IllegalOperationException.class);
        a.setStatus(state); appointments.save(a);
        if (state == AppointmentStatus.EN_ATTENTE) service.refuse(pro, a.getId());
        else assertThatThrownBy(() -> service.refuse(pro, a.getId())).isInstanceOf(IllegalOperationException.class);
        a.setStatus(state); appointments.save(a);
        if (state == AppointmentStatus.CONFIRME) service.markCompleted(pro, a.getId());
        else assertThatThrownBy(() -> service.markCompleted(pro, a.getId())).isInstanceOf(IllegalOperationException.class);
        a.setStatus(state); appointments.save(a);
        if (state == AppointmentStatus.EN_ATTENTE || state == AppointmentStatus.CONFIRME) service.cancelByClient(client, a.getId());
        else assertThatThrownBy(() -> service.cancelByClient(client, a.getId())).isInstanceOf(IllegalOperationException.class);
    }

    @Test void forgedIdsAndDisabledServicesAreRejected() {
        var other = professional("other@journey.test", true);
        assertThatThrownBy(() -> service.book(client, other, form())).isInstanceOf(IllegalOperationException.class);
        var otherPrestation = prestations.save(Prestation.builder().professional(other).name("Autre")
                .type(ServiceType.COIFFURE).price(BigDecimal.TEN).durationMinutes(30).active(true).build());
        var form = form(); form.setPrestationId(otherPrestation.getId());
        assertThatThrownBy(() -> service.book(client, pro, form)).isInstanceOf(IllegalOperationException.class);
        prestation.setActive(false); prestations.save(prestation);
        assertThatThrownBy(() -> service.book(client, pro, form())).isInstanceOf(IllegalOperationException.class);
        var longForm = form(); longForm.setNotes("x".repeat(1001));
        assertThatThrownBy(() -> service.book(client, pro, longForm)).isInstanceOf(IllegalOperationException.class);
    }

    @Test void ownershipAndSlotHistoryAreProtected() {
        var a = service.book(client, pro, form());
        User stranger = users.save(account("stranger@journey.test", Role.CLIENT));
        var other = professional("other@journey.test", true);
        assertThatThrownBy(() -> service.cancelByClient(stranger, a.getId())).isInstanceOf(IllegalOperationException.class);
        assertThatThrownBy(() -> service.confirm(other, a.getId())).isInstanceOf(IllegalOperationException.class);
        service.cancelByClient(client, a.getId());
        assertThatThrownBy(() -> professionals.removeTimeSlot(pro, slot.getId())).isInstanceOf(IllegalOperationException.class);
    }

    @Test void clientPagesRenderWithoutOpenSessionAndCsrfIsEnforced() throws Exception {
        service.book(client, pro, form());
        mvc.perform(get("/client/tableau-bord").with(user(new CustomUserDetails(client)))).andExpect(status().isOk());
        mvc.perform(get("/client/rendez-vous").with(user(new CustomUserDetails(client)))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Itinéraire à pied")));
        mvc.perform(post("/rendez-vous/reserver/{id}", pro.getId()).with(user(new CustomUserDetails(client))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/pro/rendez-vous").with(user(new CustomUserDetails(pro.getUser())))).andExpect(status().isOk());
    }

    @Test void smtpFailureCanBeRecoveredAndExpiredTokensAreDeleted() throws Exception {
        doThrow(new org.springframework.mail.MailSendException("offline")).when(sender).send(any(SimpleMailMessage.class));
        mvc.perform(post("/inscription/client").with(csrf()).param("email", "new@journey.test")
                .param("password", "Client123!").param("firstName", "Léa").param("lastName", "Test"))
                .andExpect(status().is3xxRedirection());
        var newUser = users.findByEmail("new@journey.test").orElseThrow();
        assertThat(newUser.isEnabled()).isFalse();
        var token = tokens.findByUser(newUser).orElseThrow();
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1)); tokens.save(token);
        assertThatThrownBy(() -> activation.activateAccount(token.getToken())).isInstanceOf(IllegalOperationException.class);
        assertThat(tokens.findByToken(token.getToken())).isEmpty();
        reset(sender);
        mvc.perform(post("/inscription/renvoyer-activation").with(csrf()).param("email", newUser.getEmail()))
                .andExpect(status().is3xxRedirection());
        var replacement = tokens.findByUser(newUser).orElseThrow();
        var capture = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(capture.capture());
        assertThat(capture.getValue().getText()).contains("https://beautyconnect.example/activer-compte?token=");
        activation.activateAccount(replacement.getToken());
        assertThat(users.findById(newUser.getId()).orElseThrow().isEnabled()).isTrue();
        assertThatThrownBy(() -> activation.activateAccount(replacement.getToken())).isInstanceOf(IllegalOperationException.class);
    }

    @Test void suspendedVerifiedAccountCannotReactivateUsingResend() {
        client.setEnabled(false); users.save(client);
        activation.resendActivation(client.getEmail());
        assertThat(tokens.findByUser(client)).isEmpty(); verifyNoInteractions(sender);
    }

    @Test void searchPreservesFiltersRejectsCoordinatesAndHidesUnvalidatedProfiles() throws Exception {
        var hidden = professional("hidden@journey.test", false);
        mvc.perform(get("/professionnels/{id}", hidden.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/recherche").param("name", pro.getBusinessName()).param("city", "Paris"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Voir sur la carte")));
        mvc.perform(get("/recherche").param("clientLatitude", "91").param("clientLongitude", "2"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/recherche").param("clientLatitude", "NaN").param("clientLongitude", "2"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/recherche").param("clientLatitude", "48"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/recherche").param("page", "-1")).andExpect(status().isBadRequest());
    }

    @Test void routeEndpointIsPublicButOnlyForPublicProfilesAndValidCoordinates() throws Exception {
        var response = RouteResponse.builder().distanceKm(1.5).durationMinutes(18.0)
                .geometry("{\"type\":\"LineString\",\"coordinates\":[[2,48],[2.1,48.1]]}").build();
        when(routing.getWalkingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(response);
        mvc.perform(get("/itineraire/professionnels/{id}", pro.getId()).param("lat", "48").param("lon", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.distanceKm").value(1.5))
                .andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(get("/itineraire/professionnels/{id}", pro.getId()).param("lat", "91").param("lon", "2"))
                .andExpect(status().isBadRequest());
        pro.setValidated(false); profiles.save(pro);
        mvc.perform(get("/itineraire/professionnels/{id}", pro.getId()).param("lat", "48").param("lon", "2"))
                .andExpect(status().isNotFound());
    }

    @Test void matrixReordersByWalkingDistanceAndFallsBackWithoutInventingARoute() {
        var b = professional("b@journey.test", true); b.setLatitude(48.87); profiles.save(b);
        var criteria = new SearchCriteria(); criteria.setClientLatitude(48.85); criteria.setClientLongitude(2.35);
        when(routing.matrixWalking(anyDouble(), anyDouble(), anyList())).thenReturn(List.of(
                new RoutingClient.WalkingMetric(2.4, 29.0), new RoutingClient.WalkingMetric(1.5, 18.0)));
        var result = nearest.rank(List.of(pro, b), criteria);
        assertThat(result.profiles().getFirst().getId()).isEqualTo(b.getId());
        assertThat(result.notice()).contains("résultats mesurés");
        when(routing.matrixWalking(anyDouble(), anyDouble(), anyList())).thenThrow(new RoutingUnavailableException());
        var fallback = nearest.rank(List.of(pro, b), criteria);
        assertThat(fallback.walking()).isEmpty(); assertThat(fallback.notice()).contains("vol d’oiseau");
    }

    @Test void professionalChoosesPublicationAndPrivateCoordinatesNeverReachTheMap() throws Exception {
        pro.setCoordinatesPublic(false); profiles.save(pro);
        mvc.perform(get("/recherche").param("name", pro.getBusinessName()))
                .andExpect(status().isOk()).andExpect(content().string(containsString("latitude: null")));
        mvc.perform(get("/itineraire/professionnels/{id}", pro.getId()).param("lat", "48").param("lon", "2"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/pro/localisation").with(user(new CustomUserDetails(pro.getUser()))))
                .andExpect(status().isOk());
        mvc.perform(post("/pro/localisation").with(user(new CustomUserDetails(pro.getUser()))).with(csrf())
                        .param("latitude", "48.8566").param("longitude", "2.3522").param("coordinatesPublic", "true"))
                .andExpect(status().is3xxRedirection());
        assertThat(profiles.findById(pro.getId()).orElseThrow().isCoordinatesPublic()).isTrue();
        mvc.perform(post("/pro/localisation").with(user(new CustomUserDetails(pro.getUser()))).with(csrf())
                        .param("latitude", "100").param("longitude", "2").param("coordinatesPublic", "true"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("professionalLocationForm"));
    }

    @Test void paginationKeepsFiltersAndRouteFailuresRemainJson() throws Exception {
        for (int i = 0; i < 13; i++) professional("page" + i + "@journey.test", true);
        mvc.perform(get("/recherche").param("city", "Paris"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Suivante")))
                .andExpect(content().string(containsString("city=Paris")));
        when(routing.getWalkingRoute(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenThrow(new RoutingUnavailableException());
        mvc.perform(get("/itineraire/professionnels/{id}", pro.getId()).param("lat", "48").param("lon", "2"))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").exists());
    }

    @Test void confirmationCommitsEvenIfMailFailsAndRepeatedConfirmationIsRejected() {
        var a = service.book(client, pro, form());
        doThrow(new IllegalStateException("SMTP indisponible")).when(mail).sendAppointmentConfirmation(any());
        service.confirm(pro, a.getId());
        assertThat(appointments.findById(a.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CONFIRME);
        assertThatThrownBy(() -> service.confirm(pro, a.getId())).isInstanceOf(IllegalOperationException.class);
        verify(mail, times(1)).sendAppointmentConfirmation(any());
    }

    @Test void inactiveProfessionalAndInactiveClientAreNotReservable() {
        var proUser = users.findById(pro.getUser().getId()).orElseThrow();
        proUser.setEnabled(false); users.save(proUser);
        assertThatThrownBy(() -> service.book(client, pro, form())).isInstanceOf(IllegalOperationException.class);
        proUser.setEnabled(true); users.save(proUser);
        client.setEnabled(false); users.save(client);
        assertThatThrownBy(() -> service.book(client, pro, form())).isInstanceOf(IllegalOperationException.class);
    }

    @ParameterizedTest @EnumSource(Role.class)
    void realLoginRedirectsToTheRoleDashboard(Role role) throws Exception {
        var account = role == Role.CLIENT ? client : role == Role.PROFESSIONAL ? pro.getUser()
                : users.save(account("admin@journey.test", Role.ADMIN));
        account.setPassword(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("LoginTest123!"));
        users.save(account);
        String destination = role == Role.CLIENT ? "/client/tableau-bord"
                : role == Role.PROFESSIONAL ? "/pro/tableau-bord" : "/admin/tableau-bord";
        mvc.perform(post("/connexion").with(csrf()).param("username", account.getEmail()).param("password", "LoginTest123!"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(destination));
    }
}
