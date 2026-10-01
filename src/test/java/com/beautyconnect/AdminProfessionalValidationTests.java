package com.beautyconnect;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Role;
import com.beautyconnect.model.TargetGender;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.UserRepository;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Validation des profils professionnels par l'administrateur.
 *
 * Pas de @Transactional sur la classe : volontaire. Une transaction de test
 * garderait la session Hibernate ouverte pendant le rendu Thymeleaf et
 * masquerait les LazyInitializationException (ProfessionalProfile.user est
 * LAZY et spring.jpa.open-in-view=false). Les donnees sont donc nettoyees a
 * la main dans @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class AdminProfessionalValidationTests {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ProfessionalProfileRepository profileRepository;
    @Autowired
    private SessionRegistry sessionRegistry;

    // Remplace le vrai service : aucun envoi SMTP pendant les tests, et on
    // peut verifier que la notification est bien declenchee.
    @MockitoBean
    private EmailService emailService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // Supprime uniquement les comptes crees par ces tests (emails en
    // @test.local) : les donnees de demonstration du DataInitializer
    // (profil avec creneaux et prestations) doivent rester intactes.
    @AfterEach
    void cleanUp() {
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().endsWith("@test.local"))
                .forEach(u -> {
                    profileRepository.findByUserId(u.getId()).ifPresent(profileRepository::delete);
                    userRepository.delete(u);
                });
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void validatePendingProfile_makesItValidatedAndNotifiesProfessional() throws Exception {
        ProfessionalProfile profile = createProfessional("pending@test.local", false, true);

        mockMvc.perform(post("/admin/professionnels/{id}/valider", profile.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/professionnels/" + profile.getId()))
                .andExpect(flash().attributeExists("success"));

        assertThat(profileRepository.findById(profile.getId()).orElseThrow().isValidated()).isTrue();
        verify(emailService).sendProfessionalValidated(any(ProfessionalProfile.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void validateAlreadyValidatedProfile_isRefusedWithoutSecondMail() throws Exception {
        ProfessionalProfile profile = createProfessional("validated@test.local", true, true);

        mockMvc.perform(post("/admin/professionnels/{id}/valider", profile.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("deja valide")));

        verify(emailService, never()).sendProfessionalValidated(any());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void validateDisabledAccount_isRefused() throws Exception {
        ProfessionalProfile profile = createProfessional("disabled@test.local", false, false);

        mockMvc.perform(post("/admin/professionnels/{id}/valider", profile.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("desactive")));

        assertThat(profileRepository.findById(profile.getId()).orElseThrow().isValidated()).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void revokeValidation_hidesProfileFromSearch() throws Exception {
        ProfessionalProfile profile = createProfessional("revoke@test.local", true, true);

        mockMvc.perform(post("/admin/professionnels/{id}/retirer-validation", profile.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertThat(profileRepository.findById(profile.getId()).orElseThrow().isValidated()).isFalse();
        assertThat(profileRepository.search(null, null, null)).noneMatch(p -> p.getId().equals(profile.getId()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void validateUnknownProfile_returns404() throws Exception {
        mockMvc.perform(post("/admin/professionnels/{id}/valider", 999_999L).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminPages_renderWithLazyUserLoaded() throws Exception {
        ProfessionalProfile pending = createProfessional("list-pending@test.local", false, true);
        createProfessional("list-validated@test.local", true, true);

        mockMvc.perform(get("/admin/professionnels"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("list-pending@test.local")))
                .andExpect(content().string(containsString("list-validated@test.local")));

        mockMvc.perform(get("/admin/professionnels").param("statut", "en-attente"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("list-pending@test.local")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("list-validated@test.local"))));

        mockMvc.perform(get("/admin/professionnels/{id}", pending.getId()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Valider le profil")));

        mockMvc.perform(get("/admin/tableau-bord"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "CLIENT")
    void nonAdmin_cannotValidate() throws Exception {
        ProfessionalProfile profile = createProfessional("forbidden@test.local", false, true);

        mockMvc.perform(post("/admin/professionnels/{id}/valider", profile.getId()).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(profileRepository.findById(profile.getId()).orElseThrow().isValidated()).isFalse();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void disableProfessional_hidesProfileAndLogsOutOpenSessions() throws Exception {
        ProfessionalProfile profile = createProfessional("to-disable@test.local", true, true);
        User user = userRepository.findByEmail("to-disable@test.local").orElseThrow();
        // Simule une session ouverte du professionnel
        sessionRegistry.registerNewSession("session-pro", new CustomUserDetails(user));

        mockMvc.perform(post("/admin/utilisateurs/{id}/desactiver", user.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("success"));

        assertThat(userRepository.findById(user.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(sessionRegistry.getSessionInformation("session-pro").isExpired()).isTrue();
        assertThat(profileRepository.search(null, null, null)).noneMatch(p -> p.getId().equals(profile.getId()));
        mockMvc.perform(get("/professionnels/{id}", profile.getId()))
                .andExpect(status().isNotFound());

        sessionRegistry.removeSessionInformation("session-pro");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void reenableProfessional_makesValidatedProfilePublicAgain() throws Exception {
        ProfessionalProfile profile = createProfessional("to-enable@test.local", true, false);
        User user = userRepository.findByEmail("to-enable@test.local").orElseThrow();

        mockMvc.perform(post("/admin/utilisateurs/{id}/activer", user.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(profileRepository.search(null, null, null)).anyMatch(p -> p.getId().equals(profile.getId()));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminAccount_cannotBeDisabled() throws Exception {
        User admin = userRepository.findAll().stream().filter(u -> u.getRole() == Role.ADMIN).findFirst().orElseThrow();

        mockMvc.perform(post("/admin/utilisateurs/{id}/desactiver", admin.getId()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeExists("error"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().isEnabled()).isTrue();
    }

    private ProfessionalProfile createProfessional(String email, boolean validated, boolean enabled) {
        User user = userRepository.save(User.builder()
                .email(email)
                .password("{noop}secret")
                .firstName("Prenom")
                .lastName("Nom")
                .role(Role.PROFESSIONAL)
                .enabled(enabled)
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
