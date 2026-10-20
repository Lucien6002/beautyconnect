package com.beautyconnect;

import com.beautyconnect.config.FranceDemoData;
import com.beautyconnect.config.FranceDemoInitializer;
import com.beautyconnect.model.*;
import com.beautyconnect.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(FranceDemoDataTests.SeedConfiguration.class)
@Transactional
class FranceDemoDataTests {
    @TestConfiguration
    static class SeedConfiguration {
        // Tester explicitement le service, sans lancer l'initialiseur au démarrage des tests.
        @Bean FranceDemoData demoData(UserRepository users, ProfessionalProfileRepository profiles,
                PrestationRepository prestations, TimeSlotRepository slots, AppointmentRepository appointments,
                ReviewRepository reviews, FavoriteRepository favorites, PasswordEncoder encoder) {
            return new FranceDemoData(users, profiles, prestations, slots, appointments, reviews, favorites, encoder);
        }
    }
    @Autowired FranceDemoData data;
    @Autowired UserRepository users;
    @Autowired ProfessionalProfileRepository profiles;
    @Autowired PrestationRepository prestations;
    @Autowired TimeSlotRepository slots;
    @Autowired AppointmentRepository appointments;
    @Autowired ReviewRepository reviews;
    @Autowired FavoriteRepository favorites;
    @Autowired PasswordEncoder encoder;

    @Test void showcaseCreatesSixEditablePublicAccountsAcrossSixCitiesAndPreservesChanges() throws Exception {
        assertThat(data.seedShowcase().professionalsCreated()).isEqualTo(6);
        var catalog = profiles.findAll();
        assertThat(catalog).hasSize(6).allMatch(p -> p.isValidated() && p.isCoordinatesPublic() && p.getUser().isEnabled());
        assertThat(catalog).extracting(ProfessionalProfile::getCity)
                .containsExactlyInAnyOrder("Paris", "Lyon", "Lille", "Nantes", "Bordeaux", "Marseille");
        assertThat(prestations.count()).isEqualTo(12);
        for (var profile : catalog) {
            assertThat(prestations.findByProfessional(profile)).hasSize(2);
            assertThat(slots.findByProfessionalAndAvailableTrueAndStartDateTimeAfterOrderByStartDateTimeAsc(profile, java.time.LocalDateTime.now())).isNotEmpty();
        }
        var sarah = users.findByEmail("sarah@beautyconnect.local").orElseThrow();
        assertThat(encoder.matches("Pro12345!", sarah.getPassword())).isTrue();
        var profile = profiles.findByUserId(sarah.getId()).orElseThrow();
        profile.setBusinessName("Mon salon modifié");
        profile.setProfilePhotoUrl("https://example.test/photo.jpg");
        profiles.save(profile);
        long slotsBefore = slots.count();
        assertThat(data.seedShowcase().professionalsCreated()).isZero();
        assertThat(profiles.count()).isEqualTo(6);
        assertThat(prestations.count()).isEqualTo(12);
        assertThat(slots.count()).isEqualTo(slotsBefore);
        assertThat(profiles.findByUserId(sarah.getId()).orElseThrow().getBusinessName()).isEqualTo("Mon salon modifié");
        assertThat(profiles.findByUserId(sarah.getId()).orElseThrow().getProfilePhotoUrl()).isEqualTo("https://example.test/photo.jpg");
    }

    @Test void twoCitiesProvideDistinctCatalogsPublicAndPrivateLocationsAndBookableSlots() throws Exception {
        assertThat(data.seed(2).professionalsCreated()).isEqualTo(6);
        var demoUsers = users.findAll().stream().filter(u -> u.getEmail().endsWith(FranceDemoData.DOMAIN)).toList();
        assertThat(demoUsers).hasSize(9);
        assertThat(demoUsers).allMatch(u -> encoder.matches(FranceDemoData.PASSWORD, u.getPassword()));
        var demoProfiles = profiles.findAll().stream().filter(p -> p.getUser().getEmail().endsWith(FranceDemoData.DOMAIN)).toList();
        assertThat(demoProfiles).hasSize(6).allMatch(p -> !p.getBusinessName().startsWith("[DÉMO]"));
        assertThat(demoProfiles.stream().map(ProfessionalProfile::getCity).distinct()).containsExactlyInAnyOrder("Paris", "Lyon");
        assertThat(demoProfiles.stream().filter(ProfessionalProfile::isCoordinatesPublic)).hasSize(2);
        assertThat(demoProfiles.stream().filter(p -> p.getLatitude() == null)).hasSize(2);
        assertThat(demoProfiles.stream().filter(p -> !p.isValidated())).hasSize(1);
        assertThat(demoProfiles.stream().filter(p -> !p.getUser().isEnabled())).hasSize(1);
        for (var profile : demoProfiles) {
            var catalog = prestations.findByProfessional(profile);
            assertThat(catalog).hasSize(3);
            assertThat(catalog.stream().filter(Prestation::isActive)).hasSize(2);
            assertThat(slots.findByProfessionalAndAvailableTrueAndStartDateTimeAfterOrderByStartDateTimeAsc(profile, java.time.LocalDateTime.now())).isNotEmpty();
        }
    }

