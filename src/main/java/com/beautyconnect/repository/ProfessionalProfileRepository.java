package com.beautyconnect.repository;

import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.TargetGender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Acces aux donnees pour l'entite {@link ProfessionalProfile}.
 * Voir {@link UserRepository} pour l'explication generale des requetes derivees.
 */
public interface ProfessionalProfileRepository extends JpaRepository<ProfessionalProfile, Long> {

    // Retrouve le profil professionnel a partir de l'ID du User associe
    // (relation 1-1, voir ProfessionalProfile.user). Utilise pour retrouver le
    // profil du professionnel actuellement connecte.
    Optional<ProfessionalProfile> findByUserId(Long userId);

    // Profils deja valides par un administrateur : visibles publiquement.
    List<ProfessionalProfile> findByValidatedTrue();

    // Profils en attente de validation : file d'attente de l'espace admin.
    List<ProfessionalProfile> findByValidatedFalse();

    /**
     * Recherche multicritere utilisee par le moteur de recherche public :
     * - ville (correspondance partielle, insensible a la casse)
     * - sexe de la clientele visee (optionnel)
     * - type de prestation propose (optionnel)
     * Seuls les profils valides par l'administrateur sont retournes.
     *
     * Cette methode utilise @Query avec du JPQL (Java Persistence Query
     * Language) ecrit a la main, contrairement aux autres methodes de ce
     * fichier qui sont "derivees" du nom de la methode : c'est necessaire ici
     * car la requete est trop complexe (jointure + conditions optionnelles)
     * pour etre exprimee uniquement via un nom de methode.
     *
     * Le motif "(:param IS NULL OR ...)" repete pour city/gender/type permet
     * de rendre chaque critere optionnel : si l'utilisateur ne renseigne pas
     * de ville par exemple, ":city IS NULL" est vrai et la condition suivante
     * est ignoree, sans avoir a ecrire 8 requetes differentes pour chaque
     * combinaison de filtres.
     *
     * Le "CAST(:city AS string)" est necessaire avec Hibernate 7 : sans lui,
     * quand :city vaut null, Hibernate ne sait pas deduire le type SQL exact
     * du parametre et PostgreSQL le lie par defaut comme un type binaire
     * (bytea), ce qui fait echouer LOWER(...) qui n'accepte que du texte. Le
     * cast explicite force le bon type (texte) dans tous les cas.
     */
    @Query("""
            SELECT DISTINCT p FROM ProfessionalProfile p
            LEFT JOIN Prestation pr ON pr.professional = p AND pr.active = true
            WHERE p.validated = true
              AND (:city IS NULL OR LOWER(p.city) LIKE LOWER(CONCAT('%', CAST(:city AS string), '%')))
              AND (:gender IS NULL OR p.targetGender = :gender OR p.targetGender = com.beautyconnect.model.TargetGender.MIXTE)
              And (:name Is NULL OR lower(p.businessName) LiKE LOWER (CONCAT('%', CAST(:name AS string), '%')))
              AND (:type IS NULL OR pr.type = :type)
            """)
    List<ProfessionalProfile> search(
            @Param("city") String city,
            @Param("gender") TargetGender gender,
            @Param("name") String name,
            @Param("type") com.beautyconnect.model.ServiceType type);
}
