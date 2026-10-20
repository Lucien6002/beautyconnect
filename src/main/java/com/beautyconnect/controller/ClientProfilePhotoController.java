package com.beautyconnect.controller;

import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.model.User;
import com.beautyconnect.repository.UserRepository;
import com.beautyconnect.security.CustomUserDetails;
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
@RequestMapping("/compte")
@RequiredArgsConstructor
public class ClientProfilePhotoController {

    private final UserRepository userRepository;
    private final String uploadDir = "uploads/user-photos";

    @PostMapping("/photo/upload")
    @Transactional
    public String uploadProfilePhoto(@RequestParam("photo") MultipartFile file,
                                      @AuthenticationPrincipal CustomUserDetails principal,
                                      RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Veuillez sélectionner une photo");
            return "redirect:/compte";
        }

        try {
            String fileName = UUID.randomUUID().toString() + getFileExtension(file.getOriginalFilename());
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            user.setProfilePhotoUrl("/uploads/user-photos/" + fileName);
            userRepository.save(user);

            redirectAttributes.addFlashAttribute("success", "Photo de profil mise à jour avec succès");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Erreur lors de l'upload de la photo");
        }

        return "redirect:/compte";
    }

    @PostMapping("/photo/delete")
    @Transactional
    public String deleteProfilePhoto(@AuthenticationPrincipal CustomUserDetails principal,
                                       RedirectAttributes redirectAttributes) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        user.setProfilePhotoUrl(null);
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("success", "Photo de profil supprimée avec succès");
        return "redirect:/compte";
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }
}
