package com.beautyconnect.service;

import com.beautyconnect.dto.ProfessionalLocationForm;
import com.beautyconnect.exception.ResourceNotFoundException;
import com.beautyconnect.repository.ProfessionalProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfessionalLocationService {
    private final ProfessionalProfileRepository profiles;
    @Transactional
    public void update(Long userId, ProfessionalLocationForm form) {
        if (!form.isPositionValid()) throw new IllegalArgumentException("Coordonnées invalides");
        var profile = profiles.findByUserId(userId).orElseThrow(() -> new ResourceNotFoundException("Profil introuvable"));
        profile.setLatitude(form.getLatitude()); profile.setLongitude(form.getLongitude());
        profile.setCoordinatesPublic(form.isCoordinatesPublic());
    }
}
