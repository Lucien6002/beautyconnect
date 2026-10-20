package com.beautyconnect.controller;

import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.ProfessionalService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequestMapping("/pro/profil")
@RequiredArgsConstructor
public class ProfilePhotoController {

    private final ProfessionalService professionalService;
    private final String uploadDir = "uploads/profile-photos";

    @PostMapping("/photo/upload")
    @Transactional
    public String uploadProfilePhoto(@RequestParam("photo") MultipartFile file,
                                      @AuthenticationPrincipal CustomUserDetails principal,
                                      RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner une photo");
            return "redirect:/pro/profil";
        }

        try {
            String fileName = UUID.randomUUID().toString() + getFileExtension(file.getOriginalFilename());
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            String photoUrl = "/uploads/profile-photos/" + fileName;
            profile.setProfilePhotoUrl(photoUrl);
            professionalService.updateProfile(profile);
            
            System.out.println("Photo de profil uploadée : " + photoUrl);
            System.out.println("Fichier sauvegardé : " + filePath.toAbsolutePath());
            
            redirectAttributes.addFlashAttribute("success", "Photo de profil mise à jour avec succès");
        } catch (IOException e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload de la photo : " + e.getMessage());
        }

        return "redirect:/pro/profil";
    }

    @PostMapping("/photo/delete")
    @Transactional
    public String deleteProfilePhoto(@AuthenticationPrincipal CustomUserDetails principal,
                                       RedirectAttributes redirectAttributes) {
        ProfessionalProfile profile = professionalService.getProfileByUserId(principal.getId());
        profile.setProfilePhotoUrl(null);
        professionalService.updateProfile(profile, profile.getBusinessName(), profile.getBio(),
                profile.getCity(), profile.getAddress(), profile.getTargetGender());
        redirectAttributes.addFlashAttribute("success", "Photo de profil supprimée avec succès");
        return "redirect:/pro/profil";
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }
}
