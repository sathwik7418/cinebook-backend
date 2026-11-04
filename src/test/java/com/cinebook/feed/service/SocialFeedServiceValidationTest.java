package com.cinebook.feed.service;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.feed.config.SocialDatabaseProperties;
import com.cinebook.feed.dto.FeedDtos.CreatePostRequest;
import com.cinebook.feed.dto.FeedDtos.PollRequest;
import com.cinebook.feed.dto.FeedDtos.UpdatePostRequest;
import com.cinebook.feed.dto.FeedDtos.ProfileUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.sql.Timestamp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class SocialFeedServiceValidationTest {
    private SocialFeedService service;
    private JdbcTemplate jdbc;
    private TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        tx = mock(TransactionTemplate.class);
        JsonLoaderService json = mock(JsonLoaderService.class);
        when(json.getData(anyString(), any())).thenReturn(List.of());
        service = new SocialFeedService(jdbc, tx, json,
                new SocialDatabaseProperties(false, "", "", ""), "./target/test-social-media");
    }

    @Test
    void rejectsSelfFollowBeforeAnySocialWrite() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.follow(7, 7, true));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsInvalidPublicHandleBeforeProfileWrite() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateProfile(7, new ProfileUpdateRequest("Film Fan", "id-7", "Bio", null, false)));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsEmptyPostWithoutPersisting() {
        CreatePostRequest request = new CreatePostRequest("  ", "public", false, "DISCUSSION", null, null, List.of(), null);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.createPost(7, request));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void rejectsDuplicatePollChoices() {
        PollRequest poll = new PollRequest("Which film?", List.of("Dune", "dune"), null);
        CreatePostRequest request = new CreatePostRequest("Pick one", "public", false, "POLL", null, null, List.of(), poll);
        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.createPost(7, request));
        assertEquals(400, error.getStatusCode().value());
    }

    @Test
    void anotherUsersPostCannotBeEdited() {
        UUID postId = UUID.randomUUID();
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(tx).execute(any());
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(postId))).thenReturn(88);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updatePost(7, postId, new UpdatePostRequest("attempted edit", null, null, null)));

        assertEquals(404, error.getStatusCode().value());
        verify(jdbc).query(anyString(), any(ResultSetExtractor.class), eq(postId));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void ownerCanEditPostAndPersistVisibilityAndSpoiler() {
        UUID postId = UUID.randomUUID();
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(tx).execute(any());
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(postId))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            return sql.startsWith("SELECT author_id") ? 7 : null;
        });
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (!sql.startsWith("SELECT p.id,p.author_id")) return List.of();
            Timestamp created = Timestamp.from(java.time.Instant.parse("2025-01-01T00:00:00Z"));
            Map<String, Object> row = new HashMap<>();
            row.put("id", postId);
            row.put("author_id", 7);
            row.put("content", "Updated CineTake");
            row.put("visibility", "FOLLOWERS_ONLY");
            row.put("spoiler_flag", true);
            row.put("post_type", "DISCUSSION");
            row.put("created_at", created);
            row.put("updated_at", Timestamp.from(created.toInstant().plusSeconds(60)));
            row.put("username", "filmfan");
            row.put("display_name", "Film Fan");
            row.put("likes", 0);
            row.put("comments", 0);
            row.put("reposts", 0);
            row.put("liked", false);
            row.put("saved", false);
            row.put("reposted", false);
            return List.of(row);
        });

        var updated = service.updatePost(7, postId,
                new UpdatePostRequest("Updated CineTake", "followers", true, null));

        assertEquals("Updated CineTake", updated.text());
        assertEquals("followers", updated.visibility());
        assertEquals(true, updated.spoiler());
        verify(jdbc).update(org.mockito.ArgumentMatchers.contains(
                "spoiler_flag=COALESCE(?,spoiler_flag)"), any(Object[].class));
    }

    @Test
    void anotherUserCannotDeletePost() {
        UUID postId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deletePost(7, postId));

        assertEquals(404, error.getStatusCode().value());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("WHERE id=? AND author_id=? AND deleted_at IS NULL"));
    }

    @Test
    void ownerDeleteUsesSoftDeleteAndOwnerScope() {
        UUID postId = UUID.randomUUID();
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        service.deletePost(7, postId);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).update(sql.capture(), any(Object[].class));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("SET deleted_at=now()"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("author_id=?"));
    }

    @Test
    void commentMutationRequiresOwnershipBeforeWriting() {
        UUID commentId = UUID.randomUUID();
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteComment(7, commentId));

        assertEquals(404, error.getStatusCode().value());
        verify(jdbc).queryForList(anyString(), any(Object[].class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void feedSqlSeparatesVisibilityAndBlockPredicates() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.feed(7, "latest", null, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains(
                "visibility='FOLLOWERS_ONLY' AND EXISTS(SELECT 1 FROM user_follows vf WHERE vf.follower_id=7 AND vf.followed_id=p.author_id))) AND NOT EXISTS(SELECT 1 FROM user_blocks"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().endsWith(
                "ORDER BY feed_score DESC,created_at DESC,id DESC LIMIT ?"));
    }

    @Test
    void homeFeedDoesNotFilterSpoilerPostsAndStillExcludesDeletedContent() {
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());

        service.feed(7, "latest", null, 20);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), any(Object[].class));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("p.spoiler_flag"));
        org.junit.jupiter.api.Assertions.assertTrue(sql.getValue().contains("p.deleted_at IS NULL"));
        org.junit.jupiter.api.Assertions.assertFalse(sql.getValue().contains("AND p.spoiler_flag"));
    }
}
