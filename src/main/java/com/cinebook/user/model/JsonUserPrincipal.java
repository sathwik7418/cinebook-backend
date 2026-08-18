package com.cinebook.user.model;

import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class JsonUserPrincipal implements UserDetails {

    private final AuthUserJson user;

    public JsonUserPrincipal(AuthUserJson user) {
        this.user = user;
    }

    public AuthUserJson getUser() {
        return user;
    }

    public int getId() {
        return user.id;
    }

    public String getEmail() {
        return user.email;
    }

    public String getDisplayUsername() {
        return user.username;
    }

    public String getRole() {
        return user.role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String role = user.role == null ? "USER" : user.role;

        return List.of(
                new SimpleGrantedAuthority(
                        "ROLE_" + role.toUpperCase()
                )
        );
    }

    @Override
    public String getPassword() {
        return user.passwordHash;
    }

    @Override
    public String getUsername() {
        return user.email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
