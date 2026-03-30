package com.example.controller;

import com.example.service.user.RefreshTokenService;
import com.example.service.user.UserService;
import com.example.user.dto.UserDTO;
import com.example.user.entity.Role;
import com.example.user.exception.RepeatedRegistrationException;
import com.example.user.exception.WrongDataException;
import com.example.user.http.request.AuthTokenRequest;
import com.example.user.http.request.RefreshTokenRequest;
import com.example.user.http.request.RegistrationRequest;
import com.example.user.http.response.AuthTokenResponse;
import com.example.user.http.response.RefreshTokenResponse;
import com.example.user.http.response.RegistrationResponse;
import com.example.user.http.response.UserInfoResponse;
import com.example.utils.JwtTokenUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {

    private final UserService userService;
    private final JwtTokenUtils jwtTokenUtils;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;

    @PostMapping("/signup")
    public ResponseEntity<?> registration(@Validated @RequestBody RegistrationRequest registrationRequest) {
        log.info("REGISTRATION: username={}, email={}", registrationRequest.username(), registrationRequest.email());

        if (userService.findByUsername(registrationRequest.username()).isPresent()) {
            log.warn("REGISTRATION FAILED: username {} already exists", registrationRequest.username());
            return new ResponseEntity<>(new RepeatedRegistrationException(HttpStatus.BAD_REQUEST.value(),
                    "Пользователь с указанным именем уже существует"), HttpStatus.BAD_REQUEST);
        }
        if (userService.findByEmail(registrationRequest.email()).isPresent()) {
            log.warn("REGISTRATION FAILED: email {} already exists", registrationRequest.email());
            return new ResponseEntity<>(new RepeatedRegistrationException(HttpStatus.BAD_REQUEST.value(),
                    "Пользователь с указанным email уже существует"), HttpStatus.BAD_REQUEST);
        }

        UserDTO userDTO = new UserDTO(registrationRequest.username(), registrationRequest.email(),
                registrationRequest.password(), Role.ROLE_USER);
        userService.createNewUser(userDTO);

        log.info("REGISTRATION SUCCESS: user={} created", registrationRequest.username());
        return ResponseEntity.ok(new RegistrationResponse(userDTO.username(), userDTO.email()));
    }

    @PostMapping("/createAuthToken")
    public ResponseEntity<?> createAuthToken(@Validated @RequestBody AuthTokenRequest authTokenRequest) {
        log.info("LOGIN ATTEMPT: username={}", authTokenRequest.username());

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authTokenRequest.username(), authTokenRequest.password()));
        } catch (BadCredentialsException e) {
            log.warn("LOGIN FAILED: bad credentials for user={}", authTokenRequest.username());
            return new ResponseEntity<>(new WrongDataException(HttpStatus.UNAUTHORIZED.value(),
                    "Incorrect login or password"), HttpStatus.UNAUTHORIZED);
        }

        UserDetails userDetails = userService.loadUserByUsername(authTokenRequest.username());
        String accessToken = jwtTokenUtils.generateAccessToken(userDetails);
        String refreshToken = jwtTokenUtils.generateRefreshToken(userDetails);

        refreshTokenService.deleteByUsername(userDetails.getUsername());
        refreshTokenService.createRefreshToken(userDetails);

        log.info("LOGIN SUCCESS: user={}, roles={}", authTokenRequest.username(), userDetails.getAuthorities());

        return ResponseEntity.ok(new AuthTokenResponse(accessToken, refreshToken));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Validated @RequestBody RefreshTokenRequest refreshRequest) {
        String refreshToken = refreshRequest.refreshToken();
        log.info("REFRESH TOKEN: token={}...", refreshToken.substring(0, Math.min(20, refreshToken.length())));

        // Проверяем, что это refresh токен
        if (!jwtTokenUtils.isRefreshToken(refreshToken)) {
            log.warn("REFRESH FAILED: invalid token type");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid token type");
        }

        try {
            String username = jwtTokenUtils.getUsername(refreshToken);
            UserDetails userDetails = userService.loadUserByUsername(username);
            String newAccessToken = jwtTokenUtils.generateAccessToken(userDetails);

            log.info("REFRESH SUCCESS: user={}", username);
            return ResponseEntity.ok(new RefreshTokenResponse(newAccessToken));
        } catch (Exception e) {
            log.error("REFRESH FAILED: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid refresh token");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getCurrentUser(Principal principal) {
        log.info("GET CURRENT USER: {}", principal.getName());

        UserDetails userDetails = userService.loadUserByUsername(principal.getName());
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toList());

        log.info("USER INFO: username={}, roles={}", userDetails.getUsername(), roles);
        return ResponseEntity.ok(new UserInfoResponse(userDetails.getUsername(), roles));
    }


}