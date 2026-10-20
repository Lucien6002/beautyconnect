-- Conserve les URLs locales d'Ana ; les prochains envois mémorisent leur fournisseur.
ALTER TABLE prestation_photos ADD COLUMN public_id VARCHAR(500);
ALTER TABLE users ADD COLUMN profile_photo_public_id VARCHAR(500);
ALTER TABLE professional_profiles ADD COLUMN profile_photo_public_id VARCHAR(500);
ALTER TABLE users ALTER COLUMN profile_photo_url TYPE VARCHAR(500);
ALTER TABLE professional_profiles ALTER COLUMN profile_photo_url TYPE VARCHAR(500);
