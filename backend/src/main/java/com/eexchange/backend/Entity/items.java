package com.eexchange.backend.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "items")
public class items {

    @Id
    private String id;

    private String title;
    private double price;

    @Indexed
    private String userEmail;   // changed from userId to userEmail

    private String description;
    @Indexed
    private String category;
    private ItemStatus status = ItemStatus.PENDING;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
