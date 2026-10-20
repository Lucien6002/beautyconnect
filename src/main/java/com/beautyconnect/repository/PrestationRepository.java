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

    // Accueil public : ne jamais exposer le catalogue d'un compte suspendu
    // ou d'un profil non valide. Charger le profil pour le rendu sans OSIV.
    @org.springframework.data.jpa.repository.Query("""
            select p from Prestation p
            join fetch p.professional pro
            join pro.user u
            where p.active = true and pro.validated = true and u.enabled = true and p.type = :type
            order by pro.businessName, p.name, p.id
            """)
    List<Prestation> findPublicPreviewsByType(@org.springframework.data.repository.query.Param("type") com.beautyconnect.model.ServiceType type, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("""
            select p from Prestation p join fetch p.professional pro join pro.user u
            where p.active = true and pro.validated = true and u.enabled = true
            order by pro.businessName, p.name, p.id
            """)
    List<Prestation> findPublicPreviews(org.springframework.data.domain.Pageable pageable);

    // SELECT * FROM prestations WHERE professional_id = ? AND active = true
    // Utilise pour la vitrine publique : on ne montre que les prestations actives.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "photos")
    List<Prestation> findByProfessionalAndActiveTrue(ProfessionalProfile professional);

    // SELECT * FROM prestations WHERE professional_id = ?
    // Utilise dans l'espace pro (le professionnel doit voir aussi ses prestations
    // desactivees pour pouvoir les reactiver).
    List<Prestation> findByProfessional(ProfessionalProfile professional);

    // SELECT * FROM prestations WHERE professional_id = ? AND active = false
    // Prestations retirees par le pro : affichees sur la page "Prestations
    // supprimees" d'ou il peut les restaurer.
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "photos")
    List<Prestation> findByProfessionalAndActiveFalse(ProfessionalProfile professional);

    long countByProfessionalAndActiveFalse(ProfessionalProfile professional);
}
