package com.beautyconnect;

import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.Role;
import com.beautyconnect.model.ServiceType;
import com.beautyconnect.model.TargetGender;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import com.beautyconnect.repository.TimeSlotRepository;
import com.beautyconnect.repository.UserRepository;
import com.beautyconnect.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Espace pro : retrait/restauration des prestations et refus des creneaux
 * en doublon.
 *
 * Comme AdminProfessionalValidationTests, pas de @Transactional : les vues
 * sont rendues sans session Hibernate ouverte, comme en vrai. Les donnees
 * creees ici (emails en @catalog.test) sont supprimees dans @AfterEach.
 */
@SpringBootTest
@ActiveProfiles("test")
class ProfessionalCatalogTests {

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

    @org.springframework.beans.factory.annotation.Value("${image.storage.upload-dir}")
    private String imageDirectory;

    @Autowired private com.beautyconnect.service.PhotoService photoService;
    @Autowired private com.beautyconnect.repository.PrestationPhotoRepository photoRepository;

    private MockMvc mockMvc;
    private ProfessionalProfile profile;
    private RequestPostProcessor asPro;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        profile = createProfessional("pro@catalog.test");
        // Connecte la requete avec notre CustomUserDetails, comme apres un
        // vrai login (les controleurs lisent principal.getId()).
        asPro = user(new CustomUserDetails(profile.getUser()));
    }

    @AfterEach
    void cleanUp() {
        userRepository.findAll().stream()
                .filter(u -> u.getEmail().endsWith("@catalog.test"))
                .forEach(u -> profileRepository.findByUserId(u.getId()).ifPresent(p -> {
                    timeSlotRepository.deleteAll(timeSlotRepository.findByProfessionalOrderByStartDateTimeAsc(p));
                    for (var service : prestationRepository.findByProfessional(p)) {
                        for (var photo : photoRepository.findByPrestationOrderByDisplayOrderAsc(service))
                            photoService.deletePrestationPhoto(u.getId(), service.getId(), photo.getId());
                    }
                    prestationRepository.deleteAll(prestationRepository.findByProfessional(p));
                    profileRepository.delete(p);
                    userRepository.delete(u);
                }));
    }

    @Test
    void badSecondaryImageRollsBackCreationAndUploadedFiles() throws Exception {
        long before = prestationRepository.count();
        var filesBefore = uploadedFiles();
        byte[] image = com.beautyconnect.service.storage.ImageStorageTests.photo().getBytes();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/pro/prestations")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "main.png", "image/png", image))
                .file(new org.springframework.mock.web.MockMultipartFile("secondaryPhotos", "bad.png", "image/png", "not an image".getBytes()))
                .with(asPro).with(csrf()).param("name", "Création à annuler").param("type", "COIFFURE")
                .param("price", "25").param("durationMinutes", "30"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("prestationForm"));
        assertThat(prestationRepository.count()).isEqualTo(before);
        assertThat(uploadedFiles()).isEqualTo(filesBefore);
    }

    @Test
    void editFormReplacesMainDeletesSecondaryAndAddsPhotoTogether() throws Exception {
        var service = createPrestation(profile, "Original");
        for (int i = 0; i < 3; i++) photoService.addPrestationPhoto(profile.getUser().getId(), service.getId(), image("file"));
        var old = photoRepository.findByPrestationOrderByDisplayOrderAsc(service);
        mockMvc.perform(get("/pro/prestations/{id}/modifier", service.getId()).with(asPro))
                .andExpect(status().isOk()).andExpect(content().string(containsString("deletePhotoIds")))
                .andExpect(content().string(containsString(old.getFirst().getPhotoUrl())));
        mockMvc.perform(edit(service).file(image("file")).file(image("secondaryPhotos"))
                .param("deletePhotoIds", old.get(1).getId().toString()))
                .andExpect(status().is3xxRedirection());
        var result = photoRepository.findByPrestationOrderByDisplayOrderAsc(service);
        assertThat(result).hasSize(3);
        assertThat(result.getFirst().getId()).isNotIn(old.stream().map(com.beautyconnect.model.PrestationPhoto::getId).toList());
        assertThat(result.get(1).getId()).isEqualTo(old.get(2).getId());
        assertThat(result).extracting(com.beautyconnect.model.PrestationPhoto::getDisplayOrder).containsExactly(0, 1, 2);
        assertThat(prestationRepository.findById(service.getId()).orElseThrow().getName()).isEqualTo("Modification");
        for (var removed : old.subList(0, 2))
            assertThat(java.nio.file.Files.exists(java.nio.file.Path.of(imageDirectory, removed.getPhotoUrl().substring(9)))).isFalse();
    }

    @Test
    void invalidEditRollsBackDetailsDeletedPhotosAndUploadedFiles() throws Exception {
        var service = createPrestation(profile, "Original");
        photoService.addPrestationPhoto(profile.getUser().getId(), service.getId(), image("file"));
        var original = photoRepository.findByPrestationOrderByDisplayOrderAsc(service).getFirst();
        var before = uploadedFiles();
        mockMvc.perform(edit(service).file(image("file"))
                .file(new org.springframework.mock.web.MockMultipartFile("secondaryPhotos", "bad.png", "image/png", "invalid".getBytes())))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("prestationForm"));
        assertThat(prestationRepository.findById(service.getId()).orElseThrow().getName()).isEqualTo("Original");
        assertThat(photoRepository.findByPrestationOrderByDisplayOrderAsc(service))
                .extracting(com.beautyconnect.model.PrestationPhoto::getId).containsExactly(original.getId());
        assertThat(uploadedFiles()).isEqualTo(before);
    }

    @Test
    void editCannotDeletePhotoFromAnotherServiceAndCannotExceedFive() throws Exception {
        var service = createPrestation(profile, "Original");
        var other = createPrestation(profile, "Autre");
        photoService.addPrestationPhoto(profile.getUser().getId(), other.getId(), image("file"));
        var foreign = photoRepository.findByPrestationOrderByDisplayOrderAsc(other).getFirst();
        mockMvc.perform(edit(service).param("deletePhotoIds", foreign.getId().toString())).andExpect(status().isNotFound());
        for (int i = 0; i < 5; i++) photoService.addPrestationPhoto(profile.getUser().getId(), service.getId(), image("file"));
        mockMvc.perform(edit(service).file(image("secondaryPhotos")))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("prestationForm"));
        assertThat(photoRepository.findByPrestationOrderByDisplayOrderAsc(service)).hasSize(5);
        assertThat(prestationRepository.findById(service.getId()).orElseThrow().getName()).isEqualTo("Original");
    }

    @Test
    void editCanChooseExistingMainAndThenDeleteAllPhotos() throws Exception {
        var service = createPrestation(profile, "Original");
        for (int i = 0; i < 2; i++) photoService.addPrestationPhoto(profile.getUser().getId(), service.getId(), image("file"));
        var old = photoRepository.findByPrestationOrderByDisplayOrderAsc(service);
        mockMvc.perform(edit(service).param("mainPhotoId", old.get(1).getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(photoRepository.findByPrestationOrderByDisplayOrderAsc(service).getFirst().getId()).isEqualTo(old.get(1).getId());
        mockMvc.perform(edit(service).param("deletePhotoIds", old.get(0).getId().toString(), old.get(1).getId().toString()))
                .andExpect(status().is3xxRedirection());
        assertThat(photoRepository.findByPrestationOrderByDisplayOrderAsc(service)).isEmpty();
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder edit(Prestation service) {
        var request = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/pro/prestations/{id}/modifier", service.getId());
        request.with(asPro).with(csrf()).param("name", "Modification").param("type", "COIFFURE")
                .param("price", "25").param("durationMinutes", "30");
        return request;
    }

    private org.springframework.mock.web.MockMultipartFile image(String name) throws Exception {
        return new org.springframework.mock.web.MockMultipartFile(name, "photo.png", "image/png",
                com.beautyconnect.service.storage.ImageStorageTests.photo().getBytes());
    }

    private java.util.List<String> uploadedFiles() throws java.io.IOException {
        var directory = java.nio.file.Path.of(imageDirectory);
        if (!java.nio.file.Files.isDirectory(directory)) return java.util.List.of();
        try (var files = java.nio.file.Files.list(directory)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    // ---- Prestations ----

    @Test
    void removedPrestation_disappearsFromListAndAppearsInRemovedPage() throws Exception {
        Prestation prestation = createPrestation(profile, "Barbe seule");

        mockMvc.perform(post("/pro/prestations/{id}/supprimer", prestation.getId()).with(asPro).with(csrf()))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/pro/prestations").with(asPro))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Barbe seule"))))
                .andExpect(content().string(containsString("Prestations supprimees")));

        mockMvc.perform(get("/pro/prestations/supprimees").with(asPro))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Barbe seule")))
                .andExpect(content().string(containsString("Restaurer")));
    }

    @Test
    void restorePrestation_makesItActiveAgain() throws Exception {
        Prestation prestation = createPrestation(profile, "Soin");
        prestation.setActive(false);
        prestationRepository.save(prestation);

        mockMvc.perform(post("/pro/prestations/{id}/restaurer", prestation.getId()).with(asPro).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/pro/prestations/supprimees"))
                .andExpect(flash().attributeExists("success"));

        assertThat(prestationRepository.findById(prestation.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void restoreAlreadyActivePrestation_showsError() throws Exception {
        Prestation prestation = createPrestation(profile, "Coupe");

        mockMvc.perform(post("/pro/prestations/{id}/restaurer", prestation.getId()).with(asPro).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("error", containsString("deja active")));
    }

    @Test
    void restoreOtherProfessionalsPrestation_isRefused() throws Exception {
        ProfessionalProfile other = createProfessional("other@catalog.test");
        Prestation prestation = createPrestation(other, "Pas a moi");
        prestation.setActive(false);
        prestationRepository.save(prestation);

        mockMvc.perform(post("/pro/prestations/{id}/restaurer", prestation.getId()).with(asPro).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(prestationRepository.findById(prestation.getId()).orElseThrow().isActive()).isFalse();
    }

    // ---- Creneaux ----

    @Test
    void addingSameSlotTwice_isRefusedWithMessage() throws Exception {
        String start = LocalDateTime.now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0).toString();

        mockMvc.perform(post("/pro/creneaux").with(asPro).with(csrf()).param("startDateTime", start))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/pro/creneaux").with(asPro).with(csrf()).param("startDateTime", start))
                .andExpect(status().isOk())
                .andExpect(view().name("professional/slots"))
                .andExpect(model().attributeHasFieldErrorCode("timeSlotForm", "startDateTime", "duplicate"))
                .andExpect(content().string(containsString("Vous avez deja un creneau le")));

        assertThat(timeSlotRepository.findByProfessionalOrderByStartDateTimeAsc(profile)).hasSize(1);
    }

    @Test
    void sameTimeOnAnotherDay_orAnotherProfessional_isAccepted() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(7).withHour(10).withMinute(0).withSecond(0).withNano(0);
        ProfessionalProfile other = createProfessional("other2@catalog.test");

        mockMvc.perform(post("/pro/creneaux").with(asPro).with(csrf()).param("startDateTime", start.toString()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/pro/creneaux").with(asPro).with(csrf()).param("startDateTime", start.plusDays(1).toString()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/pro/creneaux").with(user(new CustomUserDetails(other.getUser()))).with(csrf())
                        .param("startDateTime", start.toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(timeSlotRepository.findByProfessionalOrderByStartDateTimeAsc(profile)).hasSize(2);
        assertThat(timeSlotRepository.findByProfessionalOrderByStartDateTimeAsc(other)).hasSize(1);
    }

    private ProfessionalProfile createProfessional(String email) {
        User user = userRepository.save(User.builder()
                .email(email)
                .password("{noop}secret")
                .firstName("Prenom")
                .lastName("Nom")
                .role(Role.PROFESSIONAL)
                .enabled(true)
                .build());
        return profileRepository.save(ProfessionalProfile.builder()
                .user(user)
                .businessName("Salon " + email)
                .city("Paris")
                .targetGender(TargetGender.MIXTE)
                .validated(true)
                .build());
    }

    private Prestation createPrestation(ProfessionalProfile owner, String name) {
        return prestationRepository.save(Prestation.builder()
                .professional(owner)
                .name(name)
                .type(ServiceType.COIFFURE)
                .price(new BigDecimal("20.00"))
                .durationMinutes(30)
                .active(true)
                .build());
    }
}
