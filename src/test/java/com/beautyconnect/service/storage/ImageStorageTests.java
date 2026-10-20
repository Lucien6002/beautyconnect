package com.beautyconnect.service.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.hamcrest.Matchers.containsString;

public class ImageStorageTests {
    @TempDir Path directory;
    public static MockMultipartFile photo() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("photo", "../../original.html", "application/octet-stream", output.toByteArray());
    }
    public static MockMultipartFile webp() {
        // WebP lossless 8×8, transparence incluse : doit être décodé réellement.
        return new MockMultipartFile("photo", "portrait.webp", "image/webp",
                java.util.Base64.getDecoder().decode("UklGRh4AAABXRUJQVlA4TBEAAAAvB8ABEAdQoorUrICBiOh/AAA="));
    }
    @Test void acceptsWebpAndStoresAReadablePngWithTransparency() throws Exception {
        var result = local().upload(webp());
        assertThat(result.publicId()).endsWith(".png");
        var image = ImageIO.read(directory.resolve(result.publicId().substring(6)).toFile());
        assertThat(image.getWidth()).isEqualTo(8);
        assertThat(image.getColorModel().hasAlpha()).isTrue();
        assertThat(image.getRGB(0, 0) >>> 24).isEqualTo(128);
        local().delete(result.publicId());
    }
    private ConfiguredImageStorageService local() { return new ConfiguredImageStorageService("local", directory.toString(), "", "", "", "beautyconnect", "dynamic"); }
    @Test void usesActualImageContentsAndGeneratedNameAndDeletesFile() throws Exception {
        var storage = local(); var result = storage.upload(photo());
        assertThat(result.publicId()).startsWith("local:").endsWith(".png");
        assertThat(result.secureUrl()).startsWith("/uploads/").doesNotContain("original");
        Path file = directory.resolve(result.publicId().substring(6));
        assertThat(file).exists(); assertThat(ImageIO.read(file.toFile()).getWidth()).isEqualTo(8);
        storage.delete(result.publicId()); assertThat(file).doesNotExist();
    }
    @Test void rejectsDisguisedHtmlEmptyFilesAndOversizedImages() {
        var storage = local();
        assertThatThrownBy(() -> storage.upload(new MockMultipartFile("photo", "image.png", "image/png", "<script>x</script>".getBytes()))).isInstanceOf(ImageUploadException.class);
        assertThatThrownBy(() -> storage.upload(new MockMultipartFile("photo", new byte[0]))).isInstanceOf(ImageUploadException.class);
        assertThatThrownBy(() -> storage.upload(new MockMultipartFile("photo", new byte[5 * 1024 * 1024 + 1]))).isInstanceOf(ImageUploadException.class);
    }
    @Test void rejectsTraversalAndKeepsExistingLegacyImagesDeletable() throws Exception {
        Files.createDirectories(directory.resolve("prestation-photos"));
        Path legacy = directory.resolve("prestation-photos/old-photo.jpg"); Files.write(legacy, new byte[]{1});
        local().delete("local:prestation-photos/old-photo.jpg"); assertThat(legacy).doesNotExist();
        assertThatThrownBy(() -> local().delete("local:../outside.png")).isInstanceOf(ImageDeleteException.class);
    }
    @Test void cloudinaryRequiresCompleteConfiguration() {
        assertThatThrownBy(() -> new ConfiguredImageStorageService("cloudinary", directory.toString(), "", "", "", "beautyconnect", "dynamic")).isInstanceOf(IllegalStateException.class);
    }
    @Test void cloudinaryCreatesApplicationFolderAndUsesPrivateServerAuthentication() throws Exception {
        var builder = RestClient.builder().baseUrl("https://api.cloudinary.com/v1_1/test/image").defaultHeaders(h -> h.setBasicAuth("test-key", "test-secret"));
        var server = MockRestServiceServer.bindTo(builder).build();
        var storage = new ConfiguredImageStorageService("cloudinary", directory.toString(), "test", "test-key", "test-secret", "beautyconnect", "dynamic");
        ReflectionTestUtils.setField(storage, "cloud", builder.build());
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test/image/upload"))
                .andExpect(header("Authorization", "Basic dGVzdC1rZXk6dGVzdC1zZWNyZXQ="))
                .andExpect(content().string(containsString("name=\"asset_folder\"")))
                .andExpect(content().string(containsString("beautyconnect")))
                .andRespond(withSuccess("{\"public_id\":\"beautyconnect/photo1\",\"secure_url\":\"https://res.cloudinary.com/test/photo1.png\"}", MediaType.APPLICATION_JSON));
        var result = storage.upload(photo()); assertThat(result.publicId()).isEqualTo("cloudinary:beautyconnect/photo1");
        server.verify(); server.reset();
        server.expect(requestTo("https://api.cloudinary.com/v1_1/test/image/destroy"))
                .andExpect(content().string(containsString("public_id=beautyconnect%2Fphoto1")))
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));
        storage.delete(result.publicId()); server.verify();
    }
    @Test void cloudinaryForbiddenExplainsPermissionsWithoutExposingProviderBody() throws Exception {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var storage = new ConfiguredImageStorageService("cloudinary", directory.toString(), "test", "key", "secret", "beautyconnect", "dynamic");
        ReflectionTestUtils.setField(storage, "cloud", builder.build());
        server.expect(requestTo("/upload")).andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN)
                .body("confidential-provider-response"));
        assertThatThrownBy(() -> storage.upload(photo())).isInstanceOf(ImageUploadException.class)
                .hasMessageContaining("autorisations").hasMessageNotContaining("confidential-provider-response");
        server.verify();
    }
    @Test void cloudinaryOutageReturnsAnActionableError() throws Exception {
        var builder = RestClient.builder(); var server = MockRestServiceServer.bindTo(builder).build();
        var storage = new ConfiguredImageStorageService("cloudinary", directory.toString(), "test", "key", "secret", "beautyconnect", "dynamic");
        ReflectionTestUtils.setField(storage, "cloud", builder.build());
        server.expect(requestTo("/upload")).andRespond(withServerError());
        assertThatThrownBy(() -> storage.upload(photo())).isInstanceOf(ImageUploadException.class).hasMessageContaining("Réessayez"); server.verify();
    }
}
