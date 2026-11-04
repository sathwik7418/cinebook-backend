package com.cinebook.user.controller;

import com.cinebook.user.dto.ProfileResponse;
import com.cinebook.user.model.JsonUserPrincipal;
import com.cinebook.user.service.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile/me")
public class ProfileController {
    private final AuthService accounts;
    public ProfileController(AuthService accounts) { this.accounts = accounts; }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal JsonUserPrincipal principal) {
        return ProfileResponse.from(principal.getUser());
    }

    @PatchMapping
    public ProfileResponse update(@Valid @RequestBody UpdateProfileRequest request,
                                  @AuthenticationPrincipal JsonUserPrincipal principal) {
        return ProfileResponse.from(accounts.updateProfile(principal.getId(), request.name,
                request.preferredCity, request.notificationPreferences));
    }

    public static class UpdateProfileRequest {
        @Size(max = 100) public String name;
        @Size(max = 100) public String preferredCity;
        @Size(max = 10) public Map<String, Boolean> notificationPreferences;
    }
}
