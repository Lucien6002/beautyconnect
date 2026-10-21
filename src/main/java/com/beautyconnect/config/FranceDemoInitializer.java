package com.beautyconnect.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Import automatique en développement, désactivable et toujours exclu du profil prod. */
@Component
@org.springframework.core.annotation.Order(0)
@Profile("dev & !prod")
@ConditionalOnProperty(name = "app.demo.france.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FranceDemoInitializer implements CommandLineRunner {
    private final FranceDemoData data;

    @Override
    public void run(String... args) throws Exception {
        var result = data.seedShowcase();
        log.info("Présentation France : {} villes, {} comptes créés (six établissements au total sur une base vide). Les comptes existants sont conservés.",
                result.cities(), result.professionalsCreated());
    }
}
