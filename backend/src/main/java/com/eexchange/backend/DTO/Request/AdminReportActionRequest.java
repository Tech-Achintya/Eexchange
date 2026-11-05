package com.eexchange.backend.DTO.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminReportActionRequest {

    public enum Action {
        RESOLVE,
        DISMISS
    }

    @NotNull(message = "Action is required")
    private Action action;

    @Size(max = 1000, message = "Note cannot exceed 1000 characters")
    private String note;

    // Only honoured for RESOLVE.
    private Boolean deletePost;
}