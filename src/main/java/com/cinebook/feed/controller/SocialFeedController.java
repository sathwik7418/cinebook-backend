package com.cinebook.feed.controller;

import com.cinebook.feed.dto.FeedDtos.*;
import com.cinebook.feed.service.SocialFeedService;
import com.cinebook.feed.service.SocialFeedService.MediaFile;
import com.cinebook.user.model.JsonUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/feed")
@ConditionalOnProperty(prefix = "cinebook.social.database", name = "enabled", havingValue = "true")
public class SocialFeedController {
    private final SocialFeedService feed;
    public SocialFeedController(SocialFeedService feed) { this.feed = feed; }

    @GetMapping("/me") public UserDto me(@AuthenticationPrincipal JsonUserPrincipal user) { return feed.me(user); }
    @PatchMapping("/me/profile") public UserDto updateProfile(@AuthenticationPrincipal JsonUserPrincipal user,@Valid @RequestBody ProfileUpdateRequest request){return feed.updateProfile(user.getId(),request);}
    @GetMapping public FeedPage feed(@AuthenticationPrincipal JsonUserPrincipal user,
            @RequestParam(defaultValue="for-you") String type, @RequestParam(required=false) String cursor,
            @RequestParam(defaultValue="20") int size) { return feed.feed(user.getId(),type,cursor,size); }
    @PostMapping("/posts") public ResponseEntity<PostDto> create(@AuthenticationPrincipal JsonUserPrincipal user,
            @Valid @RequestBody CreatePostRequest request) { PostDto post=feed.createPost(user.getId(),request);return ResponseEntity.created(URI.create("/api/feed/posts/"+post.id())).body(post); }
    @GetMapping("/posts/{id}") public PostDto getPost(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){return feed.getPost(user.getId(),id);}
    @PatchMapping("/posts/{id}") public PostDto update(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id,@Valid @RequestBody UpdatePostRequest request){return feed.updatePost(user.getId(),id,request);}
    @DeleteMapping("/posts/{id}") public ResponseEntity<Void> delete(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){feed.deletePost(user.getId(),id);return ResponseEntity.noContent().build();}

    @PostMapping("/posts/{id}/reactions") public PostDto like(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){return feed.react(user.getId(),id,true);}
    @DeleteMapping("/posts/{id}/reactions") public PostDto unlike(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){return feed.react(user.getId(),id,false);}
    @PostMapping("/posts/{id}/repost") public PostDto repost(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){return feed.repost(user.getId(),id,true);}
    @DeleteMapping("/posts/{id}/repost") public PostDto undoRepost(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){return feed.repost(user.getId(),id,false);}
    @PostMapping("/posts/{id}/save") public ResponseEntity<Void> save(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){feed.savePost(user.getId(),id,true);return ResponseEntity.noContent().build();}
    @DeleteMapping("/posts/{id}/save") public ResponseEntity<Void> unsave(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){feed.savePost(user.getId(),id,false);return ResponseEntity.noContent().build();}
    @GetMapping("/saved") public FeedPage saved(@AuthenticationPrincipal JsonUserPrincipal user,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size){return feed.saved(user.getId(),cursor,size);}

