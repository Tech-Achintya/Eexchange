package com.eexchange.backend.DTO.Request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminModerationRequeueRequest {

    @Size(max = 300, message = "Note cannot exceed 300 characters")
    private String note;
}
