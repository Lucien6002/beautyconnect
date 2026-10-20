package com.beautyconnect.controller;

import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.PrestationPhoto;
import com.beautyconnect.repository.PrestationPhotoRepository;
import com.beautyconnect.repository.PrestationRepository;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.ProfessionalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequestMapping("/pro/prestations")
@RequiredArgsConstructor
public class PrestationPhotoController {

    private final PrestationPhotoRepository photoRepository;
    private final PrestationRepository prestationRepository;
    private final ProfessionalService professionalService;
    private final String uploadDir = "uploads/prestation-photos";

    @GetMapping("/{id}/photos")
    public String managePhotos(@PathVariable("id") Long id,
                               @AuthenticationPrincipal CustomUserDetails principal,
                               Model model) {
        var profile = professionalService.getProfileByUserId(principal.getId());
        Prestation prestation = prestationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable"));

        if (!prestation.getProfessional().getId().equals(profile.getId())) {
            throw new IllegalOperationException("Cette prestation ne vous appartient pas");
        }

        var photos = photoRepository.findByPrestationOrderByDisplayOrderAsc(prestation);
        prestation.setPhotos(photos);
        model.addAttribute("prestation", prestation);
        return "professional/prestation-photos";
    }

    @PostMapping("/{id}/photos/upload")
    @Transactional
    public String uploadPhoto(@PathVariable("id") Long id,
                               @RequestParam("photo") MultipartFile file,
                               @AuthenticationPrincipal CustomUserDetails principal,
                               RedirectAttributes redirectAttributes) {
        var profile = professionalService.getProfileByUserId(principal.getId());
        Prestation prestation = prestationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Prestation introuvable"));

        if (!prestation.getProfessional().getId().equals(profile.getId())) {
            throw new IllegalOperationException("Cette prestation ne vous appartient pas");
        }

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner une photo");
            return "redirect:/pro/prestations";
        }

        long currentCount = photoRepository.countByPrestation(prestation);
        if (currentCount >= 5) {
            redirectAttributes.addFlashAttribute("error", "Maximum 5 photos par prestation");
            return "redirect:/pro/prestations";
        }

        try {
            String fileName = UUID.randomUUID().toString() + getFileExtension(file.getOriginalFilename());
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            int nextOrder = (int) currentCount;
            PrestationPhoto photo = PrestationPhoto.builder()
                    .prestation(prestation)
                    .photoUrl("/uploads/prestation-photos/" + fileName)
                    .displayOrder(nextOrder)
                    .build();
            photoRepository.save(photo);

            redirectAttributes.addFlashAttribute("success", "Photo ajoutée avec succès");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload de la photo");
        }

        return "redirect:/pro/prestations";
    }

    @PostMapping("/{id}/photos/{photoId}/delete")
    @Transactional
    public String deletePhoto(@PathVariable("id") Long id,
                               @PathVariable("photoId") Long photoId,
                               @AuthenticationPrincipal CustomUserDetails principal,
                               RedirectAttributes redirectAttributes) {
        var profile = professionalService.getProfileByUserId(principal.getId());
        PrestationPhoto photo = photoRepository.findById(photoId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo introuvable"));

        if (!photo.getPrestation().getProfessional().getId().equals(profile.getId())) {
            throw new IllegalOperationException("Cette photo ne vous appartient pas");
        }

        photoRepository.delete(photo);
        redirectAttributes.addFlashAttribute("success", "Photo supprimée avec succès");
        return "redirect:/pro/prestations";
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }
}
