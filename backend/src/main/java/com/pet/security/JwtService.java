package com.pet.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
  private final String secret;
  private final long expirationMs;

  public JwtService(@Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
    this.secret = secret;
    this.expirationMs = expirationMinutes * 60_000L;
  }

  public String generateToken(Long userId, String role) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + expirationMs);
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("uid", userId)
        .claim("role", role)
        .issuedAt(now)
        .expiration(expiry)
        .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }

  public Long extractUserId(String token) {
    Claims claims = parseClaims(token);
    Object uid = claims.get("uid");
    if (uid instanceof Number number) {
      return number.longValue();
    }
    return Long.parseLong(claims.getSubject());
  }

  public String extractRole(String token) {
    Claims claims = parseClaims(token);
    Object role = claims.get("role");
    return role == null ? "USER" : role.toString();
  }

  public boolean isTokenValid(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public long getExpirationSeconds() {
    return expirationMs / 1000;
  }
}
