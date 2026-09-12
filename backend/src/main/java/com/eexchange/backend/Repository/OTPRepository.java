package com.eexchange.backend.Repository;

import com.eexchange.backend.Entity.Otp;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface OTPRepository extends MongoRepository<Otp,String> {
    Optional<Otp> findByEmail(String email);
    void deleteByEmail(String email);
}
