package com.beautyconnect;

import com.beautyconnect.model.*;
import com.beautyconnect.repository.*;
import com.beautyconnect.service.PhotoService;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.exception.*;
import com.beautyconnect.service.storage.ImageStorageTests;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PhotoIntegrationTests {
    @Autowired UserRepository users;
    @Autowired ProfessionalProfileRepository profiles;
    @Autowired PrestationRepository prestations;
    @Autowired PrestationPhotoRepository images;
    @Autowired PhotoService photos;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired WebApplicationContext context;
    User owner, other; Prestation prestation; MockMvc mvc;
    @BeforeEach void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = users.save(User.builder().email("owner@photos.test").password("hash").firstName("Alice").lastName("Photo").role(Role.PROFESSIONAL).enabled(true).emailVerified(true).build());
        other = users.save(User.builder().email("other@photos.test").password("hash").firstName("Autre").lastName("Photo").role(Role.PROFESSIONAL).enabled(true).emailVerified(true).build());
        var profile = profiles.save(ProfessionalProfile.builder().user(owner).businessName("Atelier test").city("Paris").targetGender(TargetGender.MIXTE).validated(true).build());
        prestation = prestations.save(Prestation.builder().professional(profile).name("Coupe test").type(ServiceType.COIFFURE).durationMinutes(30).price(BigDecimal.TEN).build());
    }
    @Test void professionalCoverAndPersonalAvatarUploadSeparatelyAndRejectInvalidReplacement() throws Exception {
        mvc.perform(multipart("/pro/profil/photo/upload").file(ImageStorageTests.webp())
                .with(user(new CustomUserDetails(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        String cover = profiles.findByUserId(owner.getId()).orElseThrow().getProfilePhotoUrl();
        assertThat(cover).endsWith(".png");
        assertThat(users.findById(owner.getId()).orElseThrow().getProfilePhotoUrl()).isNull();
        mvc.perform(multipart("/compte/photo/upload").file(ImageStorageTests.webp())
                .with(user(new CustomUserDetails(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        entityManager.flush(); entityManager.clear();
        String avatar = users.findById(owner.getId()).orElseThrow().getProfilePhotoUrl();
        assertThat(avatar).endsWith(".png").isNotEqualTo(cover);
        mvc.perform(multipart("/compte/photo/upload")
                .file(new org.springframework.mock.web.MockMultipartFile("photo", "faux.webp", "image/webp", "invalid".getBytes()))
                .with(user(new CustomUserDetails(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("error"));
        assertThat(users.findById(owner.getId()).orElseThrow().getProfilePhotoUrl()).isEqualTo(avatar);
        assertThat(profiles.findByUserId(owner.getId()).orElseThrow().getProfilePhotoUrl()).isEqualTo(cover);
    }

    @Test void uploadAndReadGalleryThroughActualMvcRoutes() throws Exception {
        mvc.perform(multipart("/pro/prestations/{id}/photos/upload", prestation.getId()).file(ImageStorageTests.photo()).with(user(new CustomUserDetails(owner))).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("success"));
        assertThat(images.countByPrestation(prestation)).isEqualTo(1);
        mvc.perform(get("/pro/prestations/{id}/photos", prestation.getId()).with(user(new CustomUserDetails(owner))))
                .andExpect(status().isOk());
        mvc.perform(get("/professionnels/{id}", prestation.getProfessional().getId())).andExpect(status().isOk());
    }
    @Test void blocksOtherProfessionalAndMismatchedPhotoRoute() throws Exception {
        assertThatThrownBy(() -> photos.addPrestationPhoto(other.getId(), prestation.getId(), ImageStorageTests.photo())).isInstanceOf(ResourceNotFoundException.class);
        // Exceptions transactionnelles ne sont pas suivies d'autres écritures dans ce test.
    }
    @Test void rejectsPhotoFromAnotherPrestationEvenForSameOwner() throws Exception {
        var second = prestations.save(Prestation.builder().professional(prestation.getProfessional()).name("Autre").type(ServiceType.COIFFURE).durationMinutes(30).price(BigDecimal.TEN).build());
        photos.addPrestationPhoto(owner.getId(), prestation.getId(), ImageStorageTests.photo());
        Long photoId = images.findByPrestationOrderByDisplayOrderAsc(prestation).getFirst().getId();
        assertThatThrownBy(() -> photos.deletePrestationPhoto(owner.getId(), second.getId(), photoId)).isInstanceOf(ResourceNotFoundException.class);
    }
    @Test void createsMainAndSecondaryPhotosFromTheSameForm() throws Exception {
        byte[] bytes = ImageStorageTests.photo().getBytes();
        mvc.perform(multipart("/pro/prestations")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "main.png", "image/png", bytes))
                .file(new org.springframework.mock.web.MockMultipartFile("secondaryPhotos", "detail1.png", "image/png", bytes))
                .file(new org.springframework.mock.web.MockMultipartFile("secondaryPhotos", "detail2.png", "image/png", bytes))
                .with(user(new CustomUserDetails(owner))).with(csrf())
                .param("name", "Avec trois photos").param("type", "COIFFURE").param("price", "25").param("durationMinutes", "30"))
                .andExpect(status().is3xxRedirection());
        var created = prestations.findByProfessional(prestation.getProfessional()).stream()
                .filter(p -> p.getName().equals("Avec trois photos")).findFirst().orElseThrow();
        assertThat(images.findByPrestationOrderByDisplayOrderAsc(created)).hasSize(3)
                .extracting(PrestationPhoto::getDisplayOrder).containsExactly(0, 1, 2);
    }

    @Test void secondaryPhotosRequireAMainPhoto() throws Exception {
        long before = prestations.count();
        mvc.perform(multipart("/pro/prestations")
                .file(new org.springframework.mock.web.MockMultipartFile("secondaryPhotos", "detail.png", "image/png", ImageStorageTests.photo().getBytes()))
                .with(user(new CustomUserDetails(owner))).with(csrf())
                .param("name", "Sans principale").param("type", "COIFFURE").param("price", "25").param("durationMinutes", "30"))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("prestationForm"));
        assertThat(prestations.count()).isEqualTo(before);
    }

    @Test void choosesAnExistingSecondaryPhotoAsMain() throws Exception {
        photos.addPrestationPhoto(owner.getId(), prestation.getId(), ImageStorageTests.photo());
        photos.addPrestationPhoto(owner.getId(), prestation.getId(), ImageStorageTests.photo());
        var previous = images.findByPrestationOrderByDisplayOrderAsc(prestation);
        Long selected = previous.getLast().getId();
        photos.chooseMainPhoto(owner.getId(), prestation.getId(), selected);
        entityManager.flush(); entityManager.clear();
        assertThat(photos.ownedPrestation(owner.getId(), prestation.getId()).getPhotos().getFirst().getId()).isEqualTo(selected);
    }

    @Test void deletionIsPersistedEvenWhenParentGalleryWasAlreadyLoaded() throws Exception {
        photos.addPrestationPhoto(owner.getId(), prestation.getId(), ImageStorageTests.photo());
        entityManager.flush(); entityManager.clear();
        var gallery = photos.ownedPrestation(owner.getId(), prestation.getId());
        Long photoId = gallery.getPhotos().getFirst().getId();
        photos.deletePrestationPhoto(owner.getId(), prestation.getId(), photoId);
        entityManager.flush(); entityManager.clear();
        assertThat(images.findById(photoId)).isEmpty();
        assertThat(photos.ownedPrestation(owner.getId(), prestation.getId()).getPhotos()).isEmpty();
    }

    @Test void removingSharedGalleryPhotoAndCoverOnlyRemovesLocalReferences() {
        var profile = prestation.getProfessional();
        profile.setProfilePhotoUrl("https://res.cloudinary.com/shared-cover.jpg");
        profile.setProfilePhotoPublicId("shared:salon:cover");
        prestation.setPhotos(new java.util.ArrayList<>());
        var image = images.save(PrestationPhoto.builder().prestation(prestation)
                .photoUrl("https://res.cloudinary.com/shared-offer.jpg").publicId("shared:offer:0").displayOrder(0).build());
        prestation.getPhotos().add(image);
        entityManager.flush(); entityManager.clear();
        int callbacks = org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations().size();
        photos.deletePrestationPhoto(owner.getId(), prestation.getId(), image.getId());
        photos.profilePhoto(owner.getId(), true, null);
        assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()).hasSize(callbacks);
        entityManager.flush(); entityManager.clear();
        assertThat(images.findById(image.getId())).isEmpty();
        assertThat(profiles.findById(profile.getId()).orElseThrow().getProfilePhotoUrl()).isNull();
    }

    @Test void enforcesFivePhotoLimit() throws Exception {
        for (int i = 0; i < 5; i++) photos.addPrestationPhoto(owner.getId(), prestation.getId(), ImageStorageTests.photo());
        assertThatThrownBy(() -> photos.addPrestationPhoto(owner.getId(), prestation.getId(), ImageStorageTests.photo())).isInstanceOf(IllegalOperationException.class);
    }
    @Test void editionIsValidatedAndProtectedByOwner() throws Exception {
        mvc.perform(post("/pro/prestations/{id}/modifier", prestation.getId()).with(user(new CustomUserDetails(owner))).with(csrf())
                .param("name", "Coupe actualisée").param("type", "COIFFURE").param("price", "42.50").param("durationMinutes", "45"))
                .andExpect(status().is3xxRedirection());
        assertThat(prestations.findById(prestation.getId()).orElseThrow().getName()).isEqualTo("Coupe actualisée");
        mvc.perform(get("/pro/prestations/{id}/modifier", prestation.getId()).with(user(new CustomUserDetails(other)))).andExpect(status().isNotFound());
    }
}
