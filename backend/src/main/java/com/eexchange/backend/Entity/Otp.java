package com.eexchange.backend.Entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Document(collection = "otps")
public class Otp {
    @Id
    private String id;

    @Indexed
    private String email;
    private String otpCode;

    @Indexed(expireAfter = "300s")
    private Date createdAt;
}
