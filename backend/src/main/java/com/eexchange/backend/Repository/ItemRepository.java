package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.items;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ItemRepository extends MongoRepository<items, String> {
    List<items> findByUserEmail(String userEmail);
    List<items> findByCategory(String category);
    List<items> findByStatus(com.eexchange.backend.Entity.ItemStatus status);
    List<items> findByStatusAndCategory(com.eexchange.backend.Entity.ItemStatus status, String category);
}
