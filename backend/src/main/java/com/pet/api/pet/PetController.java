package com.pet.api.pet;

import com.pet.api.pet.dto.PetCategoryTreeResponse;
import com.pet.api.pet.dto.PetCreateRequest;
import com.pet.api.pet.dto.PetResponse;
import com.pet.api.user.dto.AvatarUpdateRequest;
import com.pet.service.PetCategoryService;
import com.pet.service.PetService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class PetController {
  private final PetService petService;
  private final PetCategoryService petCategoryService;

  public PetController(PetService petService, PetCategoryService petCategoryService) {
    this.petService = petService;
    this.petCategoryService = petCategoryService;
  }

  @GetMapping("/pet/categories/tree")
  public List<PetCategoryTreeResponse> listCategoryTree() {
    return petCategoryService.listCategoryTree();
  }

  @GetMapping("/me/pet")
  public PetResponse getMyPet(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return petService.getMyPrimaryPet(userId);
  }

  @GetMapping("/me/pets")
  public List<PetResponse> listMyPets(Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return petService.listMyPets(userId);
  }

  @GetMapping("/pets/{id}")
  public PetResponse getMyPetById(@PathVariable("id") Long id, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return petService.getMyPetById(userId, id);
  }

  @PostMapping("/pets")
  public PetResponse createPet(@Valid @RequestBody PetCreateRequest request, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return petService.createPet(userId, request);
  }

  @PostMapping("/pets/{id}/primary")
  public void setPrimary(@PathVariable("id") Long id, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    petService.setPrimary(userId, id);
  }

  @PostMapping("/pets/{id}/avatar")
  public void updateAvatar(
      @PathVariable("id") Long id,
      @Valid @RequestBody AvatarUpdateRequest request,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    petService.updateAvatar(userId, id, request.avatarUrl());
  }

  @DeleteMapping("/pets/{id}")
  public void deletePet(@PathVariable("id") Long id, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    petService.deletePet(userId, id);
  }
}
