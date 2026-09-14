package com.suretyseven.docflow.util;

import java.security.Key;
import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JwtUtil {

    private final Key signingKey;
    private final long expiryMs;

    public JwtUtil(@Value("${app.jwt.secret}") String secret,
                    @Value("${app.jwt.expiry-ms}") long expiryMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expiryMs = expiryMs;
    }

    public String generateToken(String username) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expiryMs);
        String token = Jwts.builder()
                .setSubject(username)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(signingKey, SignatureAlgorithm.HS256)
                .compact();
        // Only the subject/expiry are logged; the signed token itself is a credential.
        log.debug("Issued JWT: username={}, expiresAt={}", username, expiry);
        return token;
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            boolean valid = claims.getExpiration().after(new Date());
            if (!valid) {
                log.debug("JWT rejected: expired at {}", claims.getExpiration());
            }
            return valid;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT rejected: {}", ex.getMessage());
            return false;
        }
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(signingKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
