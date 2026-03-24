package com.pet.api.pet.dto;

import java.util.List;

public record PetWeightRecordListResponse(
    PetWeightSummaryResponse summary,
    List<PetWeightRecordResponse> records
) {}
