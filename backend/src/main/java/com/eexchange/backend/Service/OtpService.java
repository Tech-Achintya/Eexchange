package com.eexchange.backend.Service;

import com.eexchange.backend.Entity.Otp;
import com.eexchange.backend.Repository.OTPRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;
import java.util.Random;

@Service
public class OtpService {

    private final OTPRepository otpRepository;
    private final EmailService emailService;

    @Autowired
    public OtpService(OTPRepository otpRepository, EmailService emailService) {
        this.otpRepository = otpRepository;
        this.emailService = emailService;
    }

    public String generateAndSaveOtp(String email) {
        // Generating 6-digit random OTP
        String otpCode = String.format("%06d", new Random().nextInt(999999));

        // Clear old OTP for this email
        otpRepository.deleteByEmail(email);

        // Save new OTP with createdAt for TTL index
        Otp otp = new Otp();
        otp.setEmail(email);
        otp.setOtpCode(otpCode);
        otp.setCreatedAt(new Date()); // Crucial for TTL
        otpRepository.save(otp);

        // Send OTP via email
        emailService.sendOtpEmail(email, otpCode);

        return otpCode; 
    }

    public boolean validateOtp(String email, String otpCode) {
        Optional<Otp> otpOptional = otpRepository.findByEmail(email);
        if (otpOptional.isPresent()) {
            Otp otp = otpOptional.get();
            if (otp.getOtpCode().equals(otpCode)) {
                otpRepository.deleteByEmail(email); // Delete after final use
                return true;
            }
        }
        return false;
    }

    public boolean checkOtp(String email, String otpCode) {
        Optional<Otp> otpOptional = otpRepository.findByEmail(email);
        return otpOptional.isPresent() && otpOptional.get().getOtpCode().equals(otpCode);
    }
}
