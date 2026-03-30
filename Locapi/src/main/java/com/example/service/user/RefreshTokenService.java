package com.example.service.user;

import com.example.repository.RefreshTokenRepository; // нужно создать
import com.example.user.entity.UserEntity;
import com.example.user.entity.RefreshTokenEntity;   // нужно создать
import com.example.utils.JwtTokenUtils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenUtils jwtTokenUtils;

    public RefreshTokenEntity createRefreshToken(UserDetails userDetails) {
        RefreshTokenEntity refreshToken = new RefreshTokenEntity();
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken.setUsername(userDetails.getUsername());
        refreshToken.setExpiryDate(Instant.now().plusMillis(jwtTokenUtils.getRefreshLifetime().toMillis()));
        return refreshTokenRepository.save(refreshToken);
    }

    public Optional<RefreshTokenEntity> findByToken(String token) {
        return refreshTokenRepository.findByToken(token);
    }

    public void deleteByUsername(String username) { refreshTokenRepository.deleteByUsername(username);}
}