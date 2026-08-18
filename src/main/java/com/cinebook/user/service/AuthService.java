package com.cinebook.user.service;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import com.cinebook.config.JwtService;
import com.cinebook.user.dto.AuthResponse;
import com.cinebook.user.dto.LoginRequest;
import com.cinebook.user.dto.RegisterRequest;
import com.cinebook.user.model.JsonUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JsonLoaderService jsonLoaderService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse login(LoginRequest request) {

        List<AuthUserJson> users =
                jsonLoaderService.getData(
                        "data/auth-users.json",
                        AuthUserJson.class
                );

        AuthUserJson user = users.stream()
                .filter(u -> u.email != null)
                .filter(u -> u.email.equalsIgnoreCase(request.getEmail()))
                .findFirst()
                .orElseThrow(() ->
                        new BadCredentialsException(
                                "Invalid email or password"
                        )
                );

        if (user.passwordHash == null ||
                !passwordEncoder.matches(
                        request.getPassword(),
                        user.passwordHash
                )) {

            throw new BadCredentialsException(
                    "Invalid email or password"
            );
        }

        String token = jwtService.generateToken(user.email);

        return new AuthResponse(user, token);
    }

    public AuthResponse register(RegisterRequest request) {

        List<AuthUserJson> users =
                jsonLoaderService.getData(
                        "data/auth-users.json",
                        AuthUserJson.class
                );

        // Check email
        boolean emailExists = users.stream()
                .anyMatch(u ->
                        u.email != null &&
                        u.email.equalsIgnoreCase(request.getEmail())
                );

        if (emailExists) {
            throw new IllegalStateException(
                    "Email already in use"
            );
        }

        // Check username
        boolean usernameExists = users.stream()
                .anyMatch(u ->
                        u.username != null &&
                        u.username.equalsIgnoreCase(request.getUsername())
                );

        if (usernameExists) {
            throw new IllegalStateException(
                    "Username already in use"
            );
        }

        // Create new authentication user
        AuthUserJson newUser = new AuthUserJson();

        newUser.id = users.stream()
                .mapToInt(u -> u.id)
                .max()
                .orElse(0) + 1;

        newUser.username = request.getUsername();
        newUser.email = request.getEmail();

        // Store BCrypt hash, never the plain password
        newUser.passwordHash =
                passwordEncoder.encode(request.getPassword());

        // Default role for normal users
        newUser.role = "USER";

        // Persist user
        AuthUserJson savedUser =
                jsonLoaderService.addAuthUser(newUser);

        // Generate JWT immediately after registration
        String token =
                jwtService.generateToken(savedUser.email);

        return new AuthResponse(savedUser, token);
    }

    public JsonUserPrincipal findUserByEmail(String email) {

        List<AuthUserJson> users =
                jsonLoaderService.getData(
                        "data/auth-users.json",
                        AuthUserJson.class
                );

        AuthUserJson user = users.stream()
                .filter(u ->
                        u.email != null &&
                        u.email.equalsIgnoreCase(email)
                )
                .findFirst()
                .orElse(null);

        return user == null
                ? null
                : new JsonUserPrincipal(user);
    }
}