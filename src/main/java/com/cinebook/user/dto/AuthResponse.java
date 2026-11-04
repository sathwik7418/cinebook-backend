package com.cinebook.user.dto;

import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import com.cinebook.user.model.JsonUserPrincipal;

public class AuthResponse {

    private Integer id;
    private String username;
    private String email;
    private String name;
    private String role;
    private String token;
    private boolean emailVerified;
    private boolean verificationEmailSent;

    public AuthResponse(AuthUserJson user) {
        this(user, null);
    }

    public AuthResponse(AuthUserJson user, String token) {
        this.id = user.id;
        this.username = user.username;
        this.email = user.email;
        this.name = user.name;
        this.emailVerified = Boolean.TRUE.equals(user.emailVerified);
        this.role = user.role;
        this.token = token;
    }

    public AuthResponse(JsonUserPrincipal user) {
        this(user.getUser(), null);
    }

    public AuthResponse(JsonUserPrincipal user, String token) {
        this(user.getUser(), token);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getToken() {
        return token;
    }

    public boolean isEmailVerified() { return emailVerified; }
    public void setEmailVerified(boolean emailVerified) { this.emailVerified = emailVerified; }
    public boolean isVerificationEmailSent() { return verificationEmailSent; }
    public void setVerificationEmailSent(boolean verificationEmailSent) { this.verificationEmailSent = verificationEmailSent; }

    public void setToken(String token) {
        this.token = token;
    }
}
