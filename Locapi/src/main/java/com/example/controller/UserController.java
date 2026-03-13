package com.example.controller;

import com.example.service.user.UserService;
import com.example.user.dto.UserDTO;
import com.example.user.exception.RepeatedRegistrationException;
import com.example.user.exception.WrongDataException;
import com.example.user.http.request.AuthTokenRequest;
import com.example.user.http.request.RegistrationRequest;
import com.example.user.http.response.AuthTokenResponse;
import com.example.user.http.response.RegistrationResponse;
import com.example.utils.JwtTokenUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@AllArgsConstructor
@RestController
@RequestMapping("/api/user")
@Slf4j
public class UserController {


    private final UserService userService;

    private final JwtTokenUtils jwtTokenUtils;

    private final AuthenticationManager authenticationManager;



    @PostMapping("/signup")
    public ResponseEntity<?> registration(@Validated @RequestBody RegistrationRequest registrationRequest) {

        if (userService.findByUsername(registrationRequest.username()).isPresent()) {
            return new ResponseEntity<>(new RepeatedRegistrationException(HttpStatus.BAD_REQUEST.value(), "Пользователь с указанным именем уже существует"), HttpStatus.BAD_REQUEST);
        }

        if (userService.findByEmail(registrationRequest.email()).isPresent()) {
            return new ResponseEntity<>(new RepeatedRegistrationException(HttpStatus.BAD_REQUEST.value(), "Пользователь с указанным email уже существует"), HttpStatus.BAD_REQUEST);
        }

        UserDTO userDTO = new UserDTO(registrationRequest.username(), registrationRequest.email(), registrationRequest.password());
        userService.createNewUser(userDTO);

        log.info("User {} created", registrationRequest.username());
        return ResponseEntity.ok(new RegistrationResponse(userDTO.username(), userDTO.email()));
    }



    @PostMapping("/createAuthToken")
    public ResponseEntity<?> createAuthToken(@Validated @RequestBody AuthTokenRequest authTokenRequest) {

        log.info("{} tried to sign in (requested token)", authTokenRequest.username());

        try {

            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(authTokenRequest.username(), authTokenRequest.password()));

        } catch (BadCredentialsException e) {

            log.warn("Bad credentials (incorrect login/password)");
            return new ResponseEntity<>(
                    new WrongDataException(HttpStatus.UNAUTHORIZED.value(),
                            "Incorrect login or password"), HttpStatus.UNAUTHORIZED);
        }

        UserDetails userDetails = userService.loadUserByUsername(authTokenRequest.username());
        String token = jwtTokenUtils.generateToken(userDetails);

        return ResponseEntity.ok(new AuthTokenResponse(token));
    }


    @GetMapping("/secured/checkToken")
    public boolean checkToken() {
        return true;
    }

}
