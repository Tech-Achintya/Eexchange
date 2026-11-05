package com.eexchange.backend.DTO.Request;

import com.eexchange.backend.Entity.Enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ReportCreateRequest {

    @NotNull(message = "Reason is required")
    private ReportReason reason;

    @Size(max = 500, message = "Comment cannot exceed 500 characters")
    private String comment;
}