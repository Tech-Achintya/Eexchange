package com.eexchange.backend.DTO;

import com.eexchange.backend.Entity.Enums.AdminRole;

public record AdminPrincipal(Long adminId, String username, AdminRole role) {
}