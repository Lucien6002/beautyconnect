package com.beautyconnect.controller;
import com.beautyconnect.security.CustomUserDetails;
import com.beautyconnect.service.PhotoService;
import com.beautyconnect.service.storage.ImageUploadException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
@Controller
@RequestMapping("/compte")
@RequiredArgsConstructor
public class ClientProfilePhotoController {
    private final PhotoService photos;
    @PostMapping("/photo/upload")
    public String upload(@RequestParam("photo") MultipartFile file, @AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes flash) {
        try {
            photos.profilePhoto(principal.getId(), false, file);
            flash.addFlashAttribute("success", "Votre photo a été mise à jour.");
        } catch (ImageUploadException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/compte";
    }
    @PostMapping("/photo/delete")
    public String delete(@AuthenticationPrincipal CustomUserDetails principal, RedirectAttributes flash) {
        photos.profilePhoto(principal.getId(), false, null);
        flash.addFlashAttribute("success", "Votre photo a été supprimée.");
        return "redirect:/compte";
    }
}
