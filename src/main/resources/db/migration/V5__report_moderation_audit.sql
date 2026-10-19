-- Journal de moderation des signalements : quelle decision l'admin a prise,
-- qui l'a prise et quand. Colonnes nulles tant que le signalement est en
-- attente, et pour les signalements traites avant cette migration.
ALTER TABLE reports ADD COLUMN IF NOT EXISTS decision VARCHAR(255);
ALTER TABLE reports ADD COLUMN IF NOT EXISTS handled_by_id BIGINT REFERENCES users(id);
ALTER TABLE reports ADD COLUMN IF NOT EXISTS handled_at TIMESTAMP;
