package com.beautyconnect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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

    @Bean
    public AuthenticationSuccessHandler roleBasedSuccessHandler() {
        return (request, response, authentication) -> {
            String redirect = "/";
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

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationSuccessHandler successHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/accueil", "/css/**", "/js/**", "/images/**", "/webjars/**").permitAll()
                        .requestMatchers("/recherche", "/recherche/**").permitAll()
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
                .exceptionHandling(ex -> ex.accessDeniedPage("/erreur/403"));

        return http.build();
    }
}