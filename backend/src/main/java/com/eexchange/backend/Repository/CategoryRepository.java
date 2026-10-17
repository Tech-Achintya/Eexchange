package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findBySlug(String slug);
    List<Category> findByIsActiveTrue();
    boolean existsBySlug(String slug);
    boolean existsByName(String name);
}
