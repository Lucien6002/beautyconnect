package com.beautyconnect.service.storage;

import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;

/** Décode le contenu réel et réencode sans métadonnées ni nom fourni par le navigateur. */
public record ValidatedImage(byte[] bytes, String extension, String contentType) {
    public static ValidatedImage read(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ImageUploadException("Choisissez une photo JPEG ou PNG.");
        if (file.getSize() > 5 * 1024 * 1024) throw new ImageUploadException("La photo doit peser au maximum 5 Mo.");
        try (var input = ImageIO.createImageInputStream(file.getInputStream())) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ImageUploadException("Le fichier doit être une véritable image JPEG ou PNG.");
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw new ImageUploadException("Formats acceptés : JPEG et PNG.");
                reader.setInput(input);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000) throw new ImageUploadException("La photo dépasse 20 mégapixels.");
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                ImageIO.write(image, format, output);
                return new ValidatedImage(output.toByteArray(), format.equals("jpeg") ? "jpg" : "png", "image/" + format);
            } finally { reader.dispose(); }
        } catch (ImageUploadException e) { throw e; }
        catch (Exception e) { throw new ImageUploadException("Cette image est illisible. Essayez une autre photo.", e); }
    }
}
