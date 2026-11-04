package com.cinebook.chat.service;

import com.cinebook.chat.dto.ChatDtos.ConversationDto;
import com.cinebook.feed.service.SocialFeedService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatServiceConversationListTest {
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
    @SuppressWarnings({"unchecked", "rawtypes"})
    void listMapsDirectGroupNullAndPollPreviewAndProjectsMessageIdUsedByPreview() throws Exception {
        ResultSet directRow = row("DIRECT", "Peer", "6", "Peer", "peer", "/avatar/6", null,
                "hello", Timestamp.from(Instant.parse("2026-11-04T10:00:00Z")), 1);
        when(directRow.getInt("counterpart_id")).thenReturn(6);
        ResultSet groupRow = row("GROUP", "Film club", null, null, null, null, "/group.png",
                "Poll: Friday screening?", null, 0);
        ResultSet emptyRow = row("GROUP", "Quiet group", null, null, null, null, null,
                null, null, 0);

        doAnswer(invocation -> {
            RowMapper mapper = invocation.getArgument(1);
            return List.of(mapper.mapRow(directRow, 0), mapper.mapRow(groupRow, 1), mapper.mapRow(emptyRow, 2));
        }).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));

        List<ConversationDto> result = service.conversations(24);

        assertEquals(3, result.size());
        assertEquals("direct", result.get(0).kind());
        assertEquals("6", result.get(0).counterpart().id());
        assertEquals("hello", result.get(0).lastMessage());
        assertEquals("group", result.get(1).kind());
        assertEquals("/group.png", result.get(1).avatar());
        assertEquals("Poll: Friday screening?", result.get(1).lastMessage());
        assertNull(result.get(1).counterpart());
        assertNull(result.get(2).lastMessage());
        assertNull(result.get(2).lastMessageAt());

        var sql = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(jdbc).query(sql.capture(), any(RowMapper.class), any(Object[].class));
        assertTrue(sql.getValue().contains("SELECT id,body FROM cinechat.messages"),
                "last-message lateral query must expose every column referenced by m.id and m.body");
        assertTrue(sql.getValue().contains("mine.user_id=? AND mine.left_at IS NULL"),
                "conversation listing must remain scoped to active memberships");
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void usersWithNoConversationsReceiveAnEmptyList() {
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        assertTrue(service.conversations(24).isEmpty());
    }

    private static ResultSet row(String kind, String label, String counterpartId, String counterpartName,
                                 String handle, String avatar, String groupAvatar, String lastMessage,
                                 Timestamp lastMessageAt, long unread) throws Exception {
        ResultSet row = mock(ResultSet.class);
        when(row.getString("id")).thenReturn("00000000-0000-0000-0000-000000000001");
        when(row.getString("kind")).thenReturn(kind);
        when(row.getString("label")).thenReturn(label);
        when(row.getString("counterpart_id")).thenReturn(counterpartId);
        when(row.getString("handle")).thenReturn(handle);
        when(row.getString("avatar")).thenReturn(avatar);
        when(row.getString("group_avatar")).thenReturn(groupAvatar);
        when(row.getString("last_body")).thenReturn(lastMessage);
        when(row.getTimestamp("last_message_at")).thenReturn(lastMessageAt);
        when(row.getLong("unread")).thenReturn(unread);
        if (counterpartId != null) when(row.getString("label")).thenReturn(counterpartName);
        return row;
    }
}
