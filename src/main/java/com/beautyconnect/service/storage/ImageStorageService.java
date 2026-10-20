package com.beautyconnect.service.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {
    ImageUploadResult upload(MultipartFile file) throws ImageUploadException;
    void delete(String publicId) throws ImageDeleteException;
}
