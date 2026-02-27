package com.pet.service;

import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class UploadService {
  private static final int POST_THUMB_MAX_WIDTH = 720;

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
      Path targetFile = targetDir.resolve(filename);
      file.transferTo(targetFile);
      if ("posts".equals(category)) {
        createPostThumbnail(targetFile, targetDir.resolve(toThumbFilename(filename)));
      }
    } catch (IOException ex) {
      throw new BusinessException(ApiError.UPLOAD_FAILED, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    return publicBase + "/" + category + "/" + date + "/" + filename;
  }

  private void createPostThumbnail(Path source, Path target) {
    try {
      BufferedImage input = ImageIO.read(source.toFile());
      if (input == null) {
        return;
      }
      int srcWidth = input.getWidth();
      int srcHeight = input.getHeight();
      if (srcWidth <= 0 || srcHeight <= 0) {
        return;
      }

      int outWidth = Math.min(srcWidth, POST_THUMB_MAX_WIDTH);
      int outHeight = (int) Math.round((double) srcHeight * outWidth / srcWidth);
      if (outHeight <= 0) {
        outHeight = srcHeight;
      }

      BufferedImage output = new BufferedImage(outWidth, outHeight, BufferedImage.TYPE_INT_RGB);
      Graphics2D g = output.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.drawImage(input, 0, 0, outWidth, outHeight, null);
      g.dispose();

      ImageIO.write(output, "jpg", target.toFile());
    } catch (IOException ignored) {
      // Keep original upload success even if thumbnail generation fails.
    }
  }

  private String toThumbFilename(String filename) {
    int idx = filename.lastIndexOf('.');
    if (idx <= 0) {
      return filename + "_thumb.jpg";
    }
    return filename.substring(0, idx) + "_thumb.jpg";
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
