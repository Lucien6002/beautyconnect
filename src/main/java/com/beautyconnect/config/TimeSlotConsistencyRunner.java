package com.beautyconnect.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Au demarrage, remet "disponible" tout creneau marque occupe alors qu'aucun
 * rendez-vous actif (en attente, confirme ou termine) n'y est rattache.
 * Cas typique : des rendez-vous supprimes directement en base, qui laissent
 * des creneaux bloques et invisibles pour les clients.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TimeSlotConsistencyRunner implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            int fixed = jdbcTemplate.update("""
                    UPDATE time_slots SET available = true
                    WHERE available = false
                      AND id NOT IN (
                          SELECT time_slot_id FROM appointments
                          WHERE status IN ('EN_ATTENTE', 'CONFIRME', 'TERMINE'))
                    """);
            if (fixed > 0) {
                log.info("{} creneau(x) orphelin(s) remis disponible(s)", fixed);
            }
        } catch (Exception ex) {
            log.warn("Verification de coherence des creneaux impossible : {}", ex.getMessage());
        }
    }
}
