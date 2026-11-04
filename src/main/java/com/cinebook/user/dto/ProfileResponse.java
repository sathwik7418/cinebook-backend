package com.cinebook.user.dto;

import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import java.util.List;
import java.util.Map;

public record ProfileResponse(int id, String username, String name, String email,
                              boolean emailVerified, String createdAt,
                              String preferredCity, List<String> linkedProviders,
                              Map<String, Boolean> notificationPreferences) {
    public static ProfileResponse from(AuthUserJson user) {
        return new ProfileResponse(user.id, user.username, user.name, user.email,
                Boolean.TRUE.equals(user.emailVerified), user.createdAt, user.preferredCity,
                user.linkedProviders == null ? List.of("password") : List.copyOf(user.linkedProviders),
                user.notificationPreferences == null ? Map.of() : Map.copyOf(user.notificationPreferences));
    }
}
