package com.eexchange.backend.DTO.Request;

import com.eexchange.backend.Entity.Enums.ItemCondition;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PostCreateRequest {

    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 150, message = "Title must be between 5 and 150 characters")
    private String title;

    @NotBlank(message = "Description is required")
    @Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
    private String description;

    @NotNull(message = "Category is required")
    private Long categoryId;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.00", message = "Price cannot be negative")
    @DecimalMax(value = "9999999.99", message = "Price cannot exceed 9999999.99")
    @Digits(integer = 7, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal price;

    @NotNull(message = "Condition is required")
    private ItemCondition condition;
}
