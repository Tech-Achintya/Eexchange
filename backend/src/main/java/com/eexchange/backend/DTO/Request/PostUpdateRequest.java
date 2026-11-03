package com.eexchange.backend.DTO.Request;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

// Same fields as create, plus which existing images to drop.
// Extending PostCreateRequest lets create and update share one validator.
@Data
@EqualsAndHashCode(callSuper = true)
public class PostUpdateRequest extends PostCreateRequest {

    private List<Long> removeImageIds;
}