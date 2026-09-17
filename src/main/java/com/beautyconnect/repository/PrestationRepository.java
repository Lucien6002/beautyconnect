package com.beautyconnect.repository;

import com.beautyconnect.model.Prestation;
import com.beautyconnect.model.ProfessionalProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Acces aux donnees pour l'entite {@link Prestation}.
 * Voir {@link UserRepository} pour l'explication generale du fonctionnement
 * de Spring Data JPA et des requetes derivees du nom de methode.
 */
public interface PrestationRepository extends JpaRepository<Prestation, Long> {

    // SELECT * FROM prestations WHERE professional_id = ? AND active = true
    // Utilise pour la vitrine publique : on ne montre que les prestations actives.
    List<Prestation> findByProfessionalAndActiveTrue(ProfessionalProfile professional);

    // SELECT * FROM prestations WHERE professional_id = ?
    // Utilise dans l'espace pro (le professionnel doit voir aussi ses prestations
    // desactivees pour pouvoir les reactiver).
    List<Prestation> findByProfessional(ProfessionalProfile professional);
}
