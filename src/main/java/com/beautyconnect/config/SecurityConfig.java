package com.beautyconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

/**
 * Authentification par session (formulaire de connexion classique), avec
 * redirection post-connexion selon le role : Client / Professionnel / Administrateur.
 * Le {@code UserDetailsService} (voir {@code CustomUserDetailsService}) et le
 * {@code PasswordEncoder} ci-dessous sont detectes automatiquement par
 * Spring Security pour construire le provider d'authentification par defaut.
 *
 * @Configuration + @EnableWebSecurity : indique a Spring Boot de remplacer sa
 * configuration de securite automatique par defaut par celle definie
 * explicitement dans cette classe (via les @Bean ci-dessous).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Bean utilise a deux endroits : pour hacher le mot de passe a
    // l'inscription (voir UserService) et, en coulisses, par Spring Security
    // lui-meme pour COMPARER le mot de passe saisi a la connexion avec le
    // hash stocke en base (BCrypt sait verifier un hash sans jamais avoir
    // besoin de "dehacher" le mot de passe original, ce qui est impossible par design).
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Bean technique requis par Spring Security pour orchestrer le processus
    // d'authentification (verification email + mot de passe). On se contente
    // de recuperer celui fourni automatiquement par Spring Boot.
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // Definit ce qui se passe juste apres une connexion reussie : au lieu de
    // toujours rediriger vers la meme page, on choisit la destination selon
    // le role de l'utilisateur connecte (tableau de bord adapte a chaque profil).
    // La lambda ci-dessous implemente l'interface fonctionnelle
    // AuthenticationSuccessHandler (une methode a 3 parametres : request, response, authentication).
    // Registre des sessions ouvertes, par utilisateur connecte. Permet a
    // l'admin de deconnecter immediatement un compte qu'il desactive (voir
    // AdminService.setUserEnabled) : sans lui, un utilisateur deja connecte
    // garderait l'acces jusqu'a l'expiration naturelle de sa session.
    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    // Previent le SessionRegistry quand une session HTTP est detruite
    // (deconnexion, expiration), pour qu'il ne garde pas d'entrees obsoletes.
    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (request, response, authentication) -> {
            String redirect = "/";
            // Un utilisateur n'a normalement qu'un seul role ici, mais
            // getAuthorities() renvoie toujours une COLLECTION par contrat
            // Spring Security (un utilisateur pourrait en theorie cumuler
            // plusieurs roles), d'ou la boucle.
            for (var authority : authentication.getAuthorities()) {
                switch (authority.getAuthority()) {
                    case "ROLE_CLIENT" -> redirect = "/client/tableau-bord";
                    case "ROLE_PROFESSIONAL" -> redirect = "/pro/tableau-bord";
                    case "ROLE_ADMIN" -> redirect = "/admin/tableau-bord";
                    default -> redirect = "/";
                }
            }
            response.sendRedirect(redirect);
        };
    }

    // Coeur de la configuration : definit, URL par URL, qui a le droit
    // d'acceder a quoi. C'est LE bean central de Spring Security.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler,
                                                   SessionRegistry sessionRegistry) throws Exception {
        http
                // authorizeHttpRequests : les regles sont evaluees DANS L'ORDRE
                // et la PREMIERE regle qui correspond a l'URL demandee est
                // appliquee (les suivantes sont ignorees pour cette requete).
                // Il faut donc toujours placer les regles les plus specifiques
                // avant les regles plus generales (anyRequest().authenticated()
                // doit rester en dernier, sinon elle "attraperait" tout avant
                // que les regles permitAll() suivantes ne soient examinees).
                .authorizeHttpRequests(auth -> auth
                        // Pages et ressources statiques accessibles sans etre connecte.
                        .requestMatchers("/", "/accueil", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/recherche", "/recherche/**").permitAll()
                        .requestMatchers("/professionnels/**").permitAll()
                        // "/error" (page d'erreur generique de Spring Boot) doit
                        // rester publique : sinon, si une erreur survient sur une
                        // page deja publique pour un visiteur non connecte,
                        // Spring Security bloquerait l'affichage de la page
                        // d'erreur elle-meme et redirigerait en boucle vers /connexion.
                        .requestMatchers("/inscription/**", "/connexion", "/erreur/**", "/error").permitAll()
                        // A partir d'ici, chaque zone necessite le role exact indique.
                        .requestMatchers("/client/**").hasRole("CLIENT")
                        .requestMatchers("/rendez-vous/**", "/avis/**", "/signalements/**").hasRole("CLIENT")
                        .requestMatchers("/pro/**").hasRole("PROFESSIONAL")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Regle "filet de securite" : toute URL non explicitement
                        // listee ci-dessus necessite au minimum d'etre connecte.
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/connexion") // URL du formulaire (GET, voir AuthController)
                        .loginProcessingUrl("/connexion") // URL sur laquelle le formulaire est soumis (POST, interceptee directement par Spring Security, aucun controleur ne la gere)
                        .successHandler(successHandler) // redirection selon le role, voir plus haut
                        .failureUrl("/connexion?erreur") // en cas de mauvais email/mot de passe
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/deconnexion")
                        .logoutSuccessUrl("/?deconnecte")
                        .permitAll())
                // Page affichee quand un utilisateur CONNECTE tente d'acceder a
                // une zone reservee a un autre role (ex: un client qui essaie
                // d'ouvrir /admin/tableau-bord) : diese "403 Forbidden".
                .exceptionHandling(ex -> ex.accessDeniedPage("/erreur/403"))
                // maximumSessions(-1) : pas de limite du nombre de sessions par
                // utilisateur ; on active ce mecanisme uniquement pour
                // enregistrer les sessions dans le SessionRegistry. Une session
                // expiree par l'admin redirige vers la page de connexion.
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry)
                        .expiredUrl("/connexion?desactive"));
                // CSRF reste actif (comportement par defaut) ; les formulaires Thymeleaf
                // incluent automatiquement le jeton car ils utilisent th:action.

        return http.build();
    }
}
