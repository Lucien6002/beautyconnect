package com.beautyconnect.controller;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.PhotoService;
import com.beautyconnect.exception.IllegalOperationException;
import com.beautyconnect.service.storage.ImageUploadException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
@RequestMapping("/pro/prestations")
@RequiredArgsConstructor
public class PrestationPhotoController {
    private final PhotoService photos;
    @GetMapping("/{id}/photos")
    public String manage(@PathVariable("id") Long id, @AuthenticationPrincipal CustomUserDetails principal, Model model) {
        model.addAttribute("prestation", photos.ownedPrestation(principal.getId(), id));
        return "professional/prestation-photos";
    }
    @PostMapping("/{id}/photos/upload")
    public String upload(@PathVariable("id") Long id, @RequestParam("photo") MultipartFile file,
            @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes flash) {
        try {
            photos.addPrestationPhoto(principal.getId(), id, file);
            flash.addFlashAttribute("success", "La photo est visible sur votre vitrine.");
        } catch (ImageUploadException | IllegalOperationException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/pro/prestations/" + id + "/photos";
    }
    @PostMapping("/{id}/photos/{photoId}/delete")
    public String delete(@PathVariable("id") Long id, @PathVariable("photoId") Long photoId,
            @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes flash) {
        photos.deletePrestationPhoto(principal.getId(), id, photoId);
        flash.addFlashAttribute("success", "La photo a été supprimée.");
        return "redirect:/pro/prestations/" + id + "/photos";
    }
}
