package com.cinebook.security;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.config.JwtAuthenticationFilter;
import com.cinebook.config.JwtService;
import com.cinebook.config.SecurityConfig;
import com.cinebook.chat.dto.ChatDtos.PollDto;
import com.cinebook.chat.dto.ChatDtos.PollOptionDto;
import com.cinebook.chat.dto.ChatDtos.ConversationDto;
import com.cinebook.chat.dto.ChatDtos.PersonDto;
import com.cinebook.chat.service.ChatEventHub;
import com.cinebook.chat.service.ChatService;
import com.cinebook.feed.dto.FeedDtos.FeedPage;
import com.cinebook.feed.dto.FeedDtos.PreferencesDto;
import com.cinebook.feed.dto.FeedDtos.UserDto;
import com.cinebook.feed.dto.FeedDtos.UserPage;
import com.cinebook.feed.service.SocialFeedService;
import com.cinebook.user.model.JsonUserPrincipal;
import com.cinebook.user.service.AccountTokenService;
import com.cinebook.user.service.AuthService;
import com.cinebook.user.service.GoogleIdentityService;
import com.cinebook.user.service.JsonUserService;
import com.cinebook.common.security.AuthRateLimiter;
import com.cinebook.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(properties = {
        "cinebook.social.database.enabled=true",
        "cinebook.chat.enabled=true",
        "cinebook.storage.directory=target/jwt-feed-auth-test-data",
        "jwt.secret=test-only-secret-key-with-more-than-32-bytes",
        "jwt.expiration-ms=86400000"
}, controllers = {
        com.cinebook.user.controller.AuthController.class,
        com.cinebook.feed.controller.SocialFeedController.class,
        com.cinebook.chat.controller.ChatController.class
})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtService.class,
        JsonUserService.class, JsonLoaderService.class, AuthService.class})
class JwtFeedAuthenticationIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;

    @MockBean private AuthRateLimiter rateLimiter;
    @MockBean private GoogleIdentityService googleIdentityService;
    @MockBean private AccountTokenService accountTokenService;
    @MockBean private NotificationService notifications;
    @MockBean private SocialFeedService feed;
    @MockBean private ChatService chat;
    @MockBean private ChatEventHub chatEvents;

    private String token;
    private int userId;

    @BeforeEach
    void setUp() throws Exception {
        String username = "jwt_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String email = "jwt-test-" + UUID.randomUUID().toString().replace("-", "") + "@example.invalid";
        String password = temporaryPassword();
        MvcResult registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "email", email,
                                "password", password, "name", "JWT Filter Test"))))
                .andExpect(status().isOk()).andReturn();
        var response = mapper.readTree(registration.getResponse().getContentAsString());
        token = response.path("token").asText();
        userId = response.path("id").asInt();
        when(feed.me(any(JsonUserPrincipal.class))).thenAnswer(invocation -> {
            JsonUserPrincipal user = invocation.getArgument(0);
            return new UserDto(String.valueOf(user.getId()), "JWT Filter Test", username, "JT", "blue",
                    "", false, "", 0, 0, false, false, false);
        });
        when(feed.feed(eq(userId), eq("for-you"), isNull(), eq(20)))
                .thenReturn(new FeedPage(List.of(), null, false));
        when(feed.preferences(userId)).thenReturn(new PreferencesDto(List.of(), List.of(), List.of(), false));
        when(feed.suggestions(userId, 5)).thenReturn(new UserPage(List.of(), null, false));
    }

    @Test
    void sameJwtAuthenticatesAuthAndFeedThroughTheSecurityFilterChain() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(userId));
        mvc.perform(get("/api/feed/me").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(String.valueOf(userId)));
        mvc.perform(get("/api/feed").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
        mvc.perform(get("/api/feed/preferences").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.hideSpoilers").value(false));
        mvc.perform(get("/api/feed/users/suggestions").header("Authorization", bearer()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void googleLoginIsPublicWhileGoogleAccountLinkingRequiresAuthentication() throws Exception {
        String nonce = "n".repeat(32);
        when(googleIdentityService.verify("signed-google-test-id-token", nonce))
                .thenReturn(new GoogleIdentityService.GoogleAccount(
                        "google-subject-" + UUID.randomUUID(),
                        "google-test-" + UUID.randomUUID() + "@example.invalid",
                        "Google Test User"));
        mvc.perform(post("/api/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("idToken", "signed-google-test-id-token", "nonce", nonce))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        mvc.perform(post("/api/auth/google/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("idToken", "signed-google-test-id-token", "nonce", nonce))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedConversationListReturnsDirectAndGroupDtosAndRejectsAnonymousRequest() throws Exception {
        when(chat.conversations(userId)).thenReturn(List.of(
                new ConversationDto("direct-1", "direct", "Friend", null,
                        new PersonDto("42", "Friend", "friend", null), "Hello", null, 0),
                new ConversationDto("group-1", "group", "Film club", "/group.png", null,
                        "Poll: Friday?", null, 2)));

        mvc.perform(get("/api/chat/conversations").header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].kind").value("direct"))
                .andExpect(jsonPath("$[0].counterpart.id").value("42"))
                .andExpect(jsonPath("$[1].kind").value("group"))
                .andExpect(jsonPath("$[1].lastMessage").value("Poll: Friday?"));
        mvc.perform(get("/api/chat/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void missingAndInvalidJwtAreRejectedOnFeedRoutes() throws Exception {
        mvc.perform(get("/api/feed/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/feed/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validJwtCanVoteAndMissingOrInvalidJwtIsRejectedOnExactChatRoute() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID pollId = UUID.randomUUID();
        String optionId = UUID.randomUUID().toString();
        when(chat.vote(eq(userId), eq(conversationId), eq(pollId), any()))
                .thenReturn(new PollDto(pollId.toString(), "Which movie?", false, null, false,
                        List.of(new PollOptionDto(optionId, "Arrival", 1, true))));
        String path = "/api/chat/conversations/" + conversationId + "/polls/" + pollId + "/votes";
        String body = mapper.writeValueAsString(Map.of("optionIds", List.of(optionId)));

        mvc.perform(post(path).header("Authorization", bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messageId").value(pollId.toString()))
                .andExpect(jsonPath("$.options[0].selected").value(true));
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void bearerAuthenticationSchemeIsCaseInsensitiveOnPollVoteRoute() throws Exception {
        UUID conversationId = UUID.randomUUID();
        UUID pollId = UUID.randomUUID();
        String optionId = UUID.randomUUID().toString();
        when(chat.vote(eq(userId), eq(conversationId), eq(pollId), any()))
                .thenReturn(new PollDto(pollId.toString(), "Which movie?", false, null, false,
                        List.of(new PollOptionDto(optionId, "Arrival", 1, true))));
        String path = "/api/chat/conversations/" + conversationId + "/polls/" + pollId + "/votes";

        mvc.perform(post(path).header("Authorization", "bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("optionIds", List.of(optionId)))))
                .andExpect(status().isOk());
    }

    private String bearer() {
        return "Bearer " + token;
    }

    private String temporaryPassword() {
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(random) + "!Q7";
    }
}
