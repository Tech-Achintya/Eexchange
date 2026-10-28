package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.CollegeResponse;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Repository.CollegeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CollegeCatalogService {

    private final CollegeRepository collegeRepository;

    /**
     * Public college dropdown. Cached because college rows change rarely.
     * Cache name: "colleges". Key: "active".
     */
    @Cacheable(cacheNames = "colleges", key = "'active'")
    @Transactional(readOnly = true)
    public List<CollegeResponse> getActiveColleges() {
        return collegeRepository.findByIsActiveTrue().stream()
                .sorted(Comparator.comparing(College::getName, String.CASE_INSENSITIVE_ORDER))
                .map(college -> new CollegeResponse(
                        college.getId(),
                        college.getName(),
                        college.getCity(),
                        college.getState(),
                        college.getIsOthers()))
                .toList();
    }

    /**
     * Call this after any college create/update/deactivate.
     */
    @CacheEvict(cacheNames = "colleges", allEntries = true)
    public void evictCollegeCatalog() {
        // Intentionally empty: the annotation does the work.
    }
}