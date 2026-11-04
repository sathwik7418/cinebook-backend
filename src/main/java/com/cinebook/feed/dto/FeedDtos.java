package com.cinebook.feed.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class FeedDtos {
    private FeedDtos() { }

    public record FeedPage(List<PostDto> items, String nextCursor, boolean hasMore) { }
    public record PostDto(String id, String authorId, String author, String handle, String initials,
                         String avatarTone, String time, Instant createdAt, Instant updatedAt, String movie, String movieId,
                         String text, String format, String visibility, boolean spoiler, List<String> topics,
                         String media, int likes, int comments, int reposts, boolean likedByMe,
                         boolean savedByMe, boolean repostedByMe, PollDto poll, String quoteOf,
                         String originalAuthor, String originalText, boolean originalUnavailable,
                         String authorAvatar) { }
    public record PollDto(String id, String question, List<String> options, Map<String, Integer> counts,
                          int totalVotes, String votedOption, Instant closesAt, boolean closed) { }
    public record UserDto(String id, String name, String handle, String initials, String tone,
                          String bio, boolean reviewer, String specialization, int followers,
                          int following, boolean followedByMe, boolean followsMe, boolean mutual,
                          String avatar, Instant joinedAt) {
        public UserDto(String id, String name, String handle, String initials, String tone,
                       String bio, boolean reviewer, String specialization, int followers,
                       int following, boolean followedByMe, boolean followsMe, boolean mutual) {
            this(id,name,handle,initials,tone,bio,reviewer,specialization,followers,following,followedByMe,followsMe,mutual,null,null);
        }
    }
    public record ProfileUpdateRequest(@Size(min = 1, max = 100) String name,
                                       @Size(min = 3, max = 30) String handle,
                                       @Size(max = 280) String bio,
                                       String avatarId, boolean removeAvatar) { }
    public record CommentDto(String id, String postId, String parentId, String authorId, String author,
                             String handle, String initials, String tone, String text,
                             Instant createdAt, boolean edited, String avatar) { }
    public record CommentPage(List<CommentDto> items, String nextCursor, boolean hasMore) { }
    public record UserPage(List<UserDto> items, String nextCursor, boolean hasMore) { }
    public record TopicDto(String name, String displayName, long postCount) { }
    public record SearchResults(List<PostDto> posts, List<UserDto> users, List<MovieDto> movies,
                                List<TopicDto> topics, String nextCursor) { }
    public record MovieDto(String id, String title, Integer year, List<String> genres,
                           String language, String poster) { }
    public record UploadDto(String id, String mediaType, String url) { }

    public record CreatePostRequest(
            @Size(max = 500) String content,
            String visibility,
            boolean spoiler,
            String postType,
            Integer movieId,
            String quoteOf,
            @Size(max = 4) List<String> mediaIds,
            @Valid PollRequest poll) { }

    public record PollRequest(@NotBlank @Size(min = 4, max = 120) String question,
                              @Size(min = 2, max = 4) List<@NotBlank @Size(max = 60) String> options,
                              Instant closesAt) { }
    public record UpdatePostRequest(@Size(max = 500) String content, String visibility,
                                    Boolean spoiler, Integer movieId) { }
    public record CreateCommentRequest(@NotBlank @Size(max = 1000) String content, String parentId) { }
    public record UpdateCommentRequest(@NotBlank @Size(max = 1000) String content) { }
    public record VoteRequest(@NotBlank String option) { }
    public record ReportRequest(@Size(max = 300) String reason) { }
    public record PreferencesRequest(List<String> genres, List<String> languages,
                                    List<String> postTypes, Boolean hideSpoilers) { }
    public record PreferencesDto(List<String> genres, List<String> languages,
                                 List<String> postTypes, boolean hideSpoilers) { }
}
