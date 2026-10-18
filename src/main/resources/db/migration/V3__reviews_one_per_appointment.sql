-- Un seul avis par rendez-vous : un avis lie a un rendez-vous est un "avis
-- verifie" (voir ReviewService.addReview). Les avis libres ont
-- appointment_id NULL ; un index unique PostgreSQL accepte plusieurs NULL,
-- ils ne sont donc pas concernes.
CREATE UNIQUE INDEX IF NOT EXISTS ux_reviews_appointment ON reviews(appointment_id);
