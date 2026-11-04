package com.cinebook.user.service;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import com.cinebook.config.JwtService;
import com.cinebook.user.dto.LoginRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthServiceGoogleTest {
    private JsonLoaderService json;
    private PasswordEncoder passwordEncoder;
    private JwtService jwt;
    private AuthService service;
    private List<AuthUserJson> users;

    @BeforeEach
    void setUp() {
        json = mock(JsonLoaderService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwt = mock(JwtService.class);
        service = new AuthService(json, passwordEncoder, jwt);
        users = new ArrayList<>();
        when(json.getData("data/auth-users.json", AuthUserJson.class)).thenReturn(users);
        when(json.addAuthUser(any())).thenAnswer(invocation -> {
            AuthUserJson user = invocation.getArgument(0);
            users.add(user);
            return user;
        });
        when(json.updateAuthUser(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwt.generateToken(anyString(), anyInt())).thenReturn("cinebook-test-session");
    }

    @Test
    void newGoogleUserGetsCanonicalIdVerifiedEmailAndNoPasswordCredential() {
        AuthUserJson existing = account(12, "existing@example.com", "existing-subject");
        users.add(existing);
        var response = service.googleLogin(identity("google-subject-123", "alice.private@example.com", "Alice Example"));

        assertEquals(13, response.getId());
        assertEquals("Alice Example", users.get(1).name);
        assertEquals("alice.private@example.com", users.get(1).email);
        assertTrue(users.get(1).emailVerified);
        assertNull(users.get(1).passwordHash);
        assertEquals("google-subject-123", users.get(1).googleSubject);
        assertFalse(users.get(1).username.contains("alice.private"), "public handle must not expose the email local part");
        assertEquals("cinebook-test-session", response.getToken());
    }

    @Test
    void existingEmailPasswordLoginStillUsesTheSameCanonicalAccount() {
        AuthUserJson passwordUser = account(29, "local@example.com", null);
        passwordUser.passwordHash = "bcrypt-hash";
        passwordUser.tokenVersion = 3;
        users.add(passwordUser);
        when(passwordEncoder.matches("correct password", "bcrypt-hash")).thenReturn(true);
        when(jwt.generateToken("local@example.com", 3)).thenReturn("existing-session");
        LoginRequest request = new LoginRequest();
        request.setEmail("LOCAL@example.com");
        request.setPassword("correct password");

        var response = service.login(request);

        assertEquals(29, response.getId());
        assertEquals("existing-session", response.getToken());
        verify(json, never()).addAuthUser(any());
    }

    @Test
    void returningGoogleSubjectReusesAccountAndExistingEmailRequiresExplicitLink() {
        AuthUserJson linked = account(42, "alice@example.com", "stable-google-subject");
        users.add(linked);
        var response = service.googleLogin(identity("stable-google-subject", "alice@example.com", "Alice"));
        assertEquals(42, response.getId());
        verify(json, never()).addAuthUser(any());

        AuthUserJson passwordAccount = account(43, "owner@example.com", null);
        passwordAccount.passwordHash = "bcrypt-hash";
        users.add(passwordAccount);
        IllegalStateException conflict = assertThrows(IllegalStateException.class,
                () -> service.googleLogin(identity("new-subject", "owner@example.com", "Owner")));
        assertTrue(conflict.getMessage().contains("Sign in with that account"));
        verify(json, never()).addAuthUser(any());
    }

    @Test
    void duplicateProviderSubjectFailsClosedInsteadOfChoosingAnArbitraryAccount() {
        users.add(account(51, "first@example.com", "duplicate-subject"));
        users.add(account(52, "second@example.com", "duplicate-subject"));

        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> service.googleLogin(identity("duplicate-subject", "google@example.com", "Google User")));

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        verify(json, never()).addAuthUser(any());
        verify(jwt, never()).generateToken(anyString(), anyInt());
    }

    @Test
    void duplicateStoredEmailsFailClosedWhenResolvingLinkedGoogleIdentity() {
        users.add(account(61, "same@example.com", "stable-subject"));
        users.add(account(62, "SAME@example.com", null));

        ResponseStatusException conflict = assertThrows(ResponseStatusException.class,
                () -> service.googleLogin(identity("stable-subject", "same@example.com", "Member")));

        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
        verify(jwt, never()).generateToken(anyString(), anyInt());
    }

    @Test
    void accountLinkingRequiresPasswordMatchingVerifiedEmailAndUniqueSubject() {
        AuthUserJson owner = account(17, "owner@example.com", null);
        owner.passwordHash = "bcrypt-hash";
        users.add(owner);
        when(passwordEncoder.matches("correct password", "bcrypt-hash")).thenReturn(true);
        when(passwordEncoder.matches("wrong password", "bcrypt-hash")).thenReturn(false);

        assertThrows(BadCredentialsException.class,
                () -> service.linkGoogle(17, identity("subject", "owner@example.com", "Owner"), "wrong password"));
        assertThrows(BadCredentialsException.class,
                () -> service.linkGoogle(17, identity("subject", "owner@example.com", "Owner"), null));
        ResponseStatusException mismatch = assertThrows(ResponseStatusException.class,
                () -> service.linkGoogle(17, identity("subject", "other@example.com", "Owner"), "correct password"));
        assertEquals(HttpStatus.CONFLICT, mismatch.getStatusCode());

        AuthUserJson another = account(18, "another@example.com", "subject");
        users.add(another);
        ResponseStatusException duplicate = assertThrows(ResponseStatusException.class,
                () -> service.linkGoogle(17, identity("subject", "owner@example.com", "Owner"), "correct password"));
        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());

        service.linkGoogle(17, identity("fresh-subject", "owner@example.com", "Owner"), "correct password");
        assertEquals("fresh-subject", owner.googleSubject);
        assertTrue(owner.linkedProviders.contains("google"));
        assertTrue(owner.emailVerified);
        verify(json).updateAuthUser(owner);
    }

    private static AuthUserJson account(int id, String email, String subject) {
        AuthUserJson user = new AuthUserJson();
        user.id = id; user.email = email; user.username = "user" + id; user.name = "Member";
        user.role = "USER"; user.googleSubject = subject; user.linkedProviders = new ArrayList<>(List.of("password"));
        return user;
    }

    private static GoogleIdentityService.GoogleAccount identity(String subject, String email, String name) {
        return new GoogleIdentityService.GoogleAccount(subject, email, name);
    }
}