    @GetMapping("/posts/{id}/comments") public CommentPage comments(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="30") int size){return feed.comments(user.getId(),id,cursor,size);}
    @PostMapping("/posts/{id}/comments") public ResponseEntity<CommentDto> comment(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id,@Valid @RequestBody CreateCommentRequest request){CommentDto c=feed.addComment(user.getId(),id,request);return ResponseEntity.created(URI.create("/api/feed/comments/"+c.id())).body(c);}
    @PatchMapping("/comments/{id}") public CommentDto editComment(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id,@Valid @RequestBody UpdateCommentRequest request){return feed.editComment(user.getId(),id,request);}
    @DeleteMapping("/comments/{id}") public ResponseEntity<Void> deleteComment(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){feed.deleteComment(user.getId(),id);return ResponseEntity.noContent().build();}

    @PostMapping("/users/{id}/follow") public ResponseEntity<Void> follow(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id){feed.follow(user.getId(),id,true);return ResponseEntity.noContent().build();}
    @DeleteMapping("/users/{id}/follow") public ResponseEntity<Void> unfollow(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id){feed.follow(user.getId(),id,false);return ResponseEntity.noContent().build();}
    @GetMapping("/users/{id}") public UserDto user(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id){return feed.user(user.getId(),id);}
    @GetMapping("/users/{id}/posts") public FeedPage userPosts(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id,@RequestParam(defaultValue="latest") String type,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size){return feed.userPosts(user.getId(),id,type,cursor,size);}
    @GetMapping("/users/{id}/content") public FeedPage userContent(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id,@RequestParam(defaultValue="posts") String type,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size){return feed.userContent(user.getId(),id,type,cursor,size);}
    @GetMapping("/users/{id}/followers") public UserPage followers(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="30") int size){return feed.people(user.getId(),id,"followers",cursor,size);}
    @GetMapping("/users/{id}/following") public UserPage following(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="30") int size){return feed.people(user.getId(),id,"following",cursor,size);}
    @GetMapping("/users/suggestions") public UserPage suggestions(@AuthenticationPrincipal JsonUserPrincipal user,@RequestParam(defaultValue="5") int size){return feed.suggestions(user.getId(),size);}
    @PostMapping("/users/{id}/block") public ResponseEntity<Void> block(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id){feed.block(user.getId(),id,true);return ResponseEntity.noContent().build();}
    @DeleteMapping("/users/{id}/block") public ResponseEntity<Void> unblock(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id){feed.block(user.getId(),id,false);return ResponseEntity.noContent().build();}
    @PostMapping("/users/{id}/mute") public ResponseEntity<Void> mute(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id){feed.mute(user.getId(),id,true);return ResponseEntity.noContent().build();}
    @DeleteMapping("/users/{id}/mute") public ResponseEntity<Void> unmute(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id){feed.mute(user.getId(),id,false);return ResponseEntity.noContent().build();}
    @GetMapping("/users/me/{type}") public List<String> safetyList(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable String type){return feed.safetyList(user.getId(),type);}

    @PostMapping("/polls/{id}/vote") public PollDto vote(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id,@Valid @RequestBody VoteRequest request){return feed.vote(user.getId(),id,request.option());}
    @GetMapping("/polls/{id}/results") public PollDto poll(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){return feed.pollResults(user.getId(),id);}
    @GetMapping("/search") public SearchResults search(@AuthenticationPrincipal JsonUserPrincipal user,@RequestParam String q,@RequestParam(defaultValue="20") int limit){return feed.search(user.getId(),q,limit);}
    @GetMapping("/trending") public List<TopicDto> trending(@AuthenticationPrincipal JsonUserPrincipal user,@RequestParam(defaultValue="8") int limit){return feed.trendingTopics(user.getId(),limit);}
    @GetMapping("/movies/{movieId}/posts") public FeedPage moviePosts(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int movieId,@RequestParam(defaultValue="latest") String type,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="20") int size){return feed.moviePosts(user.getId(),movieId,type,cursor,size);}
    @GetMapping("/preferences") public PreferencesDto preferences(@AuthenticationPrincipal JsonUserPrincipal user){return feed.preferences(user.getId());}
    @PutMapping("/preferences") public PreferencesDto savePreferences(@AuthenticationPrincipal JsonUserPrincipal user,@Valid @RequestBody PreferencesRequest request){return feed.savePreferences(user.getId(),request);}
    @PostMapping("/posts/{id}/reports") public ResponseEntity<Void> report(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id,@Valid @RequestBody ReportRequest request){feed.report(user.getId(),id,request.reason());return ResponseEntity.noContent().build();}

    @PostMapping(value="/media",consumes=MediaType.MULTIPART_FORM_DATA_VALUE) public UploadDto upload(@AuthenticationPrincipal JsonUserPrincipal user,@RequestPart("file") MultipartFile file){return feed.upload(user.getId(),file);}
    @DeleteMapping("/media/{id}") public ResponseEntity<Void> deleteUpload(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){feed.deleteUnattachedMedia(user.getId(),id);return ResponseEntity.noContent().build();}
    @GetMapping("/media/{id}") public ResponseEntity<Resource> media(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable UUID id){MediaFile m=feed.media(user.getId(),id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.mediaType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(m.path()));}
    @GetMapping("/users/{id}/avatar") public ResponseEntity<Resource> avatar(@AuthenticationPrincipal JsonUserPrincipal user,@PathVariable int id){MediaFile m=feed.avatar(user.getId(),id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(m.mediaType())).cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").body(new FileSystemResource(m.path()));}
}
