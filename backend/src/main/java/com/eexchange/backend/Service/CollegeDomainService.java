package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.College;
import com.eexchange.backend.Entity.CollegeEmailDomain;
import com.eexchange.backend.Repository.CollegeRepository;
import com.eexchange.backend.Exception.AppException;
import com.eexchange.backend.utils.DisposableEmailChecker;
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

        // Fetch registered domains from Caffeine cache
        List<CollegeEmailDomain> allDomains = collegeDomainLoader.loadAllDomains();

        College matchedCollege = null;
        int longestMatchLength = 0;

        for (CollegeEmailDomain ced : allDomains) {
            String registeredDomain = ced.getDomain().toLowerCase().trim();
            // Match exact domain or subdomain (e.g. cs.iitd.ac.in matches iitd.ac.in)
            if (domain.equals(registeredDomain) || domain.endsWith("." + registeredDomain)) {
                if (registeredDomain.length() > longestMatchLength) {
                    matchedCollege = ced.getCollege();
                    longestMatchLength = registeredDomain.length();
                }
            }
        }

        if (matchedCollege != null && Boolean.TRUE.equals(matchedCollege.getIsActive())) {
            return new CollegeMatchResult(matchedCollege, true);
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
