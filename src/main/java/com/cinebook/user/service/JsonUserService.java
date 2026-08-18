package com.cinebook.user.service;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import com.cinebook.user.model.JsonUserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JsonUserService {

    private final JsonLoaderService jsonLoaderService;

    public JsonUserPrincipal findUserByEmail(String email) {

        if (email == null || email.isBlank()) {
            return null;
        }

        List<AuthUserJson> users =
                jsonLoaderService.getData(
                        "data/auth-users.json",
                        AuthUserJson.class
                );

        AuthUserJson user = users.stream()
                .filter(u -> u.email != null)
                .filter(u -> u.email.equalsIgnoreCase(email))
                .findFirst()
                .orElse(null);

        return user == null
                ? null
                : new JsonUserPrincipal(user);
    }
}