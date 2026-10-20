package com.beautyconnect.config;

import com.beautyconnect.model.*;
import com.beautyconnect.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Données fictives identifiées comme telles ; aucun réseau ni mail pendant l'initialisation. */
@Service
@Profile("dev & !prod")
@RequiredArgsConstructor
public class FranceDemoData {
    public static final String DOMAIN = "@demo.beautyconnect.invalid";
    public static final String PASSWORD = "DemoFrance123!";
    private final UserRepository users;
    private final ProfessionalProfileRepository profiles;
    private final PrestationRepository prestations;
    private final TimeSlotRepository slots;
    private final AppointmentRepository appointments;
    private final ReviewRepository reviews;
    private final FavoriteRepository favorites;
    private final PasswordEncoder passwordEncoder;
    private final PrestationPhotoRepository photos;
    private final ShowcaseImportRepository imports;

    public record City(String code, String name, double latitude, double longitude) {}
    public record Result(int cities, int professionalsCreated) {}
    private static final String[] CLIENT_FIRST_NAMES = {"Camille", "Alex", "Noa"};
    private static final String[] CLIENT_LAST_NAMES = {"Martin", "Bernard", "Dubois"};
    private record ServiceExample(String name, String price, int minutes) {}
    private record CreatedProfile(ProfessionalProfile profile, Prestation prestation, TimeSlot nextSlot, int clientIndex) {}

    public List<City> cities() throws IOException {
        try (var input = new ClassPathResource("demo/france-cities.json").getInputStream()) {
            var result = new ArrayList<City>();
            for (var city : new JsonMapper().readTree(input).path("cities")) {
                result.add(new City(city.path("code").asText(), city.path("name").asText(),
                        city.path("latitude").asDouble(), city.path("longitude").asDouble()));
            }
            return List.copyOf(result);
        }
    }

    /** Catalogue de présentation : six établissements, comptes locaux compris.
     * Aucun compte existant ni historique n'est supprimé par l'initialiseur.
     */
    @Transactional(rollbackFor = Exception.class)
    public Result seedShowcase() throws IOException {
        SharedShowcaseCatalog catalog;
        try (var input = new ClassPathResource("demo/showcase-catalog.json").getInputStream()) {
            catalog = new JsonMapper().readValue(input, SharedShowcaseCatalog.class);
        }
        int created = 0;
        for (var salon : catalog.salons()) {
            String profileKey = "salon:" + salon.key();
            var previous = imports.findById(profileKey).orElse(null);
            ProfessionalProfile profile;
            if (previous != null) {
                // Le profil supprimé volontairement ne doit pas être recréé.
                profile = profiles.findById(previous.getTargetId()).orElse(null);
                if (profile == null) continue;
            } else {
                var city = cities().stream().filter(c -> c.code().equals(salon.cityCode())).findFirst().orElseThrow();
                var owner = users.findByEmail(salon.email()).orElse(null);
                if (owner != null && owner.getRole() != Role.PROFESSIONAL) continue;
                if (owner == null) {
                    owner = users.save(User.builder().email(salon.email())
                            .password(passwordEncoder.encode(salon.email().endsWith(DOMAIN) ? PASSWORD : "Pro12345!"))
                            .firstName(salon.firstName()).lastName(city.name()).role(Role.PROFESSIONAL)
                            .enabled(true).emailVerified(true).build());
                }
                profile = profiles.findByUserId(owner.getId()).orElse(null);
                if (profile == null) {
                    profile = profiles.save(ProfessionalProfile.builder().user(owner).businessName(salon.businessName())
                            .city(city.name()).targetGender(TargetGender.MIXTE).validated(true)
                            .latitude(city.latitude()).longitude(city.longitude()).coordinatesPublic(true)
                            .bio("Établissement fictif de présentation. Coordonnées au centre de la commune ; prestations et tarifs simulés.").build());
                    created++;
                }
                imported(profileKey, profile.getId());
            }
            String coverKey = profileKey + ":cover";
            if (!imports.existsById(coverKey)) {
                if (profile.getProfilePhotoUrl() == null || profile.getProfilePhotoUrl().isBlank()) {
                    profile.setProfilePhotoUrl(salon.cover());
                    profile.setProfilePhotoPublicId("shared:" + coverKey);
                }
                imported(coverKey, profile.getId());
            }
            for (var offer : salon.offers()) {
                String offerKey = "offer:" + salon.key() + ":" + offer.key();
                if (imports.existsById(offerKey)) continue;
                var prestation = prestations.findByProfessional(profile).stream()
                        .filter(p -> p.getType() == offer.type()
                                && (p.getName().equalsIgnoreCase(offer.name())
                                    || offer.aliases().stream().anyMatch(alias -> alias.equalsIgnoreCase(p.getName()))))
                        .min(java.util.Comparator.comparing(Prestation::getId)).orElse(null);
                if (prestation == null) {
                    prestation = prestations.save(Prestation.builder().professional(profile).name(offer.name())
                            .type(offer.type()).price(offer.price()).durationMinutes(offer.durationMinutes())
                            .description("Prestation fictive de présentation.").photos(new ArrayList<>()).active(true).build());
                }
                // Ne pas mélanger la galerie de présentation à des photos déjà ajoutées par le propriétaire.
                if (prestation.getPhotos().isEmpty()) {
                    for (int order = 0; order < offer.photos().size(); order++) {
                        var photo = photos.save(PrestationPhoto.builder().prestation(prestation)
                                .photoUrl(offer.photos().get(order)).publicId("shared:" + offerKey + ":" + order)
                                .displayOrder(order).build());
                        prestation.getPhotos().add(photo);
                    }
                }
                imported(offerKey, prestation.getId());
            }
            String slotsKey = profileKey + ":slots";
            if (!imports.existsById(slotsKey)) {
                if (slots.findByProfessionalOrderByStartDateTimeAsc(profile).isEmpty()) {
                    for (int day = 1; day <= 4; day++) for (int hour : new int[]{9, 14})
                        slots.save(TimeSlot.builder().professional(profile)
                                .startDateTime(LocalDate.now().plusDays(day).atTime(hour, 0)).available(true).build());
                }
                imported(slotsKey, profile.getId());
            }
        }
        for (int i = 1; i <= 3; i++) {
            String key = "client:" + i;
            if (imports.existsById(key)) continue;
            String email = "client" + i + DOMAIN;
            var client = users.findByEmail(email).orElse(null);
            if (client != null && client.getRole() != Role.CLIENT) continue;
            if (client == null) client = users.save(User.builder().email(email).password(passwordEncoder.encode(PASSWORD))
                    .firstName(CLIENT_FIRST_NAMES[i - 1]).lastName(CLIENT_LAST_NAMES[i - 1])
                    .role(Role.CLIENT).enabled(true).emailVerified(true).build());
            imported(key, client.getId());
        }
        return new Result(catalog.salons().size(), created);
    }

