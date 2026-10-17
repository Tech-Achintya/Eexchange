package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.CachedCollegeDomain;
import com.eexchange.backend.Repository.CollegeEmailDomainRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CollegeDomainLoader {

    private final CollegeEmailDomainRepository domainRepository;

    @Cacheable("collegeDomains")
    public List<CachedCollegeDomain> loadAllDomains() {
        return domainRepository.findAllCachedDomains();
    }
}
