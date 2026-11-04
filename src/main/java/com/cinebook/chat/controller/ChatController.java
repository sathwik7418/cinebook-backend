package com.cinebook.chat.controller;

import com.cinebook.chat.dto.ChatDtos.*;
import com.cinebook.chat.service.ChatEventHub;
import com.cinebook.chat.service.ChatService;
import com.cinebook.user.model.JsonUserPrincipal;
import jakarta.validation.Valid;
import com.cinebook.chat.config.CineChatPersistenceEnabledCondition;
import org.springframework.context.annotation.Conditional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@Conditional(CineChatPersistenceEnabledCondition.class)
public class ChatController {
    private final ChatService chat;
    private final ChatEventHub events;

    public ChatController(ChatService chat, ChatEventHub events) {
        this.chat = chat;
        this.events = events;
    }

    @GetMapping("/conversations")
    public List<ConversationDto> conversations(@AuthenticationPrincipal JsonUserPrincipal user) {
        return chat.conversations(user.getId());
    }

    @GetMapping("/users")
    public List<PersonDto> users(@AuthenticationPrincipal JsonUserPrincipal user, @RequestParam String q) {
        return chat.searchUsers(user.getId(), q);
    }

    @PostMapping("/direct")
    public ConversationDetailsDto direct(@AuthenticationPrincipal JsonUserPrincipal user, @Valid @RequestBody DirectRequest request) {
        return chat.direct(user.getId(), request.userId());
    }

    @PostMapping("/groups")
    public ResponseEntity<ConversationDetailsDto> createGroup(@AuthenticationPrincipal JsonUserPrincipal user,
                                                               @Valid @RequestBody GroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.createGroup(user.getId(), request));
    }

    @GetMapping("/conversations/{id}")
    public ConversationDetailsDto details(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id) {
        return chat.details(user.getId(), id);
    }

    @GetMapping("/conversations/{id}/messages")
    public MessagePageDto messages(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                   @RequestParam(required = false) String before) {
        return chat.messages(user.getId(), id, before);
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<MessageDto> send(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                           @Valid @RequestBody SendRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.send(user.getId(), id, request));
    }

    @PostMapping("/conversations/{id}/shares/cinetake")
    public ResponseEntity<MessageDto> sharePost(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                                 @Valid @RequestBody SharePostRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.sharePost(user.getId(), id, request));
    }

    @PostMapping("/conversations/{id}/typing")
    public ResponseEntity<Void> typing(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id) {
        chat.typing(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/conversations/{id}")
    public ResponseEntity<Void> rename(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                       @Valid @RequestBody GroupUpdateRequest request) {
        chat.renameGroup(user.getId(), id, request.name(), request.description());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/conversations/{id}/members/{memberId}/role")
    public ResponseEntity<Void> memberRole(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                           @PathVariable int memberId, @Valid @RequestBody RoleRequest request) {
        chat.setMemberRole(user.getId(), id, memberId, request.role());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/conversations/{id}/avatar")
    public ConversationDetailsDto groupAvatar(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                               @Valid @RequestBody MediaRequest request) {
        return chat.updateGroupAvatar(user.getId(), id, request.mediaId());
    }

    @DeleteMapping("/conversations/{id}/avatar")
    public ConversationDetailsDto removeGroupAvatar(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id) {
        return chat.removeGroupAvatar(user.getId(), id);
    }

    @PatchMapping("/conversations/{id}/messages/{messageId}")
    public MessageDto edit(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                           @PathVariable UUID messageId, @Valid @RequestBody EditRequest request) {
        return chat.edit(user.getId(), id, messageId, request);
    }

    @DeleteMapping("/conversations/{id}/messages/{messageId}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                       @PathVariable UUID messageId) {
        chat.delete(user.getId(), id, messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/delivered")
    public ResponseEntity<Void> delivered(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                          @PathVariable UUID messageId) {
        chat.delivered(user.getId(), id, messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> read(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id) {
        chat.markRead(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/reactions")
    public Map<String, Boolean> react(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                      @PathVariable UUID messageId, @Valid @RequestBody ReactionRequest request) {
        return Map.of("active", chat.react(user.getId(), id, messageId, request.emoji()));
    }

    @PostMapping("/conversations/{id}/polls")
    public ResponseEntity<PollDto> createPoll(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                              @Valid @RequestBody PollRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chat.createPoll(user.getId(), id, request));
    }

    @PostMapping("/conversations/{id}/polls/{messageId}/votes")
    public PollDto vote(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                        @PathVariable UUID messageId, @Valid @RequestBody VoteRequest request) {
        return chat.vote(user.getId(), id, messageId, request);
    }

    @PostMapping("/conversations/{id}/members/{memberId}")
    public ResponseEntity<Void> addMember(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                          @PathVariable int memberId) {
        chat.addMember(user.getId(), id, memberId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversations/{id}/members/{memberId}")
    public ResponseEntity<Void> removeMember(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id,
                                             @PathVariable int memberId) {
        chat.removeMember(user.getId(), id, memberId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/conversations/{id}/leave")
    public ResponseEntity<Void> leave(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id) {
        chat.leave(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<Void> deleteGroup(@AuthenticationPrincipal JsonUserPrincipal user, @PathVariable UUID id) {
        chat.deleteGroup(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/events", produces = "text/event-stream")
    public SseEmitter events(@AuthenticationPrincipal JsonUserPrincipal user) {
        return events.subscribe(user.getId());
    }
}
