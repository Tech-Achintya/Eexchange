package com.eexchange.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Component
public class JWTutil {

    @Value("${app.jwt.secret}")
    private String secretKey;

    @Value("${app.jwt.expiry-ms:36000000}")
    private long expiryMs;

    @Value("${app.admin.jwt.secret}")
    private String adminSecretKey;

    @Value("${app.admin.jwt.expiry-ms:7200000}")
    private long adminExpiryMs;

    // --- Admin Token Generation ---
    public String generateAdminToken(String username, Long adminId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("adminId", adminId);
        claims.put("role", role);
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(username)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + adminExpiryMs))
                .signWith(Keys.hmacShaKeyFor(adminSecretKey.getBytes()), SignatureAlgorithm.HS256)
                .compact();
    }

    // --- Admin Claims & Validation ---
    public Claims extractAdminClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(adminSecretKey.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String extractRole(String token, boolean isAdmin) {
        Claims claims = isAdmin ? extractAdminClaims(token) : extractAllClaims(token);
        Object role = claims.get("role");
        return role != null ? role.toString() : null;
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        Object userId = extractAllClaims(token).get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public Long extractAdminId(String token) {
        Object adminId = extractAdminClaims(token).get("adminId");
        if (adminId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public Long extractCollegeId(String token) {
        Object collegeId = extractAllClaims(token).get("collegeId");
        if (collegeId instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    public String generateToken(String username, Long userId, Long collegeId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("collegeId", collegeId);
        return createToken(claims, username);
    }

    public String generateToken(String username) {
        return createToken(new HashMap<>(), username);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Boolean validateToken(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secretKey.getBytes());
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }
}
