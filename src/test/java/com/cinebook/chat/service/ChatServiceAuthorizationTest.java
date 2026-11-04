package com.cinebook.chat.service;

import com.cinebook.feed.service.SocialFeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class ChatServiceAuthorizationTest {
    private JdbcTemplate jdbc;
    private SocialFeedService feed;
    private ChatService service;

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        feed = mock(SocialFeedService.class);
        service = new ChatService(jdbc, mock(TransactionTemplate.class), feed, new ChatEventHub());
    }

    @Test
    void directConversationRejectsSelfChatBeforePersistence() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.direct(17, 17));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
        verifyNoInteractions(jdbc, feed);
    }

    @Test
    void guessedConversationIdDoesNotRevealConversationDetails() {
        UUID guessedId = UUID.randomUUID();
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), any(UUID.class), eq(17)))
                .thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.details(17, guessedId));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verifyNoInteractions(feed);
    }

    @Test
    void regularMemberCannotPromoteAnotherGroupMember() {
        UUID conversationId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(conversationId), eq(17))).thenReturn("MEMBER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.setMemberRole(17, conversationId, 18, "ADMIN"));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }

    @Test
    void regularMemberCannotRemoveGroupAvatar() {
        UUID conversationId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(conversationId), eq(17))).thenReturn("MEMBER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.removeGroupAvatar(17, conversationId));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void regularMemberCannotDeleteGroup() {
        UUID conversationId = UUID.randomUUID();
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(conversationId), eq(17))).thenReturn("MEMBER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deleteGroup(17, conversationId));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void regularMemberCannotReplaceGroupAvatar() {
        UUID conversationId = UUID.randomUUID();

        when(jdbc.query(anyString(), any(ResultSetExtractor.class), eq(conversationId), eq(17))).thenReturn("MEMBER");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.updateGroupAvatar(17, conversationId, UUID.randomUUID()));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void nonMemberCannotVoteInConversationPoll() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();

        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(conversationId), eq(17))).thenReturn(false);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.vote(17, conversationId, messageId,
                        new com.cinebook.chat.dto.ChatDtos.VoteRequest(java.util.List.of(UUID.randomUUID()))));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(jdbc, never()).update(anyString(), any(Object[].class));
    }

    @Test
    void repeatedAndChangedVotesReplaceSelectionsIdempotentlyInsideTransactions() {
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID firstOption = UUID.randomUUID();
        UUID secondOption = UUID.randomUUID();
        TransactionTemplate tx = mock(TransactionTemplate.class);
        service = new ChatService(jdbc, tx, feed, new ChatEventHub());

        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(conversationId), eq(17))).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(17), eq(17), eq(17), eq(conversationId))).thenReturn(false);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(messageId), eq(conversationId))).thenReturn(true);
        when(jdbc.queryForObject(anyString(), eq(Boolean.class), eq(messageId), any(UUID.class))).thenReturn(true);
        when(jdbc.queryForMap(anyString(), eq(messageId))).thenReturn(Map.of("question", "Movie?", "multiple_choice", false));
        when(jdbc.<com.cinebook.chat.dto.ChatDtos.PollOptionDto>query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(tx.execute(any(TransactionCallback.class))).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });

        var firstVote = new com.cinebook.chat.dto.ChatDtos.VoteRequest(List.of(firstOption));
        service.vote(17, conversationId, messageId, firstVote);
        service.vote(17, conversationId, messageId, firstVote); // retry/duplicate selection
        service.vote(17, conversationId, messageId,
                new com.cinebook.chat.dto.ChatDtos.VoteRequest(List.of(secondOption))); // change vote

        verify(tx, times(3)).execute(any(TransactionCallback.class));
        verify(jdbc, times(3)).update(contains("DELETE FROM cinechat.chat_poll_votes"), eq(17), eq(messageId));
        verify(jdbc, times(3)).update(contains("INSERT INTO cinechat.chat_poll_votes"), any(), eq(17));
    }
}
