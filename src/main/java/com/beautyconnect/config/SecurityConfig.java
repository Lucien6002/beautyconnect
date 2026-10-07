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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

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
            // Une autorité supplémentaire (par exemple FACTOR_PASSWORD) ne
            // doit jamais écraser la destination choisie pour le rôle.
            var authorities = authentication.getAuthorities().stream()
                    .map(org.springframework.security.core.GrantedAuthority::getAuthority).toList();
            String redirect = authorities.contains("ROLE_ADMIN") ? "/admin/tableau-bord"
                    : authorities.contains("ROLE_PROFESSIONAL") ? "/pro/tableau-bord"
                    : authorities.contains("ROLE_CLIENT") ? "/client/tableau-bord" : "/";
            response.sendRedirect(redirect);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler,
                                                   SessionRegistry sessionRegistry) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/accueil", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/recherche", "/recherche/**", "/itineraire/**").permitAll()
                        .requestMatchers("/professionnels/**").permitAll()
                        // On autorise /activer-compte pour que le lien de l'email fonctionne sans être connecté
                        .requestMatchers("/inscription/**", "/connexion", "/activer-compte", "/erreur/**", "/error").permitAll()
                        .requestMatchers("/client/**").hasRole("CLIENT")
                        .requestMatchers("/rendez-vous/**", "/avis/**", "/signalements/**").hasRole("CLIENT")
                        .requestMatchers("/pro/**").hasRole("PROFESSIONAL")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/connexion")
                        .loginProcessingUrl("/connexion")
                        .successHandler(successHandler) // Utilise directement le bon handler
                        .failureUrl("/connexion?erreur")
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