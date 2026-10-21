package com.beautyconnect;

import com.beautyconnect.model.*;
import com.beautyconnect.repository.*;
import com.beautyconnect.service.*;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.exception.IllegalOperationException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

@SpringBootTest(properties = "app.base-url=https://beautyconnect.example")
@ActiveProfiles("test")
class PasswordResetTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired VerificationTokenRepository activationTokens;
    @Autowired PasswordEncoder encoder;
    @Autowired PasswordResetService service;
    @Autowired UserService userService;
    @Autowired SessionRegistry sessions;
    @MockitoBean JavaMailSender sender;
    MockMvc mvc;
    User account;
    static final String OLD = "AncienMot123!", NEXT = "NouveauMot456!";

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        account = users.save(User.builder().email("client@password-reset.test").firstName("Recette")
                .lastName("Mot de passe").password(encoder.encode(OLD)).role(Role.CLIENT)
                .enabled(true).emailVerified(true).build());
    }
    @AfterEach void cleanup() {
        for (var user : users.findAll().stream().filter(u -> u.getEmail().endsWith("@password-reset.test")).toList()) {
            tokens.findByUser(user).ifPresent(tokens::delete); activationTokens.findByUser(user).ifPresent(activationTokens::delete);
            sessions.getAllSessions(new CustomUserDetails(user), true).forEach(s -> sessions.removeSessionInformation(s.getSessionId()));
            users.delete(user);
        }
    }
    private ResultActions request(String email) throws Exception {
        return mvc.perform(post("/mot-de-passe-oublie").with(csrf()).param("email", email))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/mot-de-passe-oublie"));
    }
    private String latestToken() {
        var mail = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, atLeastOnce()).send(mail.capture());
        String text = mail.getAllValues().getLast().getText();
        var match = Pattern.compile("https://beautyconnect.example/reinitialiser-mot-de-passe\\?token=([A-Za-z0-9_-]{43})").matcher(text);
        assertThat(match.find()).isTrue(); return match.group(1);
    }
    private ResultActions reset(String raw, String password, String confirmation) throws Exception {
        return mvc.perform(post("/reinitialiser-mot-de-passe").with(csrf())
                .param("token", raw).param("newPassword", password).param("confirmPassword", confirmation));
    }

    @Test void pagesArePublicButPostRequiresCsrfAndUnknownEmailHasSameResponse() throws Exception {
        mvc.perform(get("/connexion")).andExpect(content().string(containsString("Mot de passe oublié ?")));
        mvc.perform(get("/mot-de-passe-oublie")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        mvc.perform(post("/mot-de-passe-oublie").param("email", account.getEmail())).andExpect(status().isForbidden());
        var known = request(account.getEmail()).andReturn().getFlashMap().get("success");
        var unknown = request("inconnu@password-reset.test").andReturn().getFlashMap().get("success");
        assertThat(unknown).isEqualTo(known);
        verify(sender, times(1)).send(any(SimpleMailMessage.class));
        var stored = tokens.findByUser(account).orElseThrow();
        assertThat(stored.getTokenHash()).hasSize(64).doesNotContain(latestToken());
        assertThat(stored.getExpiresAt()).isAfter(LocalDateTime.now().plusMinutes(14));
    }

    @ParameterizedTest @EnumSource(Role.class)
    void resetWorksForEachRoleConsumesTokenAndExpiresExistingSessions(Role role) throws Exception {
        account.setRole(role); users.save(account);
        request(account.getEmail()); String raw = latestToken();
        sessions.registerNewSession("reset-test-session", new CustomUserDetails(account));
        mvc.perform(get("/reinitialiser-mot-de-passe").param("token", raw))
                .andExpect(status().isOk()).andExpect(model().attribute("validLink", true))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        reset(raw, NEXT, NEXT).andExpect(redirectedUrl("/connexion")).andExpect(flash().attributeExists("success"));
        assertThat(encoder.matches(NEXT, users.findById(account.getId()).orElseThrow().getPassword())).isTrue();
        assertThat(tokens.findByUser(account)).isEmpty();
        assertThat(sessions.getSessionInformation("reset-test-session").isExpired()).isTrue();
        mvc.perform(post("/connexion").with(csrf()).param("username", account.getEmail()).param("password", OLD))
                .andExpect(redirectedUrl("/connexion?erreur"));
        mvc.perform(post("/connexion").with(csrf()).param("username", account.getEmail()).param("password", NEXT))
                .andExpect(redirectedUrl(role == Role.ADMIN ? "/admin/tableau-bord" : role == Role.PROFESSIONAL ? "/pro/tableau-bord" : "/"));
        reset(raw, "EncoreAutre123!", "EncoreAutre123!").andExpect(status().isOk()).andExpect(model().attribute("validLink", false));
        assertThat(encoder.matches(NEXT, users.findById(account.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test void mismatchedShortAndTooManyUtf8BytesDoNotConsumeTheLinkOrEchoPasswords() throws Exception {
        request(account.getEmail()); String raw = latestToken();
        reset(raw, NEXT, "Different123!").andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("resetPasswordForm", "confirmPassword"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("value=\"" + NEXT + "\""))));
        reset(raw, "court", "court").andExpect(model().attributeHasFieldErrors("resetPasswordForm", "newPassword"));
        reset(raw, "é".repeat(40), "é".repeat(40)).andExpect(model().attributeHasErrors("resetPasswordForm"));
        assertThat(service.isValid(raw)).isTrue();
        assertThat(encoder.matches(OLD, users.findById(account.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test void expiredAndMalformedLinksCannotChangePassword() throws Exception {
        request(account.getEmail()); String raw = latestToken();
        var token = tokens.findByUser(account).orElseThrow(); token.setExpiresAt(LocalDateTime.now().minusMinutes(1)); tokens.save(token);
        mvc.perform(get("/reinitialiser-mot-de-passe").param("token", raw)).andExpect(model().attribute("validLink", false));
        reset(raw, NEXT, NEXT).andExpect(model().attribute("validLink", false));
        reset("invalide", NEXT, NEXT).andExpect(model().attribute("validLink", false));
        assertThat(encoder.matches(OLD, users.findById(account.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test void resendIsThrottledThenReplacesPreviousLinkWithoutTouchingActivationTokens() throws Exception {
        activationTokens.save(VerificationToken.builder().user(account).token("activation-separate")
                .expiryDate(LocalDateTime.now().plusMinutes(15)).build());
        request(account.getEmail()); String first = latestToken();
        request(account.getEmail()); verify(sender, times(1)).send(any(SimpleMailMessage.class));
        var token = tokens.findByUser(account).orElseThrow(); token.setCreatedAt(LocalDateTime.now().minusMinutes(2)); tokens.save(token);
        request(account.getEmail()); String second = latestToken();
        assertThat(second).isNotEqualTo(first);
        assertThat(service.isValid(first)).isFalse(); assertThat(service.isValid(second)).isTrue();
        assertThat(activationTokens.findByToken("activation-separate")).isPresent();
    }

    @Test void unverifiedAndDisabledAccountsReceiveNoMailAndExistingLinkCannotReactivateAccount() throws Exception {
        account.setEmailVerified(false); users.save(account); request(account.getEmail()); verifyNoInteractions(sender);
        account.setEmailVerified(true); account.setEnabled(false); users.save(account); request(account.getEmail()); verifyNoInteractions(sender);
        account.setEnabled(true); users.save(account); request(account.getEmail()); String raw = latestToken();
        account.setEnabled(false); users.save(account);
        reset(raw, NEXT, NEXT).andExpect(model().attribute("validLink", false));
        assertThat(users.findById(account.getId()).orElseThrow().isEnabled()).isFalse();
        assertThat(encoder.matches(OLD, users.findById(account.getId()).orElseThrow().getPassword())).isTrue();
    }

    @Test void authenticatedPasswordChangeInvalidatesPendingResetLink() throws Exception {
        request(account.getEmail()); String raw = latestToken();
        userService.changePassword(account.getId(), OLD, NEXT);
        assertThat(service.isValid(raw)).isFalse();
    }

    @Test void concurrentConsumptionOfTheSameLinkOnlySucceedsOnce() throws Exception {
        request(account.getEmail()); String raw = latestToken();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2); var go = new CountDownLatch(1);
            Callable<Boolean> consume = () -> { ready.countDown(); go.await();
                try { service.reset(raw, NEXT); return true; } catch (IllegalOperationException ex) { return false; } };
            var first = executor.submit(consume); var second = executor.submit(consume);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); go.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(true, false);
        }
    }
}
