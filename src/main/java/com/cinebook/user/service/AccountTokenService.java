package com.cinebook.user.service;

import com.cinebook.common.json.JsonFileStore;
import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import com.cinebook.notification.EmailDeliveryService;
import com.cinebook.notification.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class AccountTokenService {
    private final JsonFileStore store; private final AuthService accounts; private final EmailDeliveryService email; private final Clock clock; private final NotificationService notifications;
    private final SecureRandom random = new SecureRandom();
    public AccountTokenService(JsonFileStore store, AuthService accounts, EmailDeliveryService email, Clock clock, NotificationService notifications) {
        this.store=store;this.accounts=accounts;this.email=email;this.clock=clock;this.notifications=notifications;
    }

    public boolean emailProviderConfigured() { return email.isConfigured(); }

    public synchronized boolean sendVerification(int userId, String to) {
        String token = issue(userId, "VERIFY", Duration.ofHours(24));
        String link = email.publicUrl() + "/verify-email?token=" + token;
        return email.send(to, "Verify your CineBook email", "Verify your email: " + link,
                "<h1>Welcome to CineBook</h1><p>Verify your email to complete your account setup.</p><p><a href=\"" + link + "\">Verify email</a></p>");
    }

    public synchronized void verifyEmail(String rawToken) {
        AccountToken token = find(rawToken, "VERIFY");
        if (token.expiresAt == null || Instant.parse(token.expiresAt).isBefore(Instant.now(clock))) throw invalid();
        accounts.markEmailVerified(Math.toIntExact(token.userId)); consume(token);
        try { notifications.create(token.userId, "email-verified", "ACCOUNT", "Email verified",
                "Your email address is now verified.", "/profile"); } catch (RuntimeException ignored) { }
    }

    public synchronized void requestPasswordReset(String emailAddress) {
        AuthUserJson user = accounts.findAccountByEmail(emailAddress);
        if (user == null || user.googleSubject != null && user.passwordHash == null) return;
        String token = issue(user.id, "RESET", Duration.ofMinutes(30));
        String link = email.publicUrl() + "/reset-password?token=" + token;
        email.send(user.email, "Reset your CineBook password", "Use this link to reset your password: " + link,
                "<h1>Password reset</h1><p>This link expires in 30 minutes.</p><p><a href=\"" + link + "\">Reset password</a></p>");
    }

    public synchronized void resetPassword(String rawToken, String newPassword) {
        AccountToken token = find(rawToken, "RESET");
        if (token.expiresAt == null || Instant.parse(token.expiresAt).isBefore(Instant.now(clock))) throw invalid();
        accounts.resetPassword(Math.toIntExact(token.userId), newPassword); consume(token);
        try { notifications.create(token.userId, "password-reset:" + token.createdAt, "SECURITY", "Password updated",
                "Your CineBook password was changed. Sign in again on your devices.", "/login"); } catch (RuntimeException ignored) { }
    }

    private String issue(int userId, String purpose, Duration ttl) {
        List<AccountToken> all = store.readList("account-tokens.json", AccountToken.class);
        String now = Instant.now(clock).toString();
        all.stream().filter(item -> item.userId == userId && purpose.equals(item.purpose) && item.usedAt == null)
                .forEach(item -> item.usedAt = now);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AccountToken item = new AccountToken(); item.userId=userId;item.purpose=purpose;item.tokenHash=hash(raw);
        item.expiresAt=Instant.now(clock).plus(ttl).toString();item.createdAt=now;all.add(item);
        if (all.size() > 50_000) all.removeIf(value -> value.usedAt != null || Instant.parse(value.expiresAt).isBefore(Instant.now(clock).minus(Duration.ofDays(7))));
        store.writeList("account-tokens.json", all); return raw;
    }

    private AccountToken find(String raw, String purpose) {
        if (raw == null || raw.length() < 32 || raw.length() > 128) throw invalid();
        String hash = hash(raw);
        return store.readList("account-tokens.json", AccountToken.class).stream()
                .filter(item -> purpose.equals(item.purpose) && item.usedAt == null && constantEquals(hash, item.tokenHash))
                .findFirst().orElseThrow(AccountTokenService::invalid);
    }

    private void consume(AccountToken token) {
        List<AccountToken> all = store.readList("account-tokens.json", AccountToken.class);
        all.stream().filter(item -> item.userId == token.userId && item.purpose.equals(token.purpose)
                && item.tokenHash.equals(token.tokenHash)).findFirst().ifPresent(item -> item.usedAt=Instant.now(clock).toString());
        store.writeList("account-tokens.json", all);
    }
    private static String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException("Token hashing is unavailable."); }
    }
    private static boolean constantEquals(String left, String right) {
        return right != null && MessageDigest.isEqual(left.getBytes(StandardCharsets.US_ASCII), right.getBytes(StandardCharsets.US_ASCII));
    }
    private static ResponseStatusException invalid() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "This link is invalid, expired, or already used."); }

    public static class AccountToken { public long userId; public String purpose; public String tokenHash; public String createdAt; public String expiresAt; public String usedAt; }
}