    private void imported(String key, Long targetId) {
        imports.save(new ShowcaseImport(key, targetId, java.time.LocalDateTime.now()));
    }

    /** Ancien générateur volumineux, conservé pour vérifier les scénarios historiques.
     * Le démarrage utilise uniquement seedShowcase().
     */
    @Transactional(rollbackFor = Exception.class)
    public Result seed(int cityLimit) throws IOException {
        List<City> catalog = cities();
        if (cityLimit < 1 || cityLimit > catalog.size()) {
            throw new IllegalArgumentException("Le nombre de villes de démonstration doit être compris entre 1 et " + catalog.size());
        }
        // Une seule empreinte : créer les comptes de démo ne nécessite pas 93 calculs BCrypt.
        String hash = passwordEncoder.encode(PASSWORD);
        List<User> clients = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            String email = "client" + i + DOMAIN;
            User client = users.findByEmail(email).orElse(null);
            if (client == null) {
                client = users.save(User.builder().email(email).password(hash)
                        .firstName(CLIENT_FIRST_NAMES[i - 1]).lastName(CLIENT_LAST_NAMES[i - 1])
                        .role(Role.CLIENT).enabled(true).emailVerified(true).build());
            } else if ("Démo".equals(client.getFirstName()) && ("client" + i).equals(client.getLastName())) {
                client.setFirstName(CLIENT_FIRST_NAMES[i - 1]); client.setLastName(CLIENT_LAST_NAMES[i - 1]);
                users.save(client);
            }
            clients.add(client);
        }
        int created = 0;
        var scenarioProfiles = new ArrayList<CreatedProfile>();
        ServiceType[] types = {ServiceType.COIFFURE, ServiceType.ONGLERIE, ServiceType.SOIN};
        for (int i = 0; i < cityLimit; i++) {
            City city = catalog.get(i);
            for (int j = 0; j < types.length; j++) {
                ServiceType type = types[j];
                String email = type.name().toLowerCase(java.util.Locale.ROOT) + "." + city.code() + DOMAIN;
                // Ne jamais écraser les modifications faites en testant les comptes de démo.
                if (users.existsByEmail(email)) {
                    // Retire uniquement l'ancien préfixe généré, sans écraser un nom personnalisé.
                    users.findByEmail(email).flatMap(u -> profiles.findByUserId(u.getId())).ifPresent(existing -> {
                        User account = existing.getUser();
                        if ("Démo".equals(account.getFirstName()) && (city.name() + " " + type.name().toLowerCase(java.util.Locale.ROOT)).equals(account.getLastName())) {
                            account.setFirstName(switch (type) { case COIFFURE -> "Camille"; case ONGLERIE -> "Lina"; case SOIN -> "Alex"; });
                            users.save(account);
                        }
                        String generated = "[DÉMO] " + switch (type) {
                            case COIFFURE -> "Coiffure"; case ONGLERIE -> "Onglerie"; case SOIN -> "Soins";
                        } + " · " + city.name();
                        if (generated.equals(existing.getBusinessName())) {
                            existing.setBusinessName(generated.substring("[DÉMO] ".length()));
                            profiles.save(existing);
                        }
                    });
                    continue;
                }
                User owner = users.save(User.builder().email(email).password(hash).firstName(new String[]{"Camille", "Lina", "Alex"}[j])
                        .lastName(city.name() + " " + type.name().toLowerCase(java.util.Locale.ROOT))
                        .role(Role.PROFESSIONAL).enabled(!(i == 1 && j == 2)).emailVerified(true).build());
                String category = switch (type) { case COIFFURE -> "Coiffure"; case ONGLERIE -> "Onglerie"; case SOIN -> "Soins"; };
                ProfessionalProfile profile = profiles.save(ProfessionalProfile.builder().user(owner)
                        .businessName(category + " · " + city.name()).city(city.name())
                        .bio("PROFIL FICTIF DE DÉMONSTRATION. Prestations et tarifs d'exemple, aucun établissement réel. "
                                + "Le point de carte correspond au centre officiel de la commune, pas à une adresse de salon.")
                        .targetGender(TargetGender.values()[(i + j) % TargetGender.values().length])
                        .validated(!(i == 0 && j == 2))
                        .latitude(j == 2 ? null : city.latitude()).longitude(j == 2 ? null : city.longitude())
                        .coordinatesPublic(j == 0).build());
                List<ServiceExample> examples = switch (type) {
                    case COIFFURE -> List.of(new ServiceExample("Coupe et brushing", "30", 45), new ServiceExample("Coloration et soin", "65", 90));
                    case ONGLERIE -> List.of(new ServiceExample("Manucure", "20", 30), new ServiceExample("Vernis semi-permanent", "38", 60));
                    case SOIN -> List.of(new ServiceExample("Soin du visage", "35", 45), new ServiceExample("Soin relaxant", "55", 60));
                };
                Prestation first = null;
                for (ServiceExample example : examples) {
                    Prestation prestation = prestations.save(Prestation.builder().professional(profile)
                            .name(example.name()).type(type).price(new BigDecimal(example.price()).add(BigDecimal.valueOf(i % 5 * 2)))
                            .durationMinutes(example.minutes()).description("Exemple de prestation — données fictives de démonstration.")
                            .active(true).build());
                    if (first == null) first = prestation;
                }
                prestations.save(Prestation.builder().professional(profile).name("Ancienne prestation — démo")
                        .type(type).price(new BigDecimal("25")).durationMinutes(45).active(false).build());
                TimeSlot next = null;
                for (int day = 1; day <= 4; day++) {
                    for (int hour : new int[]{9, 14}) {
                        TimeSlot slot = slots.save(TimeSlot.builder().professional(profile)
                                .startDateTime(LocalDate.now().plusDays(day).atTime(hour, 0)).available(true).build());
                        if (next == null) next = slot;
                    }
                }
                // Trois scénarios de compte, répartis sur trois villes, sans envoi d'email.
                if (j == 0 && i < 3) scenarioProfiles.add(new CreatedProfile(profile, first, next, i));
                created++;
            }
        }
        for (int i = 0; i < scenarioProfiles.size(); i++) {
            var scenario = scenarioProfiles.get(i);
            User client = clients.get(scenario.clientIndex());
            scenario.nextSlot().setAvailable(false);
            slots.save(scenario.nextSlot());
            appointments.save(Appointment.builder().client(client).professional(scenario.profile())
                    .prestation(scenario.prestation()).timeSlot(scenario.nextSlot())
                    .activeTimeSlotId(scenario.nextSlot().getId()).status(AppointmentStatus.CONFIRME)
                    .notes("Réservation fictive de démonstration.").build());
            TimeSlot past = slots.save(TimeSlot.builder().professional(scenario.profile())
                    .startDateTime(LocalDate.now().minusDays(2).atTime(10, 0)).available(false).build());
            Appointment completed = appointments.save(Appointment.builder().client(client).professional(scenario.profile())
                    .prestation(scenario.prestation()).timeSlot(past).activeTimeSlotId(past.getId())
                    .status(AppointmentStatus.TERMINE).notes("Rendez-vous fictif terminé.").build());
            reviews.save(Review.builder().client(client).professional(scenario.profile()).appointment(completed)
                    .rating(4 + i % 2).comment("AVIS FICTIF DE DÉMONSTRATION — exemple d'avis lié à un rendez-vous terminé.").build());
            TimeSlot cancelled = slots.save(TimeSlot.builder().professional(scenario.profile())
                    .startDateTime(LocalDate.now().minusDays(1).atTime(14, 0)).available(true).build());
            appointments.save(Appointment.builder().client(client).professional(scenario.profile())
                    .prestation(scenario.prestation()).timeSlot(cancelled).status(AppointmentStatus.ANNULE)
                    .notes("Rendez-vous fictif annulé.").build());
            favorites.save(Favorite.builder().client(client).professional(scenario.profile()).build());
        }
        return new Result(cityLimit, created);
    }
}
