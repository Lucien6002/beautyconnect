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
        record Example(String email, String firstName, String business, String cityCode, ServiceType type, String password) {}
        var examples = List.of(
                new Example("sarah@beautyconnect.local", "Sarah", "Sarah Coiffure & Ongles", "75056", ServiceType.COIFFURE, "Pro12345!"),
                new Example("chloe@beautyconnect.local", "Chloé", "Chloé Coiffure Lyon", "69123", ServiceType.COIFFURE, "Pro12345!"),
                new Example("coiffure.59350" + DOMAIN, "Camille", "Coiffure · Lille", "59350", ServiceType.COIFFURE, PASSWORD),
                new Example("onglerie.44109" + DOMAIN, "Lina", "Onglerie · Nantes", "44109", ServiceType.ONGLERIE, PASSWORD),
                new Example("onglerie.33063" + DOMAIN, "Inès", "Onglerie · Bordeaux", "33063", ServiceType.ONGLERIE, PASSWORD),
                new Example("soin.13055" + DOMAIN, "Alex", "Maquillage · Marseille", "13055", ServiceType.SOIN, PASSWORD));
        int created = 0;
        for (var example : examples) {
            var owner = users.findByEmail(example.email()).orElse(null);
            // Une réexécution ne remplace ni mot de passe, ni profil, ni catalogue personnalisé.
            if (owner != null) continue;
            var city = cities().stream().filter(c -> c.code().equals(example.cityCode())).findFirst().orElseThrow();
            owner = users.save(User.builder().email(example.email()).password(passwordEncoder.encode(example.password()))
                    .firstName(example.firstName()).lastName(city.name()).role(Role.PROFESSIONAL)
                    .enabled(true).emailVerified(true).build());
            var profile = profiles.save(ProfessionalProfile.builder().user(owner).businessName(example.business())
                    .city(city.name()).targetGender(TargetGender.MIXTE).validated(true)
                    .latitude(city.latitude()).longitude(city.longitude()).coordinatesPublic(true)
                    .bio("Établissement fictif de présentation. Coordonnées au centre de la commune ; prestations et tarifs simulés.").build());
            List<ServiceExample> catalog = switch (example.type()) {
                case COIFFURE -> List.of(new ServiceExample("Coupe et brushing", "30", 45), new ServiceExample("Coloration et soin", "65", 90));
                case ONGLERIE -> List.of(new ServiceExample("Manucure", "20", 30), new ServiceExample("Vernis semi-permanent", "38", 60));
                case SOIN -> List.of(new ServiceExample("Maquillage de jour", "35", 45), new ServiceExample("Maquillage de soirée", "55", 60));
            };
            for (var service : catalog) prestations.save(Prestation.builder().professional(profile)
                    .name(service.name()).type(example.type()).price(new BigDecimal(service.price()))
                    .durationMinutes(service.minutes()).description("Prestation fictive de présentation.").active(true).build());
            for (int day = 1; day <= 4; day++) for (int hour : new int[]{9, 14})
                slots.save(TimeSlot.builder().professional(profile).startDateTime(LocalDate.now().plusDays(day).atTime(hour, 0)).available(true).build());
            created++;
        }
        var hash = passwordEncoder.encode(PASSWORD);
        for (int i = 1; i <= 3; i++) {
            String email = "client" + i + DOMAIN;
            if (!users.existsByEmail(email)) users.save(User.builder().email(email).password(hash)
                    .firstName(CLIENT_FIRST_NAMES[i - 1]).lastName(CLIENT_LAST_NAMES[i - 1])
                    .role(Role.CLIENT).enabled(true).emailVerified(true).build());
        }
        return new Result(6, created);
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
