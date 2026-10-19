ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN;
-- Les comptes déjà actifs ont franchi l'activation ; les anciens comptes suspendus sans token
-- ne doivent pas pouvoir se réactiver en demandant un lien.
UPDATE users u SET email_verified = (u.enabled OR NOT EXISTS (
    SELECT 1 FROM verification_tokens t WHERE t.user_id = u.id)) WHERE email_verified IS NULL;
ALTER TABLE users ALTER COLUMN email_verified SET NOT NULL;
ALTER TABLE users ALTER COLUMN email_verified SET DEFAULT FALSE;
ALTER TABLE verification_tokens ADD COLUMN IF NOT EXISTS created_at TIMESTAMP;
UPDATE verification_tokens SET created_at = CURRENT_TIMESTAMP - INTERVAL '1 day' WHERE created_at IS NULL;
ALTER TABLE verification_tokens ALTER COLUMN created_at SET NOT NULL;
ALTER TABLE professional_profiles ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE professional_profiles ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE appointments ADD COLUMN IF NOT EXISTS active_time_slot_id BIGINT REFERENCES time_slots(id);
-- Retirer uniquement la contrainte unique historique sur time_slot_id (nom généré par Hibernate).
DO $$ DECLARE item RECORD; BEGIN
    FOR item IN SELECT c.conname FROM pg_constraint c
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = ANY(c.conkey)
        WHERE c.conrelid = 'appointments'::regclass AND c.contype = 'u'
          AND array_length(c.conkey, 1) = 1 AND a.attname = 'time_slot_id'
    LOOP EXECUTE format('ALTER TABLE appointments DROP CONSTRAINT %I', item.conname); END LOOP;
END $$;
UPDATE appointments SET active_time_slot_id = time_slot_id
    WHERE status IN ('EN_ATTENTE', 'CONFIRME', 'TERMINE') AND active_time_slot_id IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS ux_appointments_active_time_slot ON appointments(active_time_slot_id);
UPDATE time_slots t SET available = NOT EXISTS (
    SELECT 1 FROM appointments a WHERE a.active_time_slot_id = t.id
);

ALTER TABLE professional_profiles ADD COLUMN IF NOT EXISTS coordinates_public BOOLEAN NOT NULL DEFAULT FALSE;
