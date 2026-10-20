package com.beautyconnect.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import java.nio.file.*;
import java.util.*;

/** Adaptateur unique : disque local par défaut, Cloudinary sur configuration explicite. */
@Service
@lombok.extern.slf4j.Slf4j
public class ConfiguredImageStorageService implements ImageStorageService {
    private final Path directory;
    private final String provider;
    private final RestClient cloud;
    private final String folder;
    private final String folderMode;

    public ConfiguredImageStorageService(@Value("${app.images.provider:local}") String provider,
            @Value("${image.storage.upload-dir:uploads}") String directory,
            @Value("${app.images.cloudinary.cloud-name:}") String cloudName,
            @Value("${app.images.cloudinary.api-key:}") String apiKey,
            @Value("${app.images.cloudinary.api-secret:}") String apiSecret,
            @Value("${app.images.cloudinary.folder:beautyconnect}") String folder,
            @Value("${app.images.cloudinary.folder-mode:dynamic}") String folderMode) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
        this.provider = provider;
        this.folder = folder;
        this.folderMode = folderMode;
        if (!folder.matches("[a-zA-Z][a-zA-Z0-9_-]*(/[a-zA-Z][a-zA-Z0-9_-]*)*") || !Set.of("dynamic", "fixed").contains(folderMode))
            throw new IllegalStateException("Dossier Cloudinary ou mode invalide.");
        if (!Set.of("local", "cloudinary").contains(provider)) throw new IllegalStateException("IMAGE_STORAGE_PROVIDER doit être local ou cloudinary.");
        if (provider.equals("cloudinary")) {
            if (!cloudName.matches("[a-zA-Z0-9_-]+") || apiKey.isBlank() || apiSecret.isBlank())
                throw new IllegalStateException("Les trois variables CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY et CLOUDINARY_API_SECRET sont requises.");
            var factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(10000); factory.setReadTimeout(60000);
            cloud = RestClient.builder().baseUrl("https://api.cloudinary.com/v1_1/" + cloudName + "/image")
                    // Le multipart est borné à une image de 5 Mo. Le tampon permet
                    // d'envoyer Content-Length plutôt qu'un flux HTTP chunked.
                    .requestFactory(new BufferingClientHttpRequestFactory(factory))
                    .defaultHeaders(h -> h.setBasicAuth(apiKey, apiSecret)).build();
        } else cloud = null;
        log.info("Stockage des photos : {} (dossier {})", provider, provider.equals("cloudinary") ? folder : directory);
    }

    @Override
    public ImageUploadResult upload(MultipartFile file) {
        var image = ValidatedImage.read(file);
        String filename = UUID.randomUUID() + "." + image.extension();
        try {
            if (provider.equals("cloudinary")) {
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new ByteArrayResource(image.bytes()) {
                    @Override public String getFilename() { return filename; }
                });
                body.add("public_id", (folderMode.equals("dynamic") ? folder + "/" : "") + UUID.randomUUID());
                body.add(folderMode.equals("dynamic") ? "asset_folder" : "folder", folder);
                Map<?, ?> response = cloud.post().uri("/upload").contentType(MediaType.MULTIPART_FORM_DATA).body(body).retrieve().body(Map.class);
                if (response == null || !(response.get("public_id") instanceof String id)
                        || !(response.get("secure_url") instanceof String url) || !url.startsWith("https://"))
                    throw new ImageUploadException("Le service photo a renvoyé une réponse invalide.");
                return new ImageUploadResult("cloudinary:" + id, url);
            }
            Files.createDirectories(directory);
            Files.write(directory.resolve(filename), image.bytes(), StandardOpenOption.CREATE_NEW);
            return new ImageUploadResult("local:" + filename, "/uploads/" + filename);
        } catch (ImageUploadException e) { throw e; }
        catch (Exception e) {
            // Ne jamais journaliser les en-têtes, clés ou corps de réponse du fournisseur.
            Throwable cause = e;
            while (cause.getCause() != null) cause = cause.getCause();
            String status = e instanceof org.springframework.web.client.RestClientResponseException response
                    ? response.getStatusCode().toString() : "sans réponse HTTP";
            log.warn("Envoi photo via {} refusé : {}, cause {}", provider, status, cause.getClass().getSimpleName());
            if (e instanceof org.springframework.web.client.RestClientResponseException response
                    && (response.getStatusCode().value() == 401 || response.getStatusCode().value() == 403)) {
                throw new ImageUploadException("Cloudinary refuse l'envoi : vérifiez les identifiants et les autorisations de la clé API côté serveur.", e);
            }
            throw new ImageUploadException("La photo n'a pas pu être enregistrée. Réessayez dans un instant.", e);
        }
    }

    @Override
    public void delete(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            if (publicId.startsWith("cloudinary:")) {
                if (cloud == null) throw new ImageDeleteException("Cloudinary doit rester configuré pour supprimer ses images.");
                MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
                body.add("public_id", publicId.substring(11)); body.add("invalidate", "true");
                var result = cloud.post().uri("/destroy").contentType(MediaType.APPLICATION_FORM_URLENCODED).body(body).retrieve().body(Map.class);
                if (result == null || !Set.of("ok", "not found").contains(result.get("result"))) throw new ImageDeleteException("Suppression Cloudinary refusée.");
            } else {
                String name = publicId.startsWith("local:") ? publicId.substring(6) : publicId;
                // Accepte aussi les anciens sous-dossiers d'Ana, jamais une remontée de chemin.
                if (!name.matches("(?:prestation-photos/|profile-photos/|user-photos/)?[a-zA-Z0-9-]+\\.(?:jpg|jpeg|png)"))
                    throw new ImageDeleteException("Identifiant de photo invalide.");
                Path target = directory.resolve(name).normalize();
                if (!target.startsWith(directory)) throw new ImageDeleteException("Identifiant de photo invalide.");
                Files.deleteIfExists(target);
            }
        } catch (ImageDeleteException e) { throw e; }
        catch (Exception e) { throw new ImageDeleteException("La suppression du fichier photo a échoué.", e); }
    }
}
