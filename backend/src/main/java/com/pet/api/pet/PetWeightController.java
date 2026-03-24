package com.pet.api.pet;

import com.pet.api.pet.dto.PetWeightRecordCreateRequest;
import com.pet.api.pet.dto.PetWeightRecordListResponse;
import com.pet.api.pet.dto.PetWeightRecordResponse;
import com.pet.service.PetWeightService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pets/{petId}/weights")
public class PetWeightController {
  private final PetWeightService petWeightService;

  public PetWeightController(PetWeightService petWeightService) {
    this.petWeightService = petWeightService;
  }

  @GetMapping
  public PetWeightRecordListResponse list(@PathVariable("petId") Long petId, Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return petWeightService.listRecords(userId, petId);
  }

  @PostMapping
  public PetWeightRecordResponse create(
      @PathVariable("petId") Long petId,
      @Valid @RequestBody PetWeightRecordCreateRequest request,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    return petWeightService.createRecord(userId, petId, request);
  }

  @DeleteMapping("/{recordId}")
  public void delete(
      @PathVariable("petId") Long petId,
      @PathVariable("recordId") Long recordId,
      Authentication authentication) {
    Long userId = (Long) authentication.getPrincipal();
    petWeightService.deleteRecord(userId, petId, recordId);
  }
}
