package com.cinebook.user.controller;

import com.cinebook.user.dto.AuthResponse;
import com.cinebook.user.dto.LoginRequest;
import com.cinebook.user.dto.RegisterRequest;
import com.cinebook.user.model.JsonUserPrincipal;
import com.cinebook.user.service.AuthService;
import com.cinebook.user.service.GoogleIdentityService;
import com.cinebook.user.service.AccountTokenService;
import com.cinebook.common.security.AuthRateLimiter;
import com.cinebook.notification.NotificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthRateLimiter rateLimiter;
    private final GoogleIdentityService googleIdentityService;
    private final AccountTokenService accountTokenService;
    private final NotificationService notifications;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.check("register", servletRequest.getRemoteAddr(), 8, 60 * 60 * 1000L);
        AuthResponse response = authService.register(request);
        try { notifications.create(response.getId(), "welcome", "ACCOUNT", "Welcome to CineBook",
                "Your account is ready. Explore the movie collection and save your favorites.", "/profile"); }
        catch (RuntimeException ignored) { }
        response.setVerificationEmailSent(accountTokenService.sendVerification(response.getId(), response.getEmail()));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest
    ) {
        rateLimiter.check("login", servletRequest.getRemoteAddr(), 10, 15 * 60 * 1000L);
        AuthResponse response =
                authService.login(request);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody GoogleRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check("google", servletRequest.getRemoteAddr(), 12, 15 * 60 * 1000L);
        GoogleIdentityService.GoogleAccount identity = googleIdentityService.verify(request.idToken, request.nonce);
        return ResponseEntity.ok(authService.googleLogin(identity));
    }

    @PostMapping("/google/link")
    public ResponseEntity<Void> linkGoogle(@Valid @RequestBody GoogleRequest request,
            @AuthenticationPrincipal JsonUserPrincipal principal) {
        rateLimiter.check("google-link", String.valueOf(principal.getId()), 8, 15 * 60 * 1000L);
        GoogleIdentityService.GoogleAccount identity = googleIdentityService.verify(request.idToken, request.nonce);
        authService.linkGoogle(principal.getId(), identity, request.currentPassword);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@Valid @RequestBody TokenRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check("verify", servletRequest.getRemoteAddr(), 10, 15 * 60 * 1000L);
        accountTokenService.verifyEmail(request.token);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<java.util.Map<String, Boolean>> resendVerification(@Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check("resend", servletRequest.getRemoteAddr() + ":" + request.email.toLowerCase(), 3, 60 * 60 * 1000L);
        var user = authService.findAccountByEmail(request.email);
        if (user != null && !Boolean.TRUE.equals(user.emailVerified))
            accountTokenService.sendVerification(user.id, user.email);
        return ResponseEntity.accepted().body(java.util.Map.of("accepted", true,
                "deliveryConfigured", accountTokenService.emailProviderConfigured()));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<java.util.Map<String, Boolean>> forgotPassword(@Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check("forgot", servletRequest.getRemoteAddr() + ":" + request.email.toLowerCase(), 4, 60 * 60 * 1000L);
        accountTokenService.requestPasswordReset(request.email);
        return ResponseEntity.accepted().body(java.util.Map.of("accepted", true,
                "deliveryConfigured", accountTokenService.emailProviderConfigured()));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest servletRequest) {
        rateLimiter.check("reset", servletRequest.getRemoteAddr(), 10, 15 * 60 * 1000L);
        accountTokenService.resetPassword(request.token, request.password);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me(
            @AuthenticationPrincipal JsonUserPrincipal user
    ) {

        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                new AuthResponse(user.getUser())
        );
    }

    public static class GoogleRequest {
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 12000) public String idToken;
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 256) public String nonce;
        @jakarta.validation.constraints.Size(max = 72) public String currentPassword;
    }
    public static class TokenRequest { @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=128) public String token; }
    public static class EmailRequest { @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Email @jakarta.validation.constraints.Size(max=100) public String email; }
    public static class ResetPasswordRequest {
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max=128) public String token;
        @jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(min=10,max=72) public String password;
    }
}
