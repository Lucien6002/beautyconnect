package com.beautyconnect;

import com.beautyconnect.model.*;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Rendu réel sans transaction de test : protège le catalogue public et le chargement du profil. */
@SpringBootTest
@ActiveProfiles("test")
class HomeCatalogTests {
    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ProfessionalProfileRepository profiles;
    @Autowired PrestationRepository prestations;
    private MockMvc mvc;
    private final List<ProfessionalProfile> fixtures = new ArrayList<>();

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void cleanup() {
        for (var profile : fixtures) {
            prestations.deleteAll(prestations.findByProfessional(profile));
            profiles.deleteById(profile.getId());
            users.deleteById(profile.getUser().getId());
        }
    }

    @Test void homeOnlyShowsActiveServicesFromValidatedEnabledProfessionals() throws Exception {
        var visible = professional("visible", true, true);
        var service = service(visible, "Coupe publique accueil", true);
        service(visible, "Prestation retiree accueil", false);
        service(professional("pending", false, true), "Profil non valide accueil", true);
        service(professional("suspended", true, false), "Compte suspendu accueil", true);

        var result = mvc.perform(get("/").locale(java.util.Locale.FRANCE)).andExpect(status().isOk()).andReturn();
        var html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("Coupe publique accueil", "25,00", "45 min")
                .contains("/professionnels/" + visible.getId() + "?prestation=" + service.getId())
                .doesNotContain("Prestation retiree accueil", "Profil non valide accueil", "Compte suspendu accueil");
    }

    @Test void homepageCatalogIsLimitedToSixServices() throws Exception {
        var visible = professional("bounded", true, true);
        for (int i = 0; i < 8; i++) service(visible, "Accueil prestation " + i, true);
        var result = mvc.perform(get("/accueil")).andExpect(status().isOk()).andReturn();
        assertThat((List<?>) result.getModelAndView().getModel().get("featuredPrestations")).hasSize(6);
    }

    @Test void homepageRepresentsEachAvailableCategory() throws Exception {
        var visible = professional("diverse", true, true);
        for (var type : ServiceType.values()) {
            for (int i = 0; i < 3; i++) {
                var p = service(visible, type.name() + i, true); p.setType(type); prestations.save(p);
            }
        }
        var result = mvc.perform(get("/")).andExpect(status().isOk()).andReturn();
        @SuppressWarnings("unchecked")
        var featured = (List<Prestation>) result.getModelAndView().getModel().get("featuredPrestations");
        assertThat(featured).hasSize(6);
        assertThat(featured.stream().map(Prestation::getType).distinct()).containsExactlyInAnyOrder(ServiceType.values());
    }

    private ProfessionalProfile professional(String name, boolean validated, boolean enabled) {
        var user = users.save(User.builder().email(name + "@home-catalog.test").password("hash")
                .firstName("Accueil").lastName("Test").role(Role.PROFESSIONAL).enabled(enabled).emailVerified(true).build());
        var profile = profiles.save(ProfessionalProfile.builder().user(user).businessName(name)
                .city("Paris").targetGender(TargetGender.MIXTE).validated(validated).build());
        fixtures.add(profile);
        return profile;
    }

    private Prestation service(ProfessionalProfile profile, String name, boolean active) {
        return prestations.save(Prestation.builder().professional(profile).name(name).type(ServiceType.COIFFURE)
                .price(new BigDecimal("25.00")).durationMinutes(45).active(active).build());
    }
}
