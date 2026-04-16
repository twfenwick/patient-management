package com.pm.authservice.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtil {
    // What makes tokens secure is they can only be verified by using a secret key stored securely on the server.
    // Secret key just a string of random characters
    // We use the secret key to generate a token, but also to prove a token is from our servers and is valid
    //  anytime we receive this token in subsequent requests to our protected endpoints like getPatients
    // Secret is critically important in production enterprise environments, treated as a password
    private final Key secretKey;

    // Secret is stored in the env variables on the server
    public JwtUtil(@Value("${jwt.secret}") String secret) {
        byte[] keyBytes = Base64.getDecoder().decode(secret.getBytes(
                StandardCharsets.UTF_8));
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(String email, String role) {
        return Jwts.builder()
                .subject(email)
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 1000 * 60 * 60 * 10)) // 10 hour expiration for dev
                .signWith(secretKey) // encode all the data defined here
                .compact(); // squashes all the properties specified here down into a nice single string
                            // that forms our jwt token
    }

    public void validateToken(String token) {

        try {
            Jwts.parser().verifyWith((SecretKey) secretKey)
                    .build()
                    .parseSignedClaims(token);
        } catch (SignatureException e) {
            throw new JwtException("Invalid JWT signature");
        } catch (JwtException e) {
            throw new JwtException("Invalid JWT token");
        }
    }
}
