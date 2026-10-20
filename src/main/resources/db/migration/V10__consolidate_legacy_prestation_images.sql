-- Conserve les réalisations enregistrées via l'ancienne entité d'Ana.
INSERT INTO prestation_photos (prestation_id, photo_url, public_id, display_order, created_at)
SELECT image.prestation_id, image.secure_url,
       CASE WHEN image.secure_url LIKE '/uploads/%'
            THEN 'local:' || SUBSTRING(image.secure_url FROM 10)
            ELSE 'cloudinary:' || image.public_id END,
       COALESCE((SELECT MAX(photo.display_order) FROM prestation_photos photo
                 WHERE photo.prestation_id = image.prestation_id), -1)
           + ROW_NUMBER() OVER (PARTITION BY image.prestation_id ORDER BY image.display_order, image.id),
       image.created_at
FROM prestation_images image
WHERE NOT EXISTS (SELECT 1 FROM prestation_photos existing
                  WHERE existing.prestation_id = image.prestation_id AND existing.photo_url = image.secure_url);
