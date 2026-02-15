package com.pet.service;

import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
  private final Path baseDir;
  private final String publicBase;

  public UploadService(@Value("${app.upload.base-dir}") String baseDir,
      @Value("${app.upload.public-base:/uploads}") String publicBase) {
    this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
    this.publicBase = publicBase;
  }

  public String save(MultipartFile file, String category) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(ApiError.FILE_REQUIRED, HttpStatus.BAD_REQUEST);
    }
    String ext = extractExtension(file.getOriginalFilename());
    if (ext.isEmpty()) {
      ext = contentTypeToExtension(file.getContentType());
    }
    String date = LocalDate.now().toString();
    String filename = UUID.randomUUID().toString().replace("-", "") + ext;
    Path targetDir = baseDir.resolve(category).resolve(date);
    try {
      Files.createDirectories(targetDir);
      file.transferTo(targetDir.resolve(filename));
    } catch (IOException ex) {
      throw new BusinessException(ApiError.UPLOAD_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return publicBase + "/" + category + "/" + date + "/" + filename;
  }

  private String extractExtension(String name) {
    if (name == null) {
      return "";
    }
    int idx = name.lastIndexOf('.');
    if (idx < 0 || idx == name.length() - 1) {
      return "";
    }
    String ext = name.substring(idx).toLowerCase();
    return ext.length() > 10 ? "" : ext;
  }

  private String contentTypeToExtension(String contentType) {
    if (contentType == null) {
      return "";
    }
    return switch (contentType) {
      case "image/jpeg" -> ".jpg";
      case "image/png" -> ".png";
      case "image/webp" -> ".webp";
      default -> "";
    };
  }
}
