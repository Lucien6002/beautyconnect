package com.beautyconnect.repository;

import com.beautyconnect.model.Favorite;
import com.beautyconnect.model.ProfessionalProfile;
import com.beautyconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    // JOIN FETCH : la page des favoris affiche les infos du professionnel hors
    // transaction (open-in-view=false). Les professionnels desactives ou non
    // valides ne sont plus listes.
    @Query("SELECT f FROM Favorite f JOIN FETCH f.professional p JOIN p.user u "
            + "WHERE f.client = :client AND u.enabled = true AND p.validated = true "
            + "ORDER BY f.createdAt DESC")
    List<Favorite> findVisibleByClient(@Param("client") User client);

    boolean existsByClientAndProfessional(User client, ProfessionalProfile professional);

    void deleteByClient(User client);

    void deleteByClientAndProfessional(User client, ProfessionalProfile professional);
}