    @Test void runningTwicePreservesEditsAndDoesNotDuplicateHistoryOrFavorites() throws Exception {
        data.seed(2);
        long userCount = users.count(), serviceCount = prestations.count(), slotCount = slots.count();
        long appointmentCount = appointments.count(), favoriteCount = favorites.count(), reviewCount = reviews.count();
        var owner = users.findByEmail("coiffure.75056" + FranceDemoData.DOMAIN).orElseThrow();
        owner.setFirstName("Mon prénom modifié"); users.save(owner);
        assertThat(data.seed(2).professionalsCreated()).isZero();
        assertThat(users.count()).isEqualTo(userCount);
        assertThat(prestations.count()).isEqualTo(serviceCount);
        assertThat(slots.count()).isEqualTo(slotCount);
        assertThat(appointments.count()).isEqualTo(appointmentCount);
        assertThat(favorites.count()).isEqualTo(favoriteCount);
        assertThat(reviews.count()).isEqualTo(reviewCount);
        assertThat(users.findById(owner.getId()).orElseThrow().getFirstName()).isEqualTo("Mon prénom modifié");
    }

    @Test void refreshesLegacyGeneratedNamesButPreservesCustomizedBusinessNames() throws Exception {
        data.seed(1);
        var owner = users.findByEmail("coiffure.75056" + FranceDemoData.DOMAIN).orElseThrow();
        var profile = profiles.findByUserId(owner.getId()).orElseThrow();
        profile.setBusinessName("[DÉMO] Coiffure · Paris"); profiles.save(profile);
        data.seed(1);
        assertThat(profiles.findById(profile.getId()).orElseThrow().getBusinessName()).isEqualTo("Coiffure · Paris");
        profile.setBusinessName("Mon atelier personnalisé"); profiles.save(profile);
        data.seed(1);
        assertThat(profiles.findById(profile.getId()).orElseThrow().getBusinessName()).isEqualTo("Mon atelier personnalisé");
    }

    @Test void confirmedAndCompletedAppointmentsOccupyTheirSlotsAndReviewsAreLinkedToCompletedVisits() throws Exception {
        data.seed(2);
        var history = appointments.findAll();
        assertThat(history).hasSize(6);
        assertThat(history.stream().map(Appointment::getStatus)).contains(AppointmentStatus.CONFIRME, AppointmentStatus.TERMINE, AppointmentStatus.ANNULE);
        for (var visit : history) {
            if (visit.getStatus() == AppointmentStatus.ANNULE) {
                assertThat(visit.getActiveTimeSlotId()).isNull();
                assertThat(visit.getTimeSlot().isAvailable()).isTrue();
            } else {
                assertThat(visit.getActiveTimeSlotId()).isEqualTo(visit.getTimeSlot().getId());
                assertThat(visit.getTimeSlot().isAvailable()).isFalse();
            }
        }
        assertThat(reviews.findAll()).hasSize(2).allMatch(r -> r.getAppointment().getStatus() == AppointmentStatus.TERMINE);
        assertThat(favorites.count()).isEqualTo(2);
    }

    @Test void increasingTheCityLimitKeepsEachClientsScenarioInItsOriginalCity() throws Exception {
        data.seed(1);
        assertThat(data.seed(2).professionalsCreated()).isEqualTo(3);
        for (int i = 1; i <= 2; i++) {
            User client = users.findByEmail("client" + i + FranceDemoData.DOMAIN).orElseThrow();
            assertThat(appointments.findByClientOrderByCreatedAtDesc(client)).hasSize(3);
        }
        assertThat(appointments.count()).isEqualTo(6);
    }

    @Test void bundledCitiesCoverThirtyDistinctCommunesIncludingOverseasWithoutNetworkCalls() throws Exception {
        var cities = data.cities();
        assertThat(cities).hasSize(30);
        assertThat(cities.stream().map(FranceDemoData.City::code)).doesNotHaveDuplicates().contains("97411", "97209");
        assertThat(cities).allMatch(c -> com.beautyconnect.utils.LocationUtils.isValidPosition(c.latitude(), c.longitude()));
    }

    @Test void invalidVolumeDoesNotCreateAnyAccounts() {
        long count = users.count();
        assertThatThrownBy(() -> data.seed(31)).isInstanceOf(IllegalArgumentException.class);
        assertThat(users.count()).isEqualTo(count);
    }

    @Test void productionExcludesBothDemoBeansEvenWhenDevAndTheEnableFlagArePresent() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("dev", "prod");
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("demo", Map.of("app.demo.france.enabled", "true")));
            context.register(FranceDemoData.class, FranceDemoInitializer.class);
            context.refresh();
            assertThat(context.getBeansOfType(FranceDemoData.class)).isEmpty();
            assertThat(context.getBeansOfType(FranceDemoInitializer.class)).isEmpty();
        }
    }
}
