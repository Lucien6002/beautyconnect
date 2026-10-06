package com.beautyconnect.repository;

import com.beautyconnect.model.Role;
import com.beautyconnect.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository = couche d'acces aux donnees pour l'entite {@link User}.
 *
 * En etendant {@link JpaRepository}, cette interface obtient GRATUITEMENT
 * (sans ecrire une seule ligne de code) toutes les operations CRUD de base :
 * save(), findById(), findAll(), deleteById(), count(), etc. Spring Data JPA
 * genere lui-meme une implementation a l'execution.
 *
 * Les methodes ci-dessous sont des "requetes derivees" : Spring Data JPA lit
 * le NOM de la methode et en deduit automatiquement la requete SQL a executer,
 * sans qu'on ait besoin d'ecrire de SQL ou de JPQL. C'est une convention de
 * nommage a respecter scrupuleusement (findBy + nom du champ + operateur).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmailForUpdate(@org.springframework.data.repository.query.Param("email") String email);

    // Equivaut a : SELECT * FROM users WHERE email = ?
    // Optional<User> : peut ne rien retourner (aucun utilisateur avec cet email)
    // sans avoir a manipuler une valeur "null" dangereuse a la main.
    Optional<User> findByEmail(String email);

    // Equivaut a : SELECT EXISTS(SELECT 1 FROM users WHERE email = ?)
    // Plus efficace qu'un findByEmail(...).isPresent() car ne charge pas
    // l'utilisateur entier, juste un booleen.
    boolean existsByEmail(String email);

    // Equivaut a : SELECT COUNT(*) FROM users WHERE role = ?
    // Utilise par AdminService pour les statistiques du tableau de bord.
    long countByRole(Role role);
}
