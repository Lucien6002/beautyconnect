package com.beautyconnect;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Verifie que le contexte Spring demarre correctement (profil "test" =
 * base H2 en memoire, voir src/test/resources/application-test.properties).
 */
@SpringBootTest
@ActiveProfiles("test")
class BeautyConnectApplicationTests {

    @Test
    void contextLoads() {
    }
}
