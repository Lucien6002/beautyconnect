package com.beautyconnect.security;

import com.beautyconnect.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Adapte l'entite {@link User} au contrat Spring Security.
 * Les comptes desactives par l'administrateur ne peuvent plus se connecter.
 *
 * Spring Security ne connait rien de notre entite User "maison" : il travaille
 * uniquement avec l'interface standard UserDetails. Cette classe est donc un
 * "adaptateur" (design pattern Adapter) qui enveloppe un User et repond a
 * toutes les questions que Spring Security se pose (mot de passe ? role(s) ?
 * compte actif ?) en allant chercher la reponse dans l'objet User sous-jacent.
 * C'est cette classe qu'on retrouve injectee via @AuthenticationPrincipal
 * dans les controleurs (voir par exemple ClientDashboardController).
 */
@Getter
public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    // Raccourcis pratiques pour eviter d'ecrire user.getId() /
    // user.getFullName() partout ou on recoit un CustomUserDetails.
    public Long getId() {
        return user.getId();
    }

    public String getFullName() {
        return user.getFullName();
    }

    // Traduit notre enum Role (CLIENT/PROFESSIONAL/ADMIN) au format attendu
    // par Spring Security : une chaine prefixee "ROLE_" (ex: "ROLE_CLIENT").
    // C'est ce prefixe qui permet a .hasRole("CLIENT") dans SecurityConfig de
    // fonctionner (hasRole ajoute lui-meme le prefixe "ROLE_" en interne).
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
    }

    @Override
    public String getPassword() {
        return user.getPassword(); // deja hache (BCrypt), Spring Security compare le hash, jamais le mot de passe en clair
    }

    // Spring Security appelle ce champ "username" par convention historique,
    // mais dans cette application c'est l'EMAIL qui sert d'identifiant de connexion.
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; // pas de notion d'expiration de compte dans ce projet
    }

    // Reutilise le champ "enabled" de User : un compte desactive par un
    // admin (voir AdminService.setUserEnabled) est traite comme "verrouille"
    // par Spring Security, qui refusera alors toute tentative de connexion.
    @Override
    public boolean isAccountNonLocked() {
        return user.isEnabled();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true; // pas d'expiration de mot de passe geree dans ce projet
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabled();
    }

    // equals/hashCode bases sur l'email : le SessionRegistry (voir
    // SecurityConfig) range les sessions par principal dans une Map. Sans
    // cela, deux connexions du meme utilisateur seraient vues comme deux
    // principals differents et l'admin ne pourrait pas les retrouver.
    @Override
    public boolean equals(Object o) {
        return o instanceof CustomUserDetails other && getUsername().equals(other.getUsername());
    }

    @Override
    public int hashCode() {
        return getUsername().hashCode();
    }
}
