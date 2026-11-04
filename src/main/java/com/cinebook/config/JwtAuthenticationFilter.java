package com.cinebook.config;

import com.cinebook.user.model.JsonUserPrincipal;
import com.cinebook.user.service.JsonUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final JsonUserService jsonUserService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String token = bearerToken(authHeader);
        if (token == null) {
            if (isChatRequest(request) && !"OPTIONS".equalsIgnoreCase(request.getMethod())) {
                log.warn("chat_auth_rejected method={} path={} reason={}",
                        request.getMethod(), request.getRequestURI(),
                        authHeader == null ? "missing_authorization" : "unsupported_authorization_scheme");
            }
            filterChain.doFilter(request, response);
            return;
        }

        try {

            String email = jwtService.extractEmail(token);

            if (email != null &&
                    SecurityContextHolder
                            .getContext()
                            .getAuthentication() == null) {

                JsonUserPrincipal user =
                        jsonUserService.findUserByEmail(email);

                if (user != null) {
                    boolean tokenValid = jwtService.isTokenValid(token, email,
                            user.getUser().tokenVersion == null ? 0 : user.getUser().tokenVersion);

                    if (tokenValid) {

                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        user,
                                        null,
                                        user.getAuthorities()
                                );

                        authToken.setDetails(
                                new WebAuthenticationDetailsSource()
                                        .buildDetails(request)
                        );

                        SecurityContextHolder
                                .getContext()
                                .setAuthentication(authToken);
                        if (isPollVoteRequest(request)) {
                            log.info("chat_auth_accepted method={} path={} principal_type={}",
                                    request.getMethod(), request.getRequestURI(), "JsonUserPrincipal");
                        }
                    } else if (isChatRequest(request)) {
                        log.warn("chat_auth_rejected method={} path={} reason=invalid_or_expired_bearer",
                                request.getMethod(), request.getRequestURI());
                    }
                } else if (isChatRequest(request)) {
                    log.warn("chat_auth_rejected method={} path={} reason=account_not_found",
                            request.getMethod(), request.getRequestURI());
                }
            }

        } catch (Exception ignored) {
            // Invalid JWT is treated as unauthenticated.
            if (isChatRequest(request)) {
                log.warn("chat_auth_rejected method={} path={} reason=invalid_bearer",
                        request.getMethod(), request.getRequestURI());
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String bearerToken(String authorization) {
        if (authorization == null) return null;
        int separator = authorization.indexOf(' ');
        if (separator <= 0 || !"Bearer".equalsIgnoreCase(authorization.substring(0, separator))) return null;
        String token = authorization.substring(separator + 1).trim();
        return token.isEmpty() ? null : token;
    }

    private static boolean isChatRequest(HttpServletRequest request) {
        return request.getRequestURI().startsWith(request.getContextPath() + "/api/chat/");
    }

    private static boolean isPollVoteRequest(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().matches(".*/api/chat/conversations/[^/]+/polls/[^/]+/votes");
    }
}
