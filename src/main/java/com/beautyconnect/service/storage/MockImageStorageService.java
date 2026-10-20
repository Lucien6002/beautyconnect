package com.beautyconnect.service.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
@Profile("!production")
public class MockImageStorageService implements ImageStorageService {

    @Value("${image.storage.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public ImageUploadResult upload(MultipartFile file) throws ImageUploadException {
        try {
            String fileName = UUID.randomUUID().toString() + getFileExtension(file.getOriginalFilename());
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath);

            String publicId = fileName;
            String secureUrl = "/uploads/" + fileName;
            return new ImageUploadResult(publicId, secureUrl);
        } catch (IOException e) {
            throw new ImageUploadException("Erreur lors de l'upload : " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String publicId) throws ImageDeleteException {
        try {
            Path filePath = Paths.get(uploadDir, publicId);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
            }
        } catch (IOException e) {
            throw new ImageDeleteException("Erreur lors de la suppression : " + e.getMessage(), e);
        }
    }

    private String getFileExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        return lastDot > 0 ? filename.substring(lastDot) : "";
    }
}
