package com.beautyconnect.security;

import com.beautyconnect.model.User;
import com.beautyconnect.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Implementation du contrat UserDetailsService attendu par Spring Security :
 * "etant donne un identifiant de connexion (ici l'email), retrouve
 * l'utilisateur correspondant". Spring Security appelle automatiquement cette
 * methode a chaque tentative de connexion (POST /connexion, voir
 * SecurityConfig), sans qu'on ait besoin de l'appeler nous-meme explicitement
 * nulle part : elle est detectee et utilisee automatiquement car c'est le
 * seul bean de type UserDetailsService present dans l'application.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                // UsernameNotFoundException est une exception standard de
                // Spring Security, qu'il transforme lui-meme en "email ou
                // mot de passe incorrect" pour l'utilisateur (message
                // volontairement vague, pour ne jamais reveler si un email
                // existe ou non dans la base, par securite).
                .orElseThrow(() -> new UsernameNotFoundException("Aucun compte trouve pour : " + email));
        return new CustomUserDetails(user);
    }
}
