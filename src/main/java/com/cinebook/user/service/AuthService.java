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
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

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
                .filter(u -> u.email.equalsIgnoreCase(request.getEmail().trim()))
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

        String token = jwtService.generateToken(user.email, user.tokenVersion == null ? 0 : user.tokenVersion);

        return new AuthResponse(user, token);
    }

    public synchronized AuthResponse register(RegisterRequest request) {

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String username = request.getUsername().trim();
        String password = request.getPassword();
        if (password.length() < 10 || password.length() > 72
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must be between 10 and 72 characters.");
        }
        String normalizedPassword = password.toLowerCase(Locale.ROOT);
        if (List.of("password123", "qwerty1234", "1234567890", "letmein1234").contains(normalizedPassword)) {
            throw new IllegalArgumentException("Choose a less common password.");
        }

        List<AuthUserJson> users =
                jsonLoaderService.getData(
                        "data/auth-users.json",
                        AuthUserJson.class
                );

        // Check email
        boolean emailExists = users.stream()
                .anyMatch(u ->
                        u.email != null &&
                        u.email.equalsIgnoreCase(email)
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
                        u.username.equalsIgnoreCase(username)
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

        newUser.username = username;
        newUser.email = email;
        newUser.name = request.getName() == null || request.getName().isBlank()
                ? username : request.getName().trim();
        newUser.emailVerified = false;
        newUser.createdAt = Instant.now().toString();
        newUser.linkedProviders = new java.util.ArrayList<>(List.of("password"));

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
                jwtService.generateToken(savedUser.email, 0);

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

    public AuthUserJson updateProfile(int userId, String name, String preferredCity,
                                      java.util.Map<String, Boolean> preferences) {
        AuthUserJson user = jsonLoaderService.getData("data/auth-users.json", AuthUserJson.class)
                .stream().filter(candidate -> candidate.id == userId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (name != null) user.name = name.trim();
        if (preferredCity != null) user.preferredCity = preferredCity.trim();
        if (preferences != null) {
            java.util.Map<String, Boolean> allowed = java.util.Map.of(
                    "bookingEmail", Boolean.TRUE, "bookingInApp", Boolean.TRUE,
                    "securityEmail", Boolean.TRUE, "marketingEmail", Boolean.TRUE);
            for (String key : preferences.keySet()) {
                if (!allowed.containsKey(key)) throw new IllegalArgumentException("Unknown preference: " + key);
            }
            if (user.notificationPreferences == null) user.notificationPreferences = new java.util.HashMap<>();
            user.notificationPreferences.putAll(preferences);
        }
        return jsonLoaderService.updateAuthUser(user);
    }

    public synchronized AuthResponse googleLogin(GoogleIdentityService.GoogleAccount identity) {
        List<AuthUserJson> users = jsonLoaderService.getData("data/auth-users.json", AuthUserJson.class);
        List<AuthUserJson> linkedAccounts = users.stream()
                .filter(user -> identity.subject().equals(user.googleSubject))
                .toList();
        if (linkedAccounts.size() > 1) {
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "This Google identity is linked to multiple CineBook accounts. Contact support to recover access.");
        }
        AuthUserJson linked = linkedAccounts.isEmpty() ? null : linkedAccounts.get(0);
        if (linked != null) {
            long emailMatches = users.stream().filter(user -> user.email != null
                    && linked.email != null && user.email.equalsIgnoreCase(linked.email)).count();
            if (emailMatches > 1) {
                throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                        "This CineBook account data needs support review before Google sign-in can continue.");
            }
            return new AuthResponse(linked, jwtService.generateToken(linked.email,
                    linked.tokenVersion == null ? 0 : linked.tokenVersion));
        }
        if (users.stream().anyMatch(user -> user.email != null && user.email.equalsIgnoreCase(identity.email()))) {
            throw new IllegalStateException("An account already uses this email. Sign in with that account before linking Google.");
        }
        AuthUserJson user = new AuthUserJson();
        user.id = users.stream().mapToInt(item -> item.id).max().orElse(0) + 1;
        user.email = identity.email().trim().toLowerCase(Locale.ROOT);
        String displayName = identity.name() == null || identity.name().isBlank() ? "CineBook member" : identity.name().trim();
        String base = displayName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "").replaceAll("[._-]{2,}", "-");
        if (base.length() < 3) base = "cineuser";
        base = base.substring(0, Math.min(base.length(), 32));
        String username;
        do { username = base + "-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8); }
        while (usernameExists(users, username));
        user.username = username;
        user.name = displayName;
        user.role = "USER"; user.googleSubject = identity.subject(); user.emailVerified = true;
        user.createdAt = Instant.now().toString(); user.linkedProviders = new java.util.ArrayList<>(List.of("google"));
        AuthUserJson saved = jsonLoaderService.addAuthUser(user);
        return new AuthResponse(saved, jwtService.generateToken(saved.email, 0));
    }

    private static boolean usernameExists(List<AuthUserJson> users, String username) {
        return users.stream().anyMatch(existing -> existing.username != null && existing.username.equalsIgnoreCase(username));
    }

    public synchronized AuthUserJson linkGoogle(int userId, GoogleIdentityService.GoogleAccount identity, String currentPassword) {
        List<AuthUserJson> users = jsonLoaderService.getData("data/auth-users.json", AuthUserJson.class);
        AuthUserJson user = users.stream().filter(item -> item.id == userId).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Account not found."));
        if (user.passwordHash == null || currentPassword == null || !passwordEncoder.matches(currentPassword, user.passwordHash))
            throw new BadCredentialsException("Re-enter your CineBook password to link Google.");
        if (user.email == null || !user.email.equalsIgnoreCase(identity.email()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "This Google account cannot be linked to the signed-in CineBook account.");
        boolean alreadyLinked = users.stream().anyMatch(item -> identity.subject().equals(item.googleSubject) && item.id != userId);
        if (alreadyLinked) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "This Google account is already linked to another CineBook account.");
        boolean changed = false;
        if (!identity.subject().equals(user.googleSubject)) {
            user.googleSubject = identity.subject();
            changed = true;
        }
        if (!Boolean.TRUE.equals(user.emailVerified)) {
            user.emailVerified = true;
            changed = true;
        }
        if (user.linkedProviders == null) user.linkedProviders = new java.util.ArrayList<>();
        if (!user.linkedProviders.contains("google")) {
            user.linkedProviders.add("google");
            changed = true;
        }
        return changed ? jsonLoaderService.updateAuthUser(user) : user;
    }

    public AuthUserJson findAccountByEmail(String email) {
        if (email == null) return null;
        return jsonLoaderService.getData("data/auth-users.json", AuthUserJson.class).stream()
                .filter(item -> item.email != null && item.email.equalsIgnoreCase(email.trim())).findFirst().orElse(null);
    }

    public synchronized void markEmailVerified(int userId) {
        AuthUserJson user = jsonLoaderService.getData("data/auth-users.json", AuthUserJson.class).stream()
                .filter(item -> item.id == userId).findFirst().orElseThrow(() -> new IllegalArgumentException("Account not found."));
        user.emailVerified = true;
        jsonLoaderService.updateAuthUser(user);
    }

    public synchronized void resetPassword(int userId, String newPassword) {
        if (newPassword == null || newPassword.length() < 10 || newPassword.length() > 72
                || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || List.of("password123", "qwerty1234", "1234567890", "letmein1234").contains(newPassword.toLowerCase(Locale.ROOT)))
            throw new IllegalArgumentException("Choose a password between 10 and 72 characters that is not commonly used.");
        AuthUserJson user = jsonLoaderService.getData("data/auth-users.json", AuthUserJson.class).stream()
                .filter(item -> item.id == userId).findFirst().orElseThrow(() -> new IllegalArgumentException("Account not found."));
        user.passwordHash = passwordEncoder.encode(newPassword);
        user.tokenVersion = user.tokenVersion == null ? 1 : user.tokenVersion + 1;
        jsonLoaderService.updateAuthUser(user);
    }
}
