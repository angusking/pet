package com.pet.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.pet.api.error.ApiError;
import com.pet.api.error.BusinessException;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

class UploadServiceTest {
  @TempDir
  Path tempDir;

  @Test
  void saveShouldThrowWhenFileNull() {
    UploadService service = new UploadService(tempDir.toString(), "/uploads");

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.save(null, "pet-avatars"));

    assertEquals(ApiError.FILE_REQUIRED, ex.getError());
    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
  }

  @Test
  void saveShouldThrowWhenFileEmpty() {
    UploadService service = new UploadService(tempDir.toString(), "/uploads");
    MultipartFile file = new MockMultipartFile("file", "", "image/png", new byte[0]);

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.save(file, "pet-avatars"));

    assertEquals(ApiError.FILE_REQUIRED, ex.getError());
  }

  @Test
  void saveShouldUseFilenameExtensionWhenPresent() {
    UploadService service = new UploadService(tempDir.toString(), "/uploads");
    MultipartFile file = new MockMultipartFile("file", "avatar.png", "image/png", "x".getBytes());

    String path = service.save(file, "pet-avatars");

    assertTrue(path.startsWith("/uploads/pet-avatars/" + LocalDate.now()));
    assertTrue(path.endsWith(".png"));
  }

  @Test
  void saveShouldFallbackExtensionFromContentType() {
    UploadService service = new UploadService(tempDir.toString(), "/uploads");
    MultipartFile file = new MockMultipartFile("file", "avatar", "image/jpeg", "x".getBytes());

    String path = service.save(file, "pet-avatars");

    assertTrue(path.endsWith(".jpg"));
  }

  @Test
  void saveShouldThrowWhenTransferFails() throws Exception {
    UploadService service = new UploadService(tempDir.toString(), "/uploads");
    MultipartFile file = mock(MultipartFile.class);
    when(file.isEmpty()).thenReturn(false);
    when(file.getOriginalFilename()).thenReturn("avatar.png");
    doThrow(new IOException("disk error")).when(file).transferTo(any(Path.class));

    BusinessException ex = assertThrows(BusinessException.class,
        () -> service.save(file, "pet-avatars"));

    assertEquals(ApiError.UPLOAD_FAILED, ex.getError());
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, ex.getStatus());
  }

  @Test
  void saveShouldReturnPublicPath() {
    UploadService service = new UploadService(tempDir.toString(), "/uploads");
    MultipartFile file = new MockMultipartFile("file", "avatar.webp", "image/webp", "x".getBytes());

    String path = service.save(file, "avatars");

    assertTrue(path.startsWith("/uploads/avatars/"));
    assertTrue(path.contains(LocalDate.now().toString()));
    assertTrue(path.endsWith(".webp"));
  }
}
