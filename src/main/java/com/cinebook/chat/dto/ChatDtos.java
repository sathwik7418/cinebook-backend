package com.cinebook.chat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ChatDtos {
    private ChatDtos() { }

    public record PersonDto(String id, String name, String handle, String avatar) { }
    public record ConversationDto(String id, String kind, String title, String avatar, PersonDto counterpart,
                                  String lastMessage, Instant lastMessageAt, long unreadCount) { }
    public record MemberDto(String id, String name, String handle, String avatar, String role) { }
    public record ConversationDetailsDto(String id, String kind, String title, String avatar, Instant createdAt,
                                         String description, List<MemberDto> members) { }
    public record MessageDto(String id, String conversationId, String senderId, String senderName,
                             String senderHandle, String senderAvatar, String kind, String body,
                             String replyToId, String replyPreview, String replyAuthor, boolean replyDeleted, Instant createdAt,
                             boolean edited, boolean deleted, int deliveredCount, int readCount, int recipientCount,
                             Map<String, Integer> reactions, PollDto poll, AttachmentDto attachment) { }
    public record AttachmentDto(String id, String url, String mediaType) { }
    public record MessagePageDto(List<MessageDto> items, String nextCursor, boolean hasMore) { }
    public record ChatEvent(String eventId, String type, String conversationId, Instant at, Object data) { }

    public record DirectRequest(@NotNull Integer userId) { }
    public record SharePostRequest(@NotNull java.util.UUID postId, @NotNull java.util.UUID clientId) { }
    public record GroupRequest(@NotBlank @Size(max = 100) String name,
                               @Size(max = 500) String description,
                               @Size(min = 1, max = 49) List<@NotNull Integer> userIds) { }
    public record SendRequest(@NotNull java.util.UUID clientId,
                              @Size(max = 4000) String text,
                              java.util.UUID replyToId,
                              java.util.UUID attachmentId) { }
    public record EditRequest(@NotBlank @Size(max = 4000) String text) { }
    public record ReactionRequest(@NotBlank @Size(max = 16) String emoji) { }
    public record PollRequest(@NotBlank @Size(max = 240) String question,
                              @Size(min = 2, max = 12) List<@NotBlank @Size(max = 120) String> options,
                              boolean multipleChoice, Instant closesAt,
                              @NotNull java.util.UUID clientId) { }
    /** Choices submitted for a poll vote. */
    public record VoteRequest(@Size(min = 1, max = 12) List<@NotNull UUID> optionIds) { }
    public record RenameRequest(@NotBlank @Size(max = 100) String name) { }
    public record GroupUpdateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) { }
    public record RoleRequest(@NotBlank String role) { }
    public record MediaRequest(@NotNull java.util.UUID mediaId) { }
    public record PollOptionDto(String id, String label, long votes, boolean selected) { }
    public record PollDto(String messageId, String question, boolean multipleChoice,
                          Instant closesAt, boolean closed, List<PollOptionDto> options) { }
}
