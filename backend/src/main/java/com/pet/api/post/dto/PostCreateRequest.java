package com.pet.api.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostCreateRequest(
    Long petId,
    @NotBlank(message = "content is required") @Size(max = 5000, message = "content too long") String content,
    @Size(max = 50, message = "city too long") String city,
    List<String> tags,
    List<String> mediaUrls
) {}
