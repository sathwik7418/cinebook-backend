package com.cinebook.chat.service;

import com.cinebook.chat.dto.ChatDtos.*;
import com.cinebook.feed.service.SocialFeedService;
import org.springframework.beans.factory.annotation.Qualifier;
import com.cinebook.chat.config.CineChatPersistenceEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Conditional(CineChatPersistenceEnabledCondition.class)
public class ChatService {
    private static final int PAGE_SIZE = 40;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final SocialFeedService feed;
    private final ChatEventHub events;

    public ChatService(@Qualifier("chatJdbcTemplate") JdbcTemplate jdbc,
                       @Qualifier("chatTransactionTemplate") TransactionTemplate tx,
                       SocialFeedService feed, ChatEventHub events) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.feed = feed;
        this.events = events;
    }

    public List<ConversationDto> conversations(int userId) {
        syncUsers(userId);
        String sql = "SELECT c.id,c.kind,c.title,c.last_message_at, " +
                "CASE WHEN c.kind='DIRECT' THEN a.id ELSE NULL END counterpart_id, " +
                "CASE WHEN c.kind='DIRECT' THEN a.display_name ELSE c.title END label, " +
                "CASE WHEN c.kind='DIRECT' THEN a.username ELSE NULL END handle, " +
                "CASE WHEN c.kind='DIRECT' AND a.avatar_reference IS NOT NULL THEN '/api/feed/users/'||a.id||'/avatar' ELSE NULL END avatar, " +
                "(SELECT '/api/feed/media/'||ca.media_id FROM cinechat.conversation_avatars ca WHERE ca.conversation_id=c.id) group_avatar, " +
                "CASE WHEN NULLIF(m.body,'') IS NOT NULL THEN m.body " +
                "WHEN EXISTS(SELECT 1 FROM cinechat.message_attachments ma WHERE ma.message_id=m.id) THEN 'Photo' " +
                "WHEN m.id IS NOT NULL THEN 'Message' END last_body,(SELECT count(*) FROM cinechat.message_receipts r JOIN cinechat.messages x ON x.id=r.message_id " +
                "WHERE r.user_id=? AND r.read_at IS NULL AND x.conversation_id=c.id AND x.sender_id<>?) unread " +
                "FROM cinechat.conversations c JOIN cinechat.conversation_members mine ON mine.conversation_id=c.id AND mine.user_id=? AND mine.left_at IS NULL " +
                "AND NOT EXISTS(SELECT 1 FROM cinechat.conversation_members blocked_peer JOIN cinefeed.user_blocks b ON " +
                "((b.blocker_id=? AND b.blocked_id=blocked_peer.user_id) OR (b.blocked_id=? AND b.blocker_id=blocked_peer.user_id)) " +
                "WHERE blocked_peer.conversation_id=c.id AND blocked_peer.user_id<>? AND blocked_peer.left_at IS NULL) " +
                "LEFT JOIN LATERAL (SELECT peer.user_id FROM cinechat.conversation_members peer WHERE peer.conversation_id=c.id AND peer.user_id<>? AND peer.left_at IS NULL ORDER BY peer.joined_at LIMIT 1) p ON true " +
                "LEFT JOIN cinefeed.social_accounts a ON a.id=p.user_id " +
                // The preview expression also uses m.id to distinguish an empty conversation
                // from a message whose body is empty (for example an attachment message).
                "LEFT JOIN LATERAL (SELECT id,body FROM cinechat.messages x WHERE x.conversation_id=c.id ORDER BY x.created_at DESC,x.id DESC LIMIT 1) m ON true " +
                "ORDER BY COALESCE(c.last_message_at,c.created_at) DESC,c.id DESC";
        return jdbc.query(sql, (rs, n) -> new ConversationDto(rs.getString("id"), rs.getString("kind").toLowerCase(Locale.ROOT),
                rs.getString("label"), "GROUP".equals(rs.getString("kind")) ? rs.getString("group_avatar") : rs.getString("avatar"),
                rs.getString("counterpart_id") == null ? null : new PersonDto(String.valueOf(rs.getInt("counterpart_id")), rs.getString("label"),
                rs.getString("handle"), rs.getString("avatar")), rs.getString("last_body"),
                instant(rs.getTimestamp("last_message_at")), rs.getLong("unread")), userId, userId, userId, userId, userId, userId, userId);
    }

    public List<PersonDto> searchUsers(int userId, String term) {
        syncUsers(userId);
        String q = term == null ? "" : term.trim();
        if (q.length() < 2) return List.of();
        String sql = "SELECT a.id,a.display_name,a.username,a.avatar_reference FROM cinefeed.social_accounts a " +
                "WHERE a.id<>? AND (a.display_name ILIKE ? OR a.username ILIKE ?) " +
                "AND NOT EXISTS (SELECT 1 FROM cinefeed.user_blocks b WHERE (b.blocker_id=? AND b.blocked_id=a.id) OR (b.blocker_id=a.id AND b.blocked_id=?)) " +
                "ORDER BY lower(a.display_name),a.id LIMIT 20";
        return jdbc.query(sql, (rs, n) -> person(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                userId, "%" + escapeLike(q) + "%", "%" + escapeLike(q) + "%", userId, userId);
    }

    public ConversationDetailsDto direct(int userId, int otherId) {
        if (userId == otherId) throw badRequest("You cannot start a chat with yourself.");
        syncUsers(userId);
        feed.user(userId, otherId);
        ensureNotBlocked(userId, otherId);
        int low = Math.min(userId, otherId), high = Math.max(userId, otherId);
        String key = low + ":" + high;
        UUID id = tx.execute(status -> {
            UUID conversationId = jdbc.queryForObject("INSERT INTO cinechat.conversations(id,kind,creator_id,direct_key) VALUES (?, 'DIRECT', ?, ?) " +
                    "ON CONFLICT(direct_key) DO UPDATE SET updated_at=now() RETURNING id", UUID.class, UUID.randomUUID(), userId, key);
            jdbc.update("INSERT INTO cinechat.conversation_members(conversation_id,user_id,role) VALUES (?,?,'ADMIN'),(?,?,'MEMBER') " +
                            "ON CONFLICT(conversation_id,user_id) DO UPDATE SET left_at=NULL", conversationId, low, conversationId, high);
            return conversationId;
        });
        return details(userId, id);
    }

    public ConversationDetailsDto createGroup(int userId, GroupRequest request) {
        syncUsers(userId);
        String title = request.name().trim();
        String description = request.description() == null ? null : request.description().trim();
        if (title.isEmpty()) throw badRequest("Enter a group name.");
        if (description != null && description.length() > 500) throw badRequest("Group description must be 500 characters or fewer.");
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        ids.add(userId);
        if (request.userIds() != null) ids.addAll(request.userIds());
        if (ids.size() < 2 || ids.size() > 50) throw badRequest("A group needs 2 to 50 members.");
        for (int id : ids) {
            if (id != userId) { feed.user(userId, id); ensureNotBlocked(userId, id); }
        }
        UUID conversationId = tx.execute(status -> {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO cinechat.conversations(id,kind,title,creator_id,description) VALUES (?,'GROUP',?,?,?)", id, title, userId, description);
            for (int member : ids) jdbc.update("INSERT INTO cinechat.conversation_members(conversation_id,user_id,role) VALUES (?,?,?)",
                    id, member, member == userId ? "ADMIN" : "MEMBER");
            return id;
        });
        publish(conversationId, "conversation.created", Map.of("id", conversationId.toString()));
        return details(userId, conversationId);
    }

    public ConversationDetailsDto details(int userId, UUID conversationId) {
        requireActiveMember(userId, conversationId);
        Map<String, Object> row = jdbc.queryForMap("SELECT c.id,c.kind,c.title,c.created_at,c.description,(SELECT '/api/feed/media/'||ca.media_id FROM cinechat.conversation_avatars ca WHERE ca.conversation_id=c.id) avatar FROM cinechat.conversations c WHERE c.id=?", conversationId);
        List<MemberDto> members = jdbc.query("SELECT a.id,a.display_name,a.username,a.avatar_reference,m.role FROM cinechat.conversation_members m " +
                        "JOIN cinefeed.social_accounts a ON a.id=m.user_id WHERE m.conversation_id=? AND m.left_at IS NULL ORDER BY m.joined_at,a.id",
                (rs, n) -> new MemberDto(String.valueOf(rs.getInt(1)), rs.getString(2), rs.getString(3),
                        avatar(rs.getInt(1), rs.getString(4)), rs.getString(5).toLowerCase(Locale.ROOT)), conversationId);
        return new ConversationDetailsDto(row.get("id").toString(), ((String) row.get("kind")).toLowerCase(Locale.ROOT),
                (String) row.get("title"), (String) row.get("avatar"), instant((Timestamp) row.get("created_at")),
                (String) row.get("description"), members);
    }

    public MessagePageDto messages(int userId, UUID conversationId, String before) {
        requireActiveMember(userId, conversationId);
        Cursor cursor = parseCursor(before);
        String tail = cursor == null ? "" : " AND (m.created_at,m.id)<(?::timestamptz,?::uuid)";
        List<Object> args = new ArrayList<>(List.of(conversationId, userId));
        if (cursor != null) { args.add(cursor.at.toString()); args.add(cursor.id); }
        args.add(PAGE_SIZE + 1);
        String sql = "SELECT m.id,m.conversation_id,m.sender_id,m.kind,m.body,m.reply_to_id,m.created_at,m.updated_at,m.deleted_at," +
                "a.display_name,a.username,a.avatar_reference," +
                "(SELECT count(*) FROM cinechat.message_receipts r WHERE r.message_id=m.id AND r.read_at IS NOT NULL) read_count," +
                "(SELECT count(*) FROM cinechat.message_receipts r WHERE r.message_id=m.id AND r.delivered_at IS NOT NULL) delivered_count," +
                "(SELECT count(*) FROM cinechat.message_receipts r WHERE r.message_id=m.id) recipient_count," +
                "(SELECT CASE WHEN pm.deleted_at IS NULL THEN NULLIF(pm.body,'') END FROM cinechat.messages pm WHERE pm.id=m.reply_to_id) reply_preview," +
                "(SELECT pa.display_name FROM cinechat.messages pm JOIN cinefeed.social_accounts pa ON pa.id=pm.sender_id WHERE pm.id=m.reply_to_id) reply_author," +
                "COALESCE((SELECT pm.deleted_at IS NOT NULL FROM cinechat.messages pm WHERE pm.id=m.reply_to_id),false) reply_deleted," +
                "(SELECT ma.media_id FROM cinechat.message_attachments ma WHERE ma.message_id=m.id) attachment_id," +
                "(SELECT pm.media_type FROM cinechat.message_attachments ma JOIN cinefeed.post_media pm ON pm.id=ma.media_id WHERE ma.message_id=m.id) attachment_type " +
                "FROM cinechat.messages m JOIN cinefeed.social_accounts a ON a.id=m.sender_id " +
                "WHERE m.conversation_id=? AND EXISTS(SELECT 1 FROM cinechat.conversation_members cm WHERE cm.conversation_id=m.conversation_id AND cm.user_id=? AND cm.left_at IS NULL)" +
                tail + " ORDER BY m.created_at DESC,m.id DESC LIMIT ?";
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args.toArray());
        boolean more = rows.size() > PAGE_SIZE;
        if (more) rows = new ArrayList<>(rows.subList(0, PAGE_SIZE));
        List<MessageDto> items = new ArrayList<>();
        for (Map<String, Object> row : rows) items.add(message(row, userId));
        Collections.reverse(items);
        String next = more && !items.isEmpty() ? items.get(0).createdAt() + "," + items.get(0).id() : null;
        return new MessagePageDto(items, next, more);
    }

    public MessageDto send(int userId, UUID conversationId, SendRequest request) {
        requireActiveMember(userId, conversationId);
        String text = request.text() == null ? "" : request.text().trim();
        if ((text.isEmpty() && request.attachmentId() == null) || text.length() > 4000) throw badRequest("Add text or an image to send.");
        if (request.attachmentId() != null) requireOwnedChatUpload(userId, request.attachmentId());
        if (request.replyToId() != null && !Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM cinechat.messages WHERE id=? AND conversation_id=?)", Boolean.class,
                request.replyToId(), conversationId))) throw badRequest("The message you replied to is unavailable.");
        UUID id = tx.execute(status -> {
            UUID messageId = jdbc.queryForObject("INSERT INTO cinechat.messages(id,conversation_id,sender_id,client_id,body,reply_to_id) " +
                            "VALUES (?,?,?,?,?,?) ON CONFLICT(conversation_id,sender_id,client_id) DO UPDATE SET client_id=EXCLUDED.client_id RETURNING id",
                    UUID.class, UUID.randomUUID(), conversationId, userId, request.clientId(), text, request.replyToId());
            jdbc.update("INSERT INTO cinechat.message_receipts(message_id,user_id) SELECT ?,user_id FROM cinechat.conversation_members " +
                    "WHERE conversation_id=? AND user_id<>? AND left_at IS NULL ON CONFLICT DO NOTHING", messageId, conversationId, userId);
            if (request.attachmentId() != null) {
                jdbc.update("INSERT INTO cinechat.message_attachments(message_id,media_id,owner_id) VALUES (?,?,?) ON CONFLICT(message_id) DO NOTHING",
                        messageId, request.attachmentId(), userId);
                UUID attached = jdbc.query("SELECT media_id FROM cinechat.message_attachments WHERE message_id=?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, messageId);
                if (!request.attachmentId().equals(attached)) throw badRequest("This retry does not match the image already attached to the message.");
            }
            jdbc.update("UPDATE cinechat.conversations SET updated_at=now(),last_message_at=(SELECT created_at FROM cinechat.messages WHERE id=?) WHERE id=?",
                    messageId, conversationId);
            return messageId;
        });
        MessageDto result = messageById(userId, conversationId, id);
        publish(conversationId, "message.created", result);
        return result;
    }

    public MessageDto sharePost(int userId, UUID conversationId, SharePostRequest request) {
        requireActiveMember(userId, conversationId);
        List<Integer> recipients = jdbc.query("SELECT user_id FROM cinechat.conversation_members WHERE conversation_id=? AND left_at IS NULL",
                (rs, n) -> rs.getInt(1), conversationId);
        for (int recipient : recipients) {
            try { feed.getPost(recipient, request.postId()); }
            catch (ResponseStatusException denied) { throw notFound("This CineTake cannot be shared with everyone in this conversation."); }
        }
        UUID messageId = tx.execute(status -> {
            UUID id = jdbc.queryForObject("INSERT INTO cinechat.messages(id,conversation_id,sender_id,client_id,kind,body) VALUES (?,?,?,?,'CINE_TAKE',?) " +
                            "ON CONFLICT(conversation_id,sender_id,client_id) DO UPDATE SET client_id=EXCLUDED.client_id RETURNING id",
                    UUID.class, UUID.randomUUID(), conversationId, userId, request.clientId(), request.postId().toString());
            jdbc.update("INSERT INTO cinechat.message_receipts(message_id,user_id) SELECT ?,user_id FROM cinechat.conversation_members WHERE conversation_id=? AND user_id<>? AND left_at IS NULL ON CONFLICT DO NOTHING",
                    id, conversationId, userId);
            jdbc.update("UPDATE cinechat.conversations SET updated_at=now(),last_message_at=(SELECT created_at FROM cinechat.messages WHERE id=?) WHERE id=?", id, conversationId);
            return id;
        });
        MessageDto result = messageById(userId, conversationId, messageId);
        publish(conversationId, "message.created", result);
        return result;
    }

    public MessageDto edit(int userId, UUID conversationId, UUID messageId, EditRequest request) {
        requireActiveMember(userId, conversationId);
        String value = request.text().trim();
        if (value.isEmpty()) throw badRequest("Messages cannot be empty.");
        int changed = jdbc.update("UPDATE cinechat.messages SET body=?,updated_at=now() WHERE id=? AND conversation_id=? AND sender_id=? AND kind='TEXT' AND deleted_at IS NULL",
                value, messageId, conversationId, userId);
        if (changed == 0) throw notFound("Message not found.");
        MessageDto result = messageById(userId, conversationId, messageId);
        publish(conversationId, "message.updated", result);
        return result;
    }

    public void delete(int userId, UUID conversationId, UUID messageId) {
        requireActiveMember(userId, conversationId);
        int changed = jdbc.update("UPDATE cinechat.messages SET body='',deleted_at=now(),updated_at=now() WHERE id=? AND conversation_id=? AND sender_id=? AND deleted_at IS NULL",
                messageId, conversationId, userId);
        if (changed == 0) throw notFound("Message not found.");
        publish(conversationId, "message.deleted", Map.of("id", messageId.toString()));
    }

    public void delivered(int userId, UUID conversationId, UUID messageId) {
        requireActiveMember(userId, conversationId);
        jdbc.update("UPDATE cinechat.message_receipts r SET delivered_at=COALESCE(r.delivered_at,now()) FROM cinechat.messages m " +
                        "WHERE r.message_id=m.id AND m.conversation_id=? AND r.message_id=? AND r.user_id=?",
                conversationId, messageId, userId);
        publish(conversationId, "receipt.delivered", Map.of("messageId", messageId.toString(), "userId", userId));
    }

    public void markRead(int userId, UUID conversationId) {
        requireActiveMember(userId, conversationId);
        tx.execute(status -> {
            jdbc.update("UPDATE cinechat.message_receipts r SET read_at=COALESCE(r.read_at,now()),delivered_at=COALESCE(r.delivered_at,now()) " +
                    "FROM cinechat.messages m WHERE r.message_id=m.id AND m.conversation_id=? AND r.user_id=? AND m.sender_id<>?", conversationId, userId, userId);
            jdbc.update("UPDATE cinechat.conversation_members SET last_read_at=now() WHERE conversation_id=? AND user_id=?", conversationId, userId);
            return null;
        });
        publish(conversationId, "receipt.read", Map.of("userId", userId));
    }

    public boolean react(int userId, UUID conversationId, UUID messageId, String emoji) {
        requireActiveMember(userId, conversationId);
        if (emoji == null || emoji.isBlank() || emoji.length() > 16) throw badRequest("Choose one emoji reaction.");
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinechat.messages WHERE id=? AND conversation_id=? AND deleted_at IS NULL)",
                Boolean.class, messageId, conversationId))) throw notFound("Message not found.");
        Integer count = jdbc.queryForObject("SELECT count(*) FROM cinechat.message_reactions WHERE message_id=? AND user_id=? AND emoji=?", Integer.class, messageId, userId, emoji);
        boolean active = count == null || count == 0;
        if (active) jdbc.update("INSERT INTO cinechat.message_reactions(message_id,user_id,emoji) VALUES (?,?,?) ON CONFLICT DO NOTHING", messageId, userId, emoji);
        else jdbc.update("DELETE FROM cinechat.message_reactions WHERE message_id=? AND user_id=? AND emoji=?", messageId, userId, emoji);
        publish(conversationId, "reaction.changed", Map.of("messageId", messageId.toString(), "userId", userId, "emoji", emoji, "active", active));
        return active;
    }

    public PollDto createPoll(int userId, UUID conversationId, PollRequest request) {
        requireActiveMember(userId, conversationId);
        if (request.options() == null || request.options().size() < 2) throw badRequest("A poll needs at least two options.");
        List<String> options = request.options().stream().map(String::trim).toList();
        if (options.stream().anyMatch(String::isEmpty) || options.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count() != options.size())
            throw badRequest("Poll options must be nonempty and unique.");
        if (request.closesAt() != null && request.closesAt().isBefore(Instant.now())) throw badRequest("Poll closing time must be in the future.");
        UUID messageId = tx.execute(status -> {
            UUID id = jdbc.queryForObject("INSERT INTO cinechat.messages(id,conversation_id,sender_id,client_id,kind,body) VALUES (?, ?, ?, ?, 'POLL', ?) " +
                            "ON CONFLICT(conversation_id,sender_id,client_id) DO UPDATE SET client_id=EXCLUDED.client_id RETURNING id",
                    UUID.class, UUID.randomUUID(), conversationId, userId, request.clientId(), request.question().trim());
            jdbc.update("INSERT INTO cinechat.chat_polls(message_id,question,multiple_choice,closes_at) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                    id, request.question().trim(), request.multipleChoice(), request.closesAt());
            for (int i = 0; i < options.size(); i++) jdbc.update("INSERT INTO cinechat.chat_poll_options(id,message_id,label,position) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",
                    UUID.randomUUID(), id, options.get(i), i);
            jdbc.update("INSERT INTO cinechat.message_receipts(message_id,user_id) SELECT ?,user_id FROM cinechat.conversation_members WHERE conversation_id=? AND user_id<>? AND left_at IS NULL ON CONFLICT DO NOTHING", id, conversationId, userId);
            jdbc.update("UPDATE cinechat.conversations SET updated_at=now(),last_message_at=now() WHERE id=?", conversationId);
            return id;
        });
        PollDto poll = poll(userId, messageId);
        publish(conversationId, "poll.created", poll);
        return poll;
    }

    public PollDto vote(int userId, UUID conversationId, UUID messageId, VoteRequest request) {
        requireActiveMember(userId, conversationId);
        List<UUID> options = request.optionIds() == null ? List.of() : request.optionIds().stream().distinct().toList();
        if (options.isEmpty()) throw badRequest("Select at least one poll option.");
        Boolean belongs = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinechat.messages WHERE id=? AND conversation_id=? AND kind='POLL')",
                Boolean.class, messageId, conversationId);
        if (!Boolean.TRUE.equals(belongs)) throw notFound("Poll not found.");
        Map<String, Object> poll = jdbc.queryForMap("SELECT multiple_choice,closes_at FROM cinechat.chat_polls WHERE message_id=?", messageId);
        if (poll.get("closes_at") != null && ((Timestamp) poll.get("closes_at")).toInstant().isBefore(Instant.now())) throw badRequest("This poll is closed.");
        if (!Boolean.TRUE.equals(poll.get("multiple_choice")) && options.size() > 1) throw badRequest("Choose one option for this poll.");
        int valid = 0;
        for (UUID option : options) {
            if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinechat.chat_poll_options WHERE message_id=? AND id=?)",
                    Boolean.class, messageId, option))) valid++;
        }
        if (valid != options.size()) throw badRequest("That poll choice is unavailable.");
        tx.execute(status -> {
            jdbc.update("DELETE FROM cinechat.chat_poll_votes WHERE user_id=? AND option_id IN (SELECT id FROM cinechat.chat_poll_options WHERE message_id=?)", userId, messageId);
            for (UUID option : options) jdbc.update("INSERT INTO cinechat.chat_poll_votes(option_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING", option, userId);
            return null;
        });
        PollDto result = poll(userId, messageId);
        publish(conversationId, "poll.voted", result);
        return result;
    }

    public void addMember(int userId, UUID conversationId, int memberId) {
        requireAdmin(userId, conversationId);
        if (userId == memberId) throw badRequest("You are already in this group.");
        feed.user(userId, memberId);
        ensureNotBlocked(userId, memberId);
        jdbc.update("INSERT INTO cinechat.conversation_members(conversation_id,user_id,role) SELECT ?,?,'MEMBER' FROM cinechat.conversations WHERE id=? AND kind='GROUP' " +
                "ON CONFLICT(conversation_id,user_id) DO UPDATE SET left_at=NULL,joined_at=now()", conversationId, memberId, conversationId);
        publish(conversationId, "conversation.member_added", Map.of("userId", memberId));
    }

    public void removeMember(int userId, UUID conversationId, int memberId) {
        requireAdmin(userId, conversationId);
        if (memberId == userId) throw badRequest("Use leave to exit the group.");
        int changed = jdbc.update("UPDATE cinechat.conversation_members SET left_at=now() WHERE conversation_id=? AND user_id=? AND left_at IS NULL AND EXISTS(SELECT 1 FROM cinechat.conversations WHERE id=? AND kind='GROUP')",
                conversationId, memberId, conversationId);
        if (changed == 0) throw notFound("Group member not found.");
        publish(conversationId, "conversation.member_removed", Map.of("userId", memberId));
    }

    public void leave(int userId, UUID conversationId) {
        tx.executeWithoutResult(status -> {
            Map<String, Object> membership = jdbc.query("SELECT m.role,(SELECT count(*) FROM cinechat.conversation_members x WHERE x.conversation_id=m.conversation_id AND x.role='ADMIN' AND x.left_at IS NULL) admins FROM cinechat.conversation_members m JOIN cinechat.conversations c ON c.id=m.conversation_id WHERE m.conversation_id=? AND m.user_id=? AND m.left_at IS NULL AND c.kind='GROUP' FOR UPDATE OF c",
                    rs -> rs.next() ? Map.of("role", rs.getString("role"), "admins", rs.getInt("admins")) : null, conversationId, userId);
            if (membership == null) throw notFound("Group conversation not found.");
            if ("ADMIN".equals(membership.get("role")) && ((Number) membership.get("admins")).intValue() <= 1) {
                throw badRequest("Promote another member to admin before leaving this group.");
            }
            jdbc.update("UPDATE cinechat.conversation_members SET left_at=now() WHERE conversation_id=? AND user_id=? AND left_at IS NULL", conversationId, userId);
        });
        publish(conversationId, "conversation.member_left", Map.of("userId", userId));
    }

    /** Closes access for every member while retaining the group and its message history. */
    public void deleteGroup(int userId, UUID conversationId) {
        requireAdmin(userId, conversationId);
        List<Integer> members = tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM cinechat.conversations WHERE id=? AND kind='GROUP' FOR UPDATE", UUID.class, conversationId);
            String role = jdbc.query("SELECT role FROM cinechat.conversation_members WHERE conversation_id=? AND user_id=? AND left_at IS NULL",
                    rs -> rs.next() ? rs.getString(1) : null, conversationId, userId);
            if (!"ADMIN".equals(role)) throw notFound("Group not found.");
            List<Integer> activeMembers = jdbc.query("SELECT user_id FROM cinechat.conversation_members WHERE conversation_id=? AND left_at IS NULL",
                    (rs, n) -> rs.getInt(1), conversationId);
            jdbc.update("UPDATE cinechat.conversation_members SET left_at=now() WHERE conversation_id=? AND left_at IS NULL", conversationId);
            return activeMembers;
        });
        if (members != null) {
            ChatEvent event = new ChatEvent(UUID.randomUUID().toString(), "conversation.deleted", conversationId.toString(), Instant.now(), Map.of());
            members.forEach(memberId -> events.send(memberId, event));
        }
    }

    public void renameGroup(int userId, UUID conversationId, String name, String description) {
        requireAdmin(userId, conversationId);
        String title = name == null ? "" : name.trim();
        if (title.isEmpty() || title.length() > 100) throw badRequest("Group name must contain 1 to 100 characters.");
        String summary = description == null ? null : description.trim();
        if (summary != null && summary.length() > 500) throw badRequest("Group description must be 500 characters or fewer.");
        if (description == null) jdbc.update("UPDATE cinechat.conversations SET title=?,updated_at=now() WHERE id=? AND kind='GROUP'", title, conversationId);
        else jdbc.update("UPDATE cinechat.conversations SET title=?,description=?,updated_at=now() WHERE id=? AND kind='GROUP'", title, summary, conversationId);
        publish(conversationId, "conversation.updated", Map.of("title", title, "description", summary == null ? "" : summary));
    }

    public void setMemberRole(int userId, UUID conversationId, int memberId, String role) {
        requireAdmin(userId, conversationId);
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("ADMIN") && !normalized.equals("MEMBER")) throw badRequest("Choose a valid group role.");
        if (memberId == userId && normalized.equals("MEMBER")) throw badRequest("Transfer admin access before changing your own role.");
        tx.executeWithoutResult(status -> {
            jdbc.queryForObject("SELECT id FROM cinechat.conversations WHERE id=? AND kind='GROUP' FOR UPDATE", UUID.class, conversationId);
            Boolean target = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinechat.conversation_members WHERE conversation_id=? AND user_id=? AND left_at IS NULL)", Boolean.class, conversationId, memberId);
            if (!Boolean.TRUE.equals(target)) throw notFound("Group member not found.");
            if (normalized.equals("MEMBER")) {
                Integer admins = jdbc.queryForObject("SELECT count(*) FROM cinechat.conversation_members WHERE conversation_id=? AND role='ADMIN' AND left_at IS NULL AND user_id<>?", Integer.class, conversationId, memberId);
                if (admins == null || admins == 0) throw badRequest("A group must keep at least one admin.");
            }
            jdbc.update("UPDATE cinechat.conversation_members SET role=? WHERE conversation_id=? AND user_id=? AND left_at IS NULL", normalized, conversationId, memberId);
        });
        publish(conversationId, "conversation.member_role_changed", Map.of("userId", memberId, "role", normalized.toLowerCase(Locale.ROOT)));
    }

    public ConversationDetailsDto updateGroupAvatar(int userId, UUID conversationId, UUID mediaId) {
        requireAdmin(userId, conversationId);
        requireOwnedChatUpload(userId, mediaId);
        jdbc.update("INSERT INTO cinechat.conversation_avatars(conversation_id,media_id,owner_id) VALUES (?,?,?) " +
                        "ON CONFLICT(conversation_id) DO UPDATE SET media_id=EXCLUDED.media_id,owner_id=EXCLUDED.owner_id,updated_at=now()",
                conversationId, mediaId, userId);
        publish(conversationId, "conversation.updated", Map.of("avatar", "/api/feed/media/" + mediaId));
        return details(userId, conversationId);
    }

    public ConversationDetailsDto removeGroupAvatar(int userId, UUID conversationId) {
        requireAdmin(userId, conversationId);
        jdbc.update("DELETE FROM cinechat.conversation_avatars WHERE conversation_id=?", conversationId);
        publish(conversationId, "conversation.updated", Map.of("avatarRemoved", true));
        return details(userId, conversationId);
    }

    public void typing(int userId, UUID conversationId) {
        requireActiveMember(userId, conversationId);
        publish(conversationId, "typing", Map.of("userId", userId, "expiresInMs", 2500));
    }

    private PollDto poll(int userId, UUID messageId) {
        Map<String, Object> row = jdbc.queryForMap("SELECT question,multiple_choice,closes_at FROM cinechat.chat_polls WHERE message_id=?", messageId);
        Timestamp closing = (Timestamp) row.get("closes_at");
        List<PollOptionDto> options = jdbc.query("SELECT o.id,o.label,count(v.user_id) votes,bool_or(v.user_id=?) selected FROM cinechat.chat_poll_options o " +
                        "LEFT JOIN cinechat.chat_poll_votes v ON v.option_id=o.id WHERE o.message_id=? GROUP BY o.id,o.label,o.position ORDER BY o.position",
                (rs, n) -> new PollOptionDto(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getBoolean(4)), userId, messageId);
        Instant closes = instant(closing);
        return new PollDto(messageId.toString(), (String) row.get("question"), (Boolean) row.get("multiple_choice"), closes,
                closes != null && closes.isBefore(Instant.now()), options);
    }

    private MessageDto messageById(int userId, UUID conversationId, UUID id) {
        Map<String, Object> row = jdbc.queryForMap("SELECT m.id,m.conversation_id,m.sender_id,m.kind,m.body,m.reply_to_id,m.created_at,m.updated_at,m.deleted_at," +
                "a.display_name,a.username,a.avatar_reference,(SELECT count(*) FROM cinechat.message_receipts r WHERE r.message_id=m.id AND r.read_at IS NOT NULL) read_count," +
                "(SELECT count(*) FROM cinechat.message_receipts r WHERE r.message_id=m.id AND r.delivered_at IS NOT NULL) delivered_count," +
                "(SELECT count(*) FROM cinechat.message_receipts r WHERE r.message_id=m.id) recipient_count," +
                "(SELECT CASE WHEN p.deleted_at IS NULL THEN NULLIF(p.body,'') END FROM cinechat.messages p WHERE p.id=m.reply_to_id) reply_preview," +
                "(SELECT a.display_name FROM cinechat.messages p JOIN cinefeed.social_accounts a ON a.id=p.sender_id WHERE p.id=m.reply_to_id) reply_author," +
                "COALESCE((SELECT p.deleted_at IS NOT NULL FROM cinechat.messages p WHERE p.id=m.reply_to_id),false) reply_deleted," +
                "(SELECT ma.media_id FROM cinechat.message_attachments ma WHERE ma.message_id=m.id) attachment_id," +
                "(SELECT pm.media_type FROM cinechat.message_attachments ma JOIN cinefeed.post_media pm ON pm.id=ma.media_id WHERE ma.message_id=m.id) attachment_type " +
                "FROM cinechat.messages m JOIN cinefeed.social_accounts a ON a.id=m.sender_id WHERE m.id=? AND m.conversation_id=?", id, conversationId);
        return message(row, userId);
    }

    private MessageDto message(Map<String, Object> row, int viewerId) {
        UUID messageId = (UUID) row.get("id");
        int senderId = ((Number) row.get("sender_id")).intValue();
        Map<String, Integer> reactions = new LinkedHashMap<>();
        jdbc.query("SELECT emoji,count(*) FROM cinechat.message_reactions WHERE message_id=? GROUP BY emoji", rs -> {
            while (rs.next()) reactions.put(rs.getString(1), rs.getInt(2)); return null;
        }, messageId);
        boolean deleted = row.get("deleted_at") != null;
        return new MessageDto(messageId.toString(), row.get("conversation_id").toString(), String.valueOf(senderId),
                (String) row.get("display_name"), (String) row.get("username"), avatar(senderId, (String) row.get("avatar_reference")),
                "CINE_TAKE".equals(row.get("kind")) ? "cinetake" : ((String) row.get("kind")).toLowerCase(Locale.ROOT), deleted ? "Message deleted" : (String) row.get("body"),
                row.get("reply_to_id") == null ? null : row.get("reply_to_id").toString(),
                row.get("reply_preview") == null ? null : (String) row.get("reply_preview"),
                row.get("reply_author") == null ? null : (String) row.get("reply_author"), Boolean.TRUE.equals(row.get("reply_deleted")), instant((Timestamp) row.get("created_at")),
                !Objects.equals(row.get("created_at"), row.get("updated_at")), deleted,
                ((Number) row.get("delivered_count")).intValue(), ((Number) row.get("read_count")).intValue(), ((Number) row.get("recipient_count")).intValue(), reactions,
                "POLL".equals(row.get("kind")) && !deleted ? poll(viewerId, messageId) : null,
                !deleted && row.get("attachment_id") != null ? new AttachmentDto(row.get("attachment_id").toString(),
                        "/api/feed/media/" + row.get("attachment_id"), (String) row.get("attachment_type")) : null);
    }

    private void requireOwnedChatUpload(int userId, UUID mediaId) {
        Boolean valid = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinefeed.post_media WHERE id=? AND owner_id=? " +
                "AND post_id IS NULL AND media_type LIKE 'image/%')", Boolean.class, mediaId, userId);
        if (!Boolean.TRUE.equals(valid)) throw notFound("That image upload is unavailable.");
    }

    private void publish(UUID conversationId, String type, Object data) {
        List<Integer> members = jdbc.query("SELECT user_id FROM cinechat.conversation_members WHERE conversation_id=? AND left_at IS NULL",
                (rs, n) -> rs.getInt(1), conversationId);
        ChatEvent event = new ChatEvent(UUID.randomUUID().toString(), type, conversationId.toString(), Instant.now(), data);
        members.forEach(id -> events.send(id, event));
    }

    private void syncUsers(int userId) { feed.user(userId, userId); }

    private void ensureNotBlocked(int first, int second) {
        Boolean blocked = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinefeed.user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?))",
                Boolean.class, first, second, second, first);
        if (Boolean.TRUE.equals(blocked)) throw notFound("This account is unavailable.");
    }

    private void requireActiveMember(int userId, UUID conversationId) {
        Boolean member = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinechat.conversation_members WHERE conversation_id=? AND user_id=? AND left_at IS NULL)",
                Boolean.class, conversationId, userId);
        if (!Boolean.TRUE.equals(member)) throw notFound("Conversation not found.");
        Boolean blocked = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinechat.conversations c JOIN cinechat.conversation_members other " +
                        "ON other.conversation_id=c.id AND other.user_id<>? AND other.left_at IS NULL JOIN cinefeed.user_blocks b " +
                        "ON (b.blocker_id=? AND b.blocked_id=other.user_id) OR (b.blocker_id=other.user_id AND b.blocked_id=?) " +
                        "WHERE c.id=? AND c.kind='DIRECT')", Boolean.class, userId, userId, userId, conversationId);
        if (Boolean.TRUE.equals(blocked)) throw notFound("Conversation not found.");
    }

    private void requireAdmin(int userId, UUID conversationId) {
        String role = jdbc.query("SELECT m.role FROM cinechat.conversation_members m JOIN cinechat.conversations c ON c.id=m.conversation_id " +
                "WHERE m.conversation_id=? AND m.user_id=? AND m.left_at IS NULL AND c.kind='GROUP'", rs -> rs.next() ? rs.getString(1) : null,
                conversationId, userId);
        if (!"ADMIN".equals(role)) throw notFound("Group not found.");
    }

    private PersonDto person(int id, String name, String handle, String avatarReference) {
        return new PersonDto(String.valueOf(id), name, handle, avatar(id, avatarReference));
    }
    private static String avatar(int id, String reference) { return reference == null ? null : "/api/feed/users/" + id + "/avatar"; }
    private static Instant instant(Timestamp timestamp) { return timestamp == null ? null : timestamp.toInstant(); }
    private static ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static String escapeLike(String value) { return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"); }

    private static Cursor parseCursor(String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split(",", 2);
        if (parts.length != 2) throw badRequest("Invalid message cursor.");
        try { return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1])); }
        catch (RuntimeException ex) { throw badRequest("Invalid message cursor."); }
    }
    private record Cursor(Instant at, UUID id) { }
}
