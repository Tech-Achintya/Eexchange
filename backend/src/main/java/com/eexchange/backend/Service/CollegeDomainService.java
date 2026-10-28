package com.eexchange.backend.Service;

import com.eexchange.backend.DTO.CachedCollegeDomain;
import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.Repository.CollegeRepository;
import com.eexchange.backend.Utils.DisposableEmailChecker;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CollegeDomainService {

    private final CollegeRepository collegeRepository;
    private final CollegeDomainLoader collegeDomainLoader;
    private final DisposableEmailChecker disposableChecker;

    @Getter
    @AllArgsConstructor
    public static class CollegeMatchResult {
        private final College college;
        private final boolean isCollegeVerified;
    }

    public CollegeMatchResult resolveCollege(String email) {
        String domain = extractDomain(email);

        if (disposableChecker.isDisposable(domain)) {
            throw new AppException("Disposable email addresses are not permitted", HttpStatus.BAD_REQUEST);
        }

        // Fetch plain cached DTOs from Caffeine cache (no lazy loading risks)
        List<CachedCollegeDomain> allDomains = collegeDomainLoader.loadAllDomains();

        CachedCollegeDomain bestMatch = null;
        int longestMatchLength = 0;

        for (CachedCollegeDomain ccd : allDomains) {
            String registeredDomain = ccd.domain().toLowerCase().trim();
            // Match exact domain or subdomain (e.g. cs.iitd.ac.in matches iitd.ac.in)
            if (domain.equals(registeredDomain) || domain.endsWith("." + registeredDomain)) {
                if (registeredDomain.length() > longestMatchLength) {
                    bestMatch = ccd;
                    longestMatchLength = registeredDomain.length();
                }
            }
        }

        if (bestMatch != null && bestMatch.isActive()) {
            College college = collegeRepository.findById(bestMatch.collegeId())
                    .orElseThrow(() -> new AppException("Matched college not found", HttpStatus.INTERNAL_SERVER_ERROR));
            return new CollegeMatchResult(college, true);
        }

        // Fallback to "Others" college
        College othersCollege = collegeRepository.findByIsOthersTrue()
                .orElseThrow(() -> new AppException("System 'Others' college not configured", HttpStatus.INTERNAL_SERVER_ERROR));

        return new CollegeMatchResult(othersCollege, false);
    }

    public String extractDomain(String email) {
        if (email == null || !email.contains("@")) {
            throw new AppException("Invalid email format", HttpStatus.BAD_REQUEST);
        }
        return email.substring(email.indexOf("@") + 1).toLowerCase().trim();
    }
}
