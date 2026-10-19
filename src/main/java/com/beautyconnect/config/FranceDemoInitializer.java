package com.beautyconnect.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Activation explicite en développement, jamais sous le profil prod. */
@Component
@Profile("dev & !prod")
@ConditionalOnProperty(name = "app.demo.france.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class FranceDemoInitializer implements CommandLineRunner {
    private final FranceDemoData data;
    @Value("${app.demo.france.city-limit:30}")
    private int cityLimit;

    @Override
    public void run(String... args) throws Exception {
        var result = data.seed(cityLimit);
        log.info("Démonstration France : {} villes, {} professionnels fictifs créés. Les comptes existants sont conservés.",
                result.cities(), result.professionalsCreated());
    }
}
