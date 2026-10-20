-- Pas de FK : conserver le reçu après une suppression volontaire empêche une recréation au redémarrage.
CREATE TABLE showcase_imports (
    catalog_key VARCHAR(160) PRIMARY KEY,
    target_id BIGINT NOT NULL,
    imported_at TIMESTAMP NOT NULL
);
