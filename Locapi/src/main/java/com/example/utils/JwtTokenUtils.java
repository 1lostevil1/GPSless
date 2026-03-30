package com.example.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class JwtTokenUtils {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access.lifetime}")
    private Duration accessLifetime;

    @Value("${jwt.refresh.lifetime}")
    private Duration refreshLifetime;

    // Генерация access токена
    public String generateAccessToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", userDetails.getUsername());
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());
        claims.put("roles", roles);
        return generateToken(claims, userDetails.getUsername(), accessLifetime);
    }

    // Генерация refresh токена
    public String generateRefreshToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", userDetails.getUsername());
        claims.put("type", "refresh");
        return generateToken(claims, userDetails.getUsername(), refreshLifetime);
    }

    private String generateToken(Map<String, Object> claims, String subject, Duration lifetime) {
        Date issuedAt = new Date();
        Date expiredAt = new Date(issuedAt.getTime() + lifetime.toMillis());
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(issuedAt)
                .setExpiration(expiredAt)
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    // Получение имени пользователя из токена
    public String getUsername(String token) {
        return getClaimsFromToken(token).getSubject();
    }

    // Получение ролей из токена
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        return getClaimsFromToken(token).get("roles", List.class);
    }

    // Проверка, является ли токен refresh
    public boolean isRefreshToken(String token) {
        String type = getClaimsFromToken(token).get("type", String.class);
        return "refresh".equals(type);
    }

    public boolean isTokenExpired(String token) {
        return getClaimsFromToken(token).getExpiration().before(new Date());
    }

    // Добавьте этот метод
    public Duration getRefreshLifetime() {
        return refreshLifetime;
    }

    // Опционально: метод для получения access lifetime
    public Duration getAccessLifetime() {
        return accessLifetime;
    }

    private Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }
}