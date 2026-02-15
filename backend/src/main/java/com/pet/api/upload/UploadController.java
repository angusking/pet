package com.pet.api.upload;

import com.pet.api.upload.dto.UploadResponse;
import com.pet.service.UploadService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/uploads")
public class UploadController {
  private final UploadService uploadService;

  public UploadController(UploadService uploadService) {
    this.uploadService = uploadService;
  }

  @PostMapping("/user-avatar")
  public UploadResponse uploadUserAvatar(@RequestParam("file") MultipartFile file) {
    String path = uploadService.save(file, "avatars");
    return new UploadResponse(path);
  }

  @PostMapping("/pet-avatar")
  public UploadResponse uploadPetAvatar(@RequestParam("file") MultipartFile file) {
    String path = uploadService.save(file, "pet-avatars");
    return new UploadResponse(path);
  }

  @PostMapping("/post")
  public UploadResponse uploadPost(@RequestParam("file") MultipartFile file) {
    String path = uploadService.save(file, "posts");
    return new UploadResponse(path);
  }
}
