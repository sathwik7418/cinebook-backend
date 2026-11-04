package com.cinebook.feed.service;

import com.cinebook.common.json.JsonLoaderService;
import com.cinebook.common.json.JsonLoaderService.AuthUserJson;
import com.cinebook.common.json.JsonLoaderService.MovieJson;
import com.cinebook.feed.config.SocialDatabaseProperties;
import com.cinebook.feed.dto.FeedDtos.*;
import com.cinebook.user.model.JsonUserPrincipal;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.cinebook.feed.dto.FeedDtos.*;

@Service
@ConditionalOnProperty(prefix = "cinebook.social.database", name = "enabled", havingValue = "true")
public class SocialFeedService {
    private static final int MAX_PAGE = 50;
    private static final Pattern TOPIC = Pattern.compile("(?<![\\p{L}\\p{N}_])#([\\p{L}\\p{N}_]{1,80})", Pattern.UNICODE_CHARACTER_CLASS);
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final TransactionTemplate tx;
    private final JsonLoaderService json;
    private final Path mediaRoot;
    private volatile List<MovieDto> movieCache;

    public SocialFeedService(JdbcTemplate socialJdbcTemplate,
                             org.springframework.transaction.support.TransactionTemplate socialTransactionTemplate,
                             JsonLoaderService jsonLoaderService,
                             SocialDatabaseProperties properties,
                             @org.springframework.beans.factory.annotation.Value("${cinebook.social.media.directory:./data/cinebook-social-media}") String mediaDirectory) {
        this.jdbc = socialJdbcTemplate;
        this.namedJdbc = new NamedParameterJdbcTemplate(socialJdbcTemplate);
        this.tx = socialTransactionTemplate;
        this.json = jsonLoaderService;
        this.mediaRoot = Path.of(mediaDirectory).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void prepareMediaDirectory() {
        try { Files.createDirectories(mediaRoot); }
        catch (IOException ex) { throw new IllegalStateException("CineFeed media storage is unavailable.", ex); }
    }

    public UserDto me(JsonUserPrincipal principal) {
        syncAccounts();
        return user(principal.getId(), principal.getId());
    }

    public FeedPage feed(int viewerId, String type, String cursor, int requestedSize) {
        return feedFiltered(viewerId,type,cursor,requestedSize,null,null,null,null);
    }

    private FeedPage feedFiltered(int viewerId, String type, String cursor, int requestedSize, Integer movieFilter, Integer authorFilter) { return feedFiltered(viewerId,type,cursor,requestedSize,movieFilter,authorFilter,null,null); }
    private FeedPage feedFiltered(int viewerId, String type, String cursor, int requestedSize, Integer movieFilter, Integer authorFilter, String contentType, Integer activityAuthor) {
        syncAccounts();
        String feedType = normalizeFeedType(type);
        int size = Math.max(1, Math.min(MAX_PAGE, requestedSize));
        Cursor before = parseCursor(cursor);
        String score = scoreSql(feedType, viewerId);
        String scope = visibilitySql(viewerId, "p");
        String following = feedType.equals("following")
                ? " AND EXISTS (SELECT 1 FROM user_follows ff WHERE ff.follower_id=" + viewerId + " AND ff.followed_id=p.author_id) " : "";
        String noBlocked = noBlockSql(viewerId, "p.author_id");
        String mute = " AND NOT EXISTS (SELECT 1 FROM user_mutes um WHERE um.muter_id=" + viewerId + " AND um.muted_id=p.author_id) ";
        String base = "SELECT p.id,p.author_id,p.content,p.visibility,p.spoiler_flag,p.post_type,p.quoted_post_id,p.movie_id,p.created_at,p.updated_at, " +
                "a.username,a.display_name,a.bio,a.avatar_reference, " +
                "(SELECT count(*) FROM post_reactions r WHERE r.post_id=p.id) likes, " +
                "(SELECT count(*) FROM post_comments c WHERE c.post_id=p.id AND c.deleted_at IS NULL) comments, " +
                "(SELECT count(*) FROM post_reposts r WHERE r.post_id=p.id) reposts, " +
                "EXISTS(SELECT 1 FROM post_reactions r WHERE r.post_id=p.id AND r.user_id=" + viewerId + ") liked, " +
                "EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=" + viewerId + ") saved, " +
                "EXISTS(SELECT 1 FROM post_reposts r WHERE r.post_id=p.id AND r.user_id=" + viewerId + ") reposted, " +
                "(SELECT storage_reference FROM post_media pm WHERE pm.post_id=p.id ORDER BY pm.position LIMIT 1) media, " +
                score + " AS feed_score " +
                "FROM cine_posts p JOIN social_accounts a ON a.id=p.author_id " +
                "WHERE p.deleted_at IS NULL AND " + scope + " AND " + noBlocked + mute + following + (movieFilter==null?"":" AND p.movie_id="+movieFilter) + (authorFilter==null?"":" AND p.author_id="+authorFilter) + (activityAuthor==null?"":contentPredicate(contentType,activityAuthor));
        String order = "feed_score DESC,created_at DESC,id DESC";
        String outerCursor = before == null ? "" : " WHERE (feed_score < ? OR (feed_score = ? AND (created_at < ? OR (created_at = ? AND id < ?::uuid)))) ";
        String sql = "SELECT * FROM (" + base + ") q" + outerCursor + " ORDER BY " + order + " LIMIT ?";
        List<Object> args = new ArrayList<>();
        if (before != null) { args.add(before.score); args.add(before.score); args.add(java.sql.Timestamp.from(before.createdAt)); args.add(java.sql.Timestamp.from(before.createdAt)); args.add(before.id); }
        args.add(size + 1);
        List<Map<String,Object>> rows = jdbc.queryForList(sql, args.toArray());
        boolean more = rows.size() > size;
        if (more) rows = new ArrayList<>(rows.subList(0, size));
        List<PostDto> items = rows.stream().map(row -> post(row, viewerId)).toList();
        String next = more && !rows.isEmpty() ? cursorFor(rows.get(rows.size()-1)) : null;
        return new FeedPage(items, next, more);
    }

    public PostDto getPost(int viewerId, UUID postId) {
        syncAccounts();
        return postById(viewerId, postId).orElseThrow(() -> notFound("CineTake not found."));
    }

    public PostDto createPost(int userId, CreatePostRequest request) {
        syncAccounts();
        String content = request.content() == null ? "" : request.content().trim();
        String visibility = normalizeVisibility(request.visibility());
        String type = normalizePostType(request.postType(), request.poll(), request.mediaIds());
        UUID id = UUID.randomUUID();
        Integer movieId = validateMovie(request.movieId());
        UUID quote = parseOptionalUuid(request.quoteOf(), "Quoted CineTake");
        List<String> mediaIds = request.mediaIds() == null ? List.of() : request.mediaIds();
        if (mediaIds.size() > 4) throw badRequest("Attach no more than four images.");
        validateContent(content, request.poll(), mediaIds);
        if (type.equals("POLL") && request.poll() == null) throw badRequest("Poll data is required.");
        if (request.poll() != null) validatePoll(request.poll());
        tx.execute(status -> {
            if (quote != null) postById(userId, quote).orElseThrow(() -> notFound("Quoted CineTake not found."));
            jdbc.update("INSERT INTO cine_posts(id,author_id,content,visibility,spoiler_flag,post_type,quoted_post_id,movie_id) VALUES (?,?,?,?,?,?,?,?)",
                    id, userId, content, visibility, request.spoiler(), type, quote, movieId);
            attachMedia(userId, id, mediaIds);
            saveTopics(id, content);
            if (request.poll() != null) createPoll(id, request.poll());
            return null;
        });
        return getPost(userId, id);
    }

    public PostDto updatePost(int userId, UUID postId, UpdatePostRequest request) {
        tx.execute(status -> {
            requirePostOwner(userId, postId);
            Integer movieId = request.movieId() == null ? currentMovie(postId) : validateMovie(request.movieId());
            String visibility = request.visibility() == null ? currentVisibility(postId) : normalizeVisibility(request.visibility());
            String content = request.content() == null ? currentContent(postId) : request.content().trim();
            if (content.length() > 500) throw badRequest("CineTakes are limited to 500 characters.");
            jdbc.update("UPDATE cine_posts SET content=?,visibility=?,spoiler_flag=COALESCE(?,spoiler_flag),movie_id=?,updated_at=now() WHERE id=?",
                    content, visibility, request.spoiler(), movieId, postId);
            jdbc.update("DELETE FROM post_topics WHERE post_id=?", postId);
            saveTopics(postId, content);
            return null;
        });
        return getPost(userId, postId);
    }

    public void deletePost(int userId, UUID postId) {
        int changed = jdbc.update("UPDATE cine_posts SET deleted_at=now(),updated_at=now() WHERE id=? AND author_id=? AND deleted_at IS NULL", postId, userId);
        if (changed == 0) throw notFound("CineTake not found.");
    }

    public PostDto react(int userId, UUID postId, boolean like) {
        requireReadablePost(userId, postId);
        if (like) jdbc.update("INSERT INTO post_reactions(post_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING", postId, userId);
        else jdbc.update("DELETE FROM post_reactions WHERE post_id=? AND user_id=?", postId, userId);
        return getPost(userId, postId);
    }

    public void follow(int userId, int targetId, boolean following) {
        syncAccounts();
        if (userId == targetId) throw badRequest("You cannot follow your own account.");
        if (jdbc.queryForObject("SELECT count(*) FROM social_accounts WHERE id=?", Integer.class, targetId) == 0) throw notFound("Account not found.");
        if (isBlockedEitherWay(userId, targetId)) throw notFound("Account not found.");
        if (following) jdbc.update("INSERT INTO user_follows(follower_id,followed_id) VALUES (?,?) ON CONFLICT DO NOTHING", userId, targetId);
        else jdbc.update("DELETE FROM user_follows WHERE follower_id=? AND followed_id=?", userId, targetId);
    }

    public UserPage people(int viewerId, int targetId, String relation, String cursor, int requestedSize) {
        syncAccounts();
        if (!relation.equals("followers") && !relation.equals("following")) throw badRequest("Unknown account list.");
        int size = Math.max(1, Math.min(MAX_PAGE, requestedSize));
        UUIDKeyset keyset = parseUserCursor(cursor);
        String join = relation.equals("followers") ? "f.followed_id=? AND f.follower_id=a.id" : "f.follower_id=? AND f.followed_id=a.id";
        String sql = "SELECT a.id,a.username,a.display_name,a.bio,a.avatar_reference,a.created_at, (SELECT count(*) FROM user_follows x WHERE x.followed_id=a.id) followers, " +
                "(SELECT count(*) FROM user_follows x WHERE x.follower_id=a.id) following, " +
                "EXISTS(SELECT 1 FROM user_follows x WHERE x.follower_id=? AND x.followed_id=a.id) followed_by_me, " +
                "EXISTS(SELECT 1 FROM user_follows x WHERE x.follower_id=a.id AND x.followed_id=?) follows_me " +
                "FROM user_follows f JOIN social_accounts a ON " + join + " WHERE NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=? AND b.blocked_id=a.id) OR (b.blocker_id=a.id AND b.blocked_id=?)) " +
                (keyset == null ? "" : "AND (a.username > ? OR (a.username=? AND a.id > ?)) ") + "ORDER BY lower(a.username),a.id LIMIT ?";
        List<Object> args = new ArrayList<>(List.of(viewerId, viewerId, targetId, viewerId, viewerId));
        if (keyset != null) { args.add(keyset.username); args.add(keyset.username); args.add(keyset.id); }
        args.add(size + 1);
        List<Map<String,Object>> rows = jdbc.queryForList(sql, args.toArray());
        boolean more = rows.size() > size;
        if (more) rows = new ArrayList<>(rows.subList(0,size));
        List<UserDto> users = rows.stream().map(r -> userFromRow(r, viewerId)).toList();
        String next = more && !rows.isEmpty() ? Base64.getUrlEncoder().withoutPadding().encodeToString((rows.get(rows.size()-1).get("username") + "|" + rows.get(rows.size()-1).get("id")).getBytes(java.nio.charset.StandardCharsets.UTF_8)) : null;
        return new UserPage(users,next,more);
    }

    public UserPage suggestions(int viewerId, int requestedSize) {
        syncAccounts();
        int size = Math.max(1, Math.min(20, requestedSize));
        String sql = "SELECT a.id,a.username,a.display_name,a.bio,a.avatar_reference,a.created_at,(SELECT count(*) FROM user_follows x WHERE x.followed_id=a.id) followers,(SELECT count(*) FROM user_follows x WHERE x.follower_id=a.id) following, " +
                "EXISTS(SELECT 1 FROM user_follows x WHERE x.follower_id=? AND x.followed_id=a.id) followed_by_me, EXISTS(SELECT 1 FROM user_follows x WHERE x.follower_id=a.id AND x.followed_id=?) follows_me, " +
                "(SELECT count(*) FROM user_follows mutual JOIN user_follows their ON their.follower_id=mutual.followed_id AND their.followed_id=a.id WHERE mutual.follower_id=? AND mutual.followed_id=a.id) mutuals, " +
                "(SELECT count(*) FROM cine_posts p WHERE p.author_id=a.id AND p.deleted_at IS NULL AND p.visibility='PUBLIC' AND p.created_at>now()-interval '30 days') activity " +
                "FROM social_accounts a WHERE a.id<>? AND NOT EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id=? AND f.followed_id=a.id) " +
                "AND NOT EXISTS(SELECT 1 FROM user_blocks b WHERE (b.blocker_id=? AND b.blocked_id=a.id) OR (b.blocker_id=a.id AND b.blocked_id=?)) " +
                "ORDER BY mutuals DESC,activity DESC,lower(a.username),a.id LIMIT ?";
        List<Map<String,Object>> rows = jdbc.queryForList(sql, viewerId,viewerId,viewerId,viewerId,viewerId,viewerId,viewerId,size);
        return new UserPage(rows.stream().map(r -> userFromRow(r,viewerId)).toList(),null,false);
    }

    public UserDto user(int viewerId, int targetId) {
        syncAccounts();
        if (isBlockedEitherWay(viewerId,targetId)) throw notFound("Account not found.");
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT a.id,a.username,a.display_name,a.bio,a.avatar_reference,a.created_at,(SELECT count(*) FROM user_follows x WHERE x.followed_id=a.id) followers,(SELECT count(*) FROM user_follows x WHERE x.follower_id=a.id) following,EXISTS(SELECT 1 FROM user_follows x WHERE x.follower_id=? AND x.followed_id=a.id) followed_by_me,EXISTS(SELECT 1 FROM user_follows x WHERE x.follower_id=a.id AND x.followed_id=?) follows_me FROM social_accounts a WHERE a.id=?",viewerId,viewerId,targetId);
        if(rows.isEmpty()) throw notFound("Account not found.");
        return userFromRow(rows.get(0),viewerId);
    }

    public UserDto updateProfile(int userId, ProfileUpdateRequest request) {
        syncAccounts();
        if (request.name() == null || request.name().isBlank()) throw badRequest("Display name is required.");
        if (request.handle() == null || !request.handle().matches("[A-Za-z0-9_.-]{3,30}") || request.handle().matches("(?i)id[-_]?[0-9]+")) throw badRequest("Choose a public username, not an internal account ID.");
        if (request.bio() != null && request.bio().length() > 280) throw badRequest("Bio is limited to 280 characters.");
        String avatar = null;
        if (request.avatarId() != null && !request.avatarId().isBlank()) {
            UUID id = parseOptionalUuid(request.avatarId(), "Avatar");
            List<Map<String,Object>> uploaded = jdbc.queryForList("SELECT storage_reference FROM post_media WHERE id=? AND owner_id=? AND post_id IS NULL", id, userId);
            if (uploaded.isEmpty()) throw badRequest("That avatar upload is unavailable.");
            avatar = (String) uploaded.get(0).get("storage_reference");
        }
        try {
            if (request.removeAvatar()) jdbc.update("UPDATE social_accounts SET username=?,display_name=?,bio=?,avatar_reference=NULL,updated_at=now() WHERE id=?", request.handle().trim(), request.name().trim(), Objects.toString(request.bio(), "").trim(), userId);
            else if (avatar != null) jdbc.update("UPDATE social_accounts SET username=?,display_name=?,bio=?,avatar_reference=?,updated_at=now() WHERE id=?", request.handle().trim(), request.name().trim(), Objects.toString(request.bio(), "").trim(), avatar, userId);
            else jdbc.update("UPDATE social_accounts SET username=?,display_name=?,bio=?,updated_at=now() WHERE id=?", request.handle().trim(), request.name().trim(), Objects.toString(request.bio(), "").trim(), userId);
        } catch (DuplicateKeyException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "That username is already in use."); }
        return user(userId, userId);
    }

    public MediaFile avatar(int viewerId, int ownerId) {
        if (isBlockedEitherWay(viewerId, ownerId)) throw notFound("Profile not found.");
        List<Map<String,Object>> rows = jdbc.queryForList("SELECT m.storage_reference,m.media_type FROM social_accounts a JOIN post_media m ON m.storage_reference=a.avatar_reference WHERE a.id=?", ownerId);
        if (rows.isEmpty()) throw notFound("Avatar not found.");
        Path path = mediaRoot.resolve((String) rows.get(0).get("storage_reference")).normalize();
        if (!path.startsWith(mediaRoot) || !Files.isRegularFile(path)) throw notFound("Avatar not found.");
        return new MediaFile(path, (String) rows.get(0).get("media_type"));
    }

    public CommentPage comments(int viewerId, UUID postId, String cursor, int requestedSize) {
        requireReadablePost(viewerId,postId);
        int size=Math.max(1,Math.min(50,requestedSize));
        String sql="SELECT c.id,c.post_id,c.parent_comment_id,c.author_id,c.content,c.created_at,c.updated_at,c.deleted_at,a.username,a.display_name,a.avatar_reference FROM post_comments c JOIN social_accounts a ON a.id=c.author_id WHERE c.post_id=? AND (c.deleted_at IS NULL OR EXISTS (SELECT 1 FROM post_comments child WHERE child.parent_comment_id=c.id AND child.deleted_at IS NULL)) AND "+noBlockSql(viewerId,"c.author_id")+
                (cursor==null||cursor.isBlank()?"":"AND (c.created_at,c.id)>(?::timestamptz,?::uuid) ")+"ORDER BY c.created_at,c.id LIMIT ?";
        List<Object> args=new ArrayList<>(List.of(postId));
        CommentCursor before=parseCommentCursor(cursor);
        if(before!=null){args.add(before.createdAt.toString());args.add(before.id);}
        args.add(size+1);
        List<Map<String,Object>> rows=jdbc.queryForList(sql,args.toArray());boolean more=rows.size()>size;if(more)rows=new ArrayList<>(rows.subList(0,size));
        List<CommentDto> items=rows.stream().map(this::comment).toList();String next=more&&!rows.isEmpty()?Base64.getUrlEncoder().withoutPadding().encodeToString((items.get(items.size()-1).createdAt()+"|"+items.get(items.size()-1).id()).getBytes(java.nio.charset.StandardCharsets.UTF_8)):null;
        return new CommentPage(items,next,more);
    }

    public CommentDto addComment(int userId, UUID postId, CreateCommentRequest request) {
        requireReadablePost(userId,postId);
        String content=request.content().trim();if(content.isEmpty())throw badRequest("Comment cannot be empty.");
        UUID parent=parseOptionalUuid(request.parentId(),"Parent comment");UUID id=UUID.randomUUID();
        tx.execute(status->{
            if(parent!=null){Integer depth=jdbc.query("SELECT depth FROM post_comments WHERE id=? AND post_id=? AND deleted_at IS NULL",rs->rs.next()?rs.getInt(1):null,parent,postId);if(depth==null)throw notFound("Parent comment not found.");if(depth>=1)throw badRequest("Replies can be one level deep.");}
            jdbc.update("INSERT INTO post_comments(id,post_id,author_id,parent_comment_id,depth,content) VALUES (?,?,?,?,?,?)",id,postId,userId,parent,parent==null?0:1,content);return null;
        });
        return commentById(userId,id);
    }

    public CommentDto editComment(int userId, UUID commentId, UpdateCommentRequest request) {
        requireReadableComment(userId, commentId);
        int n=jdbc.update("UPDATE post_comments SET content=?,updated_at=now() WHERE id=? AND author_id=? AND deleted_at IS NULL",request.content().trim(),commentId,userId);if(n==0)throw notFound("Comment not found.");return commentById(userId,commentId);
    }

    public void deleteComment(int userId, UUID commentId) {
        requireReadableComment(userId, commentId);
        int n=jdbc.update("UPDATE post_comments SET deleted_at=now(),content='[deleted]',updated_at=now() WHERE id=? AND author_id=? AND deleted_at IS NULL",commentId,userId);if(n==0)throw notFound("Comment not found.");
    }

    public void savePost(int userId,UUID postId,boolean save){requireReadablePost(userId,postId);if(save)jdbc.update("INSERT INTO saved_posts(user_id,post_id) VALUES (?,?) ON CONFLICT DO NOTHING",userId,postId);else jdbc.update("DELETE FROM saved_posts WHERE user_id=? AND post_id=?",userId,postId);}
    public FeedPage saved(int userId,String cursor,int size){return relationPosts(userId,cursor,size,"saved_posts","s.user_id=?","s.post_id=p.id",userId);}
    public PostDto repost(int userId,UUID postId,boolean active){requireReadablePost(userId,postId);if(active)jdbc.update("INSERT INTO post_reposts(post_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",postId,userId);else jdbc.update("DELETE FROM post_reposts WHERE post_id=? AND user_id=?",postId,userId);return getPost(userId,postId);}

    public PollDto vote(int userId,UUID pollId,String option){
        UUID optionId=jdbc.query("SELECT o.id FROM poll_options o JOIN polls p ON p.id=o.poll_id WHERE p.id=? AND o.option_text=? AND (p.expires_at IS NULL OR p.expires_at>now())",rs->rs.next()?UUID.fromString(rs.getString(1)):null,pollId,option);
        if(optionId==null){boolean exists=Boolean.TRUE.equals(jdbc.query("SELECT EXISTS(SELECT 1 FROM polls WHERE id=?)",rs->rs.next()&&rs.getBoolean(1),pollId));if(!exists)throw notFound("Poll not found.");throw new ResponseStatusException(HttpStatus.GONE,"This poll is closed or the selected choice is invalid.");}
        UUID postId=jdbc.queryForObject("SELECT post_id FROM polls WHERE id=?",UUID.class,pollId);requireReadablePost(userId,postId);
        jdbc.update("INSERT INTO poll_votes(poll_id,option_id,user_id) VALUES (?,?,?) ON CONFLICT (poll_id,user_id) DO NOTHING",pollId,optionId,userId);
        UUID vote=jdbc.query("SELECT option_id FROM poll_votes WHERE poll_id=? AND user_id=?",rs->rs.next()?UUID.fromString(rs.getString(1)):null,pollId,userId);
        return pollDto(pollId,userId,vote);
    }

    public FeedPage moviePosts(int viewerId,int movieId,String type,String cursor,int size){validateMovie(movieId);return feedFiltered(viewerId,type,cursor,size,movieId,null);}
    public FeedPage userPosts(int viewerId,int authorId,String type,String cursor,int size){user(viewerId,authorId);return feedFiltered(viewerId,type,cursor,size,null,authorId);}
    public FeedPage userContent(int viewerId,int authorId,String contentType,String cursor,int size){user(viewerId,authorId);if(!Set.of("posts","replies","reposts","media").contains(contentType))throw badRequest("Unknown profile tab.");if(contentType.equals("posts"))return userPosts(viewerId,authorId,"latest",cursor,size);return feedFiltered(viewerId,"latest",cursor,size,null,null,contentType,authorId);}
    private String contentPredicate(String type,int authorId){return switch(type){case "replies"->" AND EXISTS(SELECT 1 FROM post_comments pc WHERE pc.post_id=p.id AND pc.author_id="+authorId+" AND pc.deleted_at IS NULL)";case "reposts"->" AND EXISTS(SELECT 1 FROM post_reposts rp WHERE rp.post_id=p.id AND rp.user_id="+authorId+")";case "media"->" AND EXISTS(SELECT 1 FROM post_media pm WHERE pm.post_id=p.id)";default->"";};}

    public PollDto pollResults(int userId,UUID pollId){UUID postId=jdbc.query("SELECT post_id FROM polls WHERE id=?",rs->rs.next()?UUID.fromString(rs.getString(1)):null,pollId);if(postId==null)throw notFound("Poll not found.");requireReadablePost(userId,postId);return pollDto(pollId,userId,null);}

    public SearchResults search(int viewerId,String rawQuery,int limit){
        syncAccounts();String q=rawQuery==null?"":rawQuery.trim();if(q.length()<2) return new SearchResults(List.of(),List.of(),List.of(),List.of(),null);String term=q.toLowerCase(Locale.ROOT);int size=Math.max(1,Math.min(20,limit));
        String sql="SELECT p.id,p.author_id,p.content,p.visibility,p.spoiler_flag,p.post_type,p.quoted_post_id,p.movie_id,p.created_at,p.updated_at,a.username,a.display_name,a.bio,a.avatar_reference,(SELECT count(*) FROM post_reactions r WHERE r.post_id=p.id) likes,(SELECT count(*) FROM post_comments c WHERE c.post_id=p.id AND c.deleted_at IS NULL) comments,(SELECT count(*) FROM post_reposts r WHERE r.post_id=p.id) reposts,EXISTS(SELECT 1 FROM post_reactions r WHERE r.post_id=p.id AND r.user_id=?) liked,EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=?) saved,EXISTS(SELECT 1 FROM post_reposts r WHERE r.post_id=p.id AND r.user_id=?) reposted,(SELECT storage_reference FROM post_media pm WHERE pm.post_id=p.id ORDER BY pm.position LIMIT 1) media,0.0 feed_score FROM cine_posts p JOIN social_accounts a ON a.id=p.author_id WHERE p.deleted_at IS NULL AND p.visibility='PUBLIC' AND to_tsvector('simple',p.content) @@ plainto_tsquery('simple',?) AND "+noBlockSql(viewerId,"p.author_id")+"AND NOT EXISTS(SELECT 1 FROM user_mutes um WHERE um.muter_id="+viewerId+" AND um.muted_id=p.author_id) ORDER BY p.created_at DESC,p.id DESC LIMIT ?";
        List<Map<String,Object>> postRows=jdbc.queryForList(sql,viewerId,viewerId,viewerId,term,size);
        String userSql="SELECT a.id,a.username,a.display_name,a.bio,a.avatar_reference,(SELECT count(*) FROM user_follows x WHERE x.followed_id=a.id) followers,(SELECT count(*) FROM user_follows x WHERE x.follower_id=a.id) following,EXISTS(SELECT 1 FROM user_follows x WHERE x.follower_id=? AND x.followed_id=a.id) followed_by_me,EXISTS(SELECT 1 FROM user_follows x WHERE x.follower_id=a.id AND x.followed_id=?) follows_me FROM social_accounts a WHERE lower(a.username) LIKE ? OR lower(a.display_name) LIKE ? ORDER BY lower(a.username) LIMIT ?";
        List<UserDto> users=jdbc.queryForList(userSql,viewerId,viewerId,"%"+escapeLike(term)+"%","%"+escapeLike(term)+"%",size).stream().filter(r->!isBlockedEitherWay(viewerId,((Number)r.get("id")).intValue())).map(r->userFromRow(r,viewerId)).toList();
        List<MovieDto> movies=movieCatalogue().stream().filter(m->m.title().toLowerCase(Locale.ROOT).contains(term)||m.genres().stream().anyMatch(g->g.toLowerCase(Locale.ROOT).contains(term))).limit(size).toList();
        List<TopicDto> topics=jdbc.query("SELECT t.normalized_name,t.display_name,count(*) post_count FROM topics t JOIN post_topics pt ON pt.topic_id=t.id JOIN cine_posts p ON p.id=pt.post_id WHERE p.deleted_at IS NULL AND p.visibility='PUBLIC' AND t.normalized_name LIKE ? AND "+noBlockSql(viewerId,"p.author_id")+"AND NOT EXISTS(SELECT 1 FROM user_mutes um WHERE um.muter_id="+viewerId+" AND um.muted_id=p.author_id) GROUP BY t.id ORDER BY post_count DESC,t.normalized_name LIMIT ?",(rs,n)->new TopicDto(rs.getString(1),rs.getString(2),rs.getLong(3)),"%"+escapeLike(term)+"%",size);
        return new SearchResults(postRows.stream().map(r->post(r,viewerId)).toList(),users,movies,topics,null);
    }

    public List<TopicDto> trendingTopics(int viewerId,int size){
        return jdbc.query("SELECT t.normalized_name,t.display_name,count(DISTINCT p.id) post_count FROM topics t JOIN post_topics pt ON pt.topic_id=t.id JOIN cine_posts p ON p.id=pt.post_id WHERE p.deleted_at IS NULL AND p.visibility='PUBLIC' AND p.created_at>now()-interval '7 days' AND "+noBlockSql(viewerId,"p.author_id")+"AND NOT EXISTS(SELECT 1 FROM user_mutes um WHERE um.muter_id="+viewerId+" AND um.muted_id=p.author_id) GROUP BY t.id ORDER BY (count(DISTINCT p.id)::numeric/(1+extract(epoch from (now()-min(p.created_at)))/3600/24)) DESC,t.normalized_name LIMIT ?",(rs,n)->new TopicDto(rs.getString(1),rs.getString(2),rs.getLong(3)),Math.max(1,Math.min(20,size)));
    }

    public PreferencesDto preferences(int userId){
        return jdbc.query("SELECT selected_genres,selected_languages,selected_post_types,hide_spoilers FROM feed_preferences WHERE user_id=?",rs->rs.next()?new PreferencesDto(Arrays.asList((String[])rs.getArray(1).getArray()),Arrays.asList((String[])rs.getArray(2).getArray()),Arrays.asList((String[])rs.getArray(3).getArray()),rs.getBoolean(4)):new PreferencesDto(List.of(),List.of(),List.of(),true),userId);
    }

    public PreferencesDto savePreferences(int userId,PreferencesRequest request){
        List<String> genres=boundedValues(request.genres(),30,40);List<String> languages=boundedValues(request.languages(),20,40);List<String> types=boundedValues(request.postTypes(),4,20);
        jdbc.update("INSERT INTO feed_preferences(user_id,selected_genres,selected_languages,selected_post_types,hide_spoilers,updated_at) VALUES (?,?,?,?,?,now()) ON CONFLICT(user_id) DO UPDATE SET selected_genres=EXCLUDED.selected_genres,selected_languages=EXCLUDED.selected_languages,selected_post_types=EXCLUDED.selected_post_types,hide_spoilers=EXCLUDED.hide_spoilers,updated_at=now()",userId,genres.toArray(String[]::new),languages.toArray(String[]::new),types.toArray(String[]::new),request.hideSpoilers()==null||request.hideSpoilers());
        return preferences(userId);
    }

    public void block(int userId,int targetId,boolean active){syncAccounts();if(userId==targetId)throw badRequest("You cannot block your own account.");requireAccount(targetId);if(active){jdbc.update("INSERT INTO user_blocks(blocker_id,blocked_id) VALUES (?,?) ON CONFLICT DO NOTHING",userId,targetId);jdbc.update("DELETE FROM user_follows WHERE (follower_id=? AND followed_id=?) OR (follower_id=? AND followed_id=?)",userId,targetId,targetId,userId);}else jdbc.update("DELETE FROM user_blocks WHERE blocker_id=? AND blocked_id=?",userId,targetId);}
    public void mute(int userId,int targetId,boolean active){syncAccounts();if(userId==targetId)throw badRequest("You cannot mute your own account.");requireAccount(targetId);if(active)jdbc.update("INSERT INTO user_mutes(muter_id,muted_id) VALUES (?,?) ON CONFLICT DO NOTHING",userId,targetId);else jdbc.update("DELETE FROM user_mutes WHERE muter_id=? AND muted_id=?",userId,targetId);}
    public List<String> safetyList(int userId,String type){syncAccounts();String column=type.equals("blocks")?"blocked_id":type.equals("mutes")?"muted_id":null;if(column==null)throw badRequest("Unknown safety list.");String table=type.equals("blocks")?"user_blocks":"user_mutes";String owner=type.equals("blocks")?"blocker_id":"muter_id";return jdbc.query("SELECT "+column+" FROM "+table+" WHERE "+owner+"=? ORDER BY created_at DESC",(rs,n)->String.valueOf(rs.getInt(1)),userId);}
    public void report(int userId,UUID postId,String reason){requireReadablePost(userId,postId);jdbc.update("INSERT INTO content_reports(id,post_id,reporter_id,reason) VALUES (?,?,?,?) ON CONFLICT(post_id,reporter_id) DO NOTHING",UUID.randomUUID(),postId,userId,reason==null?"":reason.trim());}

    public UploadDto upload(int userId,MultipartFile file){
        if(file==null||file.isEmpty())throw badRequest("Choose an image to upload.");if(file.getSize()>8L*1024*1024)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Image files must be 8 MB or smaller.");
        try {
            byte[] bytes=file.getBytes();String ext=detectImage(bytes,file.getContentType());UUID id=UUID.randomUUID();String storage=id+"."+ext;Path target=mediaRoot.resolve(storage).normalize();if(!target.startsWith(mediaRoot))throw badRequest("Invalid media path.");Files.write(target,bytes);
            jdbc.update("INSERT INTO post_media(id,owner_id,media_type,storage_reference) VALUES (?,?,?,?)",id,userId,"image/"+ext,storage);
            return new UploadDto(id.toString(),"image/"+ext,"/api/feed/media/"+id);
        } catch(IOException ex){throw new IllegalStateException("Image upload could not be stored.",ex);}
    }

    public MediaFile media(int viewerId,UUID mediaId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT pm.storage_reference,pm.media_type,pm.owner_id,pm.post_id FROM post_media pm WHERE pm.id=?",mediaId);if(rows.isEmpty())throw notFound("Image not found.");Map<String,Object> row=rows.get(0);UUID postId=(UUID)row.get("post_id");int owner=((Number)row.get("owner_id")).intValue();if(postId==null){if(owner!=viewerId&&!chatMediaVisibleTo(viewerId,mediaId))throw notFound("Image not found.");}else requireReadablePost(viewerId,postId);Path path=mediaRoot.resolve((String)row.get("storage_reference")).normalize();if(!path.startsWith(mediaRoot)||!Files.isRegularFile(path))throw notFound("Image not found.");return new MediaFile(path,(String)row.get("media_type"));
    }

    public record MediaFile(Path path,String mediaType) { }

    public void deleteUnattachedMedia(int userId,UUID mediaId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT storage_reference FROM post_media WHERE id=? AND owner_id=? AND post_id IS NULL",mediaId,userId);
        if(rows.isEmpty())return;
        if(chatMediaReferenced(mediaId))throw new ResponseStatusException(HttpStatus.CONFLICT,"This image is attached to a chat and cannot be removed here.");
        Path path=mediaRoot.resolve((String)rows.get(0).get("storage_reference")).normalize();
        if(!path.startsWith(mediaRoot))throw notFound("Image not found.");
        try{Files.deleteIfExists(path);}catch(IOException ex){throw new IllegalStateException("Image cleanup failed.",ex);}
        jdbc.update("DELETE FROM post_media WHERE id=? AND owner_id=? AND post_id IS NULL",mediaId,userId);
    }

    private boolean chatMediaVisibleTo(int viewerId,UUID mediaId){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('cinechat.message_attachments') IS NOT NULL AND to_regclass('cinechat.conversation_avatars') IS NOT NULL",Boolean.class)))return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinechat.message_attachments ma JOIN cinechat.messages m ON m.id=ma.message_id AND m.deleted_at IS NULL JOIN cinechat.conversation_members cm ON cm.conversation_id=m.conversation_id AND cm.user_id=? AND cm.left_at IS NULL WHERE ma.media_id=? UNION ALL SELECT 1 FROM cinechat.conversation_avatars ca JOIN cinechat.conversation_members cm ON cm.conversation_id=ca.conversation_id AND cm.user_id=? AND cm.left_at IS NULL WHERE ca.media_id=?)",Boolean.class,viewerId,mediaId,viewerId,mediaId));
    }

    private boolean chatMediaReferenced(UUID mediaId){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT to_regclass('cinechat.message_attachments') IS NOT NULL AND to_regclass('cinechat.conversation_avatars') IS NOT NULL",Boolean.class)))return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM cinechat.message_attachments WHERE media_id=? UNION ALL SELECT 1 FROM cinechat.conversation_avatars WHERE media_id=?)",Boolean.class,mediaId,mediaId));
    }

    private FeedPage relationPosts(int viewerId,String cursor,int size,String relation,String relationWhere,String join,Object... params){
        if(relation.equals("saved_posts")){
            int actualSize=Math.max(1,Math.min(MAX_PAGE,size));Cursor before=parseCursor(cursor);String sql="SELECT p.id,p.author_id,p.content,p.visibility,p.spoiler_flag,p.post_type,p.quoted_post_id,p.movie_id,p.created_at,p.updated_at,a.username,a.display_name,a.bio,a.avatar_reference,(SELECT count(*) FROM post_reactions r WHERE r.post_id=p.id) likes,(SELECT count(*) FROM post_comments c WHERE c.post_id=p.id AND c.deleted_at IS NULL) comments,(SELECT count(*) FROM post_reposts r WHERE r.post_id=p.id) reposts,EXISTS(SELECT 1 FROM post_reactions r WHERE r.post_id=p.id AND r.user_id=?) liked,true saved,EXISTS(SELECT 1 FROM post_reposts r WHERE r.post_id=p.id AND r.user_id=?) reposted,(SELECT storage_reference FROM post_media pm WHERE pm.post_id=p.id ORDER BY pm.position LIMIT 1) media,0.0 feed_score FROM saved_posts s JOIN cine_posts p ON p.id=s.post_id JOIN social_accounts a ON a.id=p.author_id WHERE s.user_id=? AND p.deleted_at IS NULL AND "+visibilitySql(viewerId,"p")+" AND "+noBlockSql(viewerId,"p.author_id")+(before==null?"":" AND (p.created_at,p.id)<(?::timestamptz,?::uuid)")+" ORDER BY p.created_at DESC,p.id DESC LIMIT ?";
            List<Object> args=new ArrayList<>(List.of(viewerId,viewerId,viewerId));if(before!=null){args.add(before.createdAt.toString());args.add(before.id);}args.add(actualSize+1);List<Map<String,Object>> rows=jdbc.queryForList(sql,args.toArray());boolean more=rows.size()>actualSize;if(more)rows=new ArrayList<>(rows.subList(0,actualSize));List<PostDto> items=rows.stream().map(r->post(r,viewerId)).toList();return new FeedPage(items,more&&!rows.isEmpty()?cursorFor(rows.get(rows.size()-1)):null,more);
        }
        return feed(viewerId,"latest",cursor,size);
    }

    private Optional<PostDto> postById(int viewerId,UUID id){
        String sql="SELECT p.id,p.author_id,p.content,p.visibility,p.spoiler_flag,p.post_type,p.quoted_post_id,p.movie_id,p.created_at,p.updated_at,a.username,a.display_name,a.bio,a.avatar_reference,(SELECT count(*) FROM post_reactions r WHERE r.post_id=p.id) likes,(SELECT count(*) FROM post_comments c WHERE c.post_id=p.id AND c.deleted_at IS NULL) comments,(SELECT count(*) FROM post_reposts r WHERE r.post_id=p.id) reposts,EXISTS(SELECT 1 FROM post_reactions r WHERE r.post_id=p.id AND r.user_id=?) liked,EXISTS(SELECT 1 FROM saved_posts s WHERE s.post_id=p.id AND s.user_id=?) saved,EXISTS(SELECT 1 FROM post_reposts r WHERE r.post_id=p.id AND r.user_id=?) reposted,(SELECT storage_reference FROM post_media pm WHERE pm.post_id=p.id ORDER BY pm.position LIMIT 1) media,0.0 feed_score FROM cine_posts p JOIN social_accounts a ON a.id=p.author_id WHERE p.id=? AND p.deleted_at IS NULL AND "+visibilitySql(viewerId,"p")+" AND "+noBlockSql(viewerId,"p.author_id");
        List<Map<String,Object>> rows=jdbc.queryForList(sql,viewerId,viewerId,viewerId,id);return rows.isEmpty()?Optional.empty():Optional.of(post(rows.get(0),viewerId));
    }

    private PostDto post(Map<String,Object> r,int viewerId){
        UUID id=(UUID)r.get("id");Object movieRaw=r.get("movie_id");Integer movieId=movieRaw==null?null:((Number)movieRaw).intValue();MovieDto movie=movieId==null?null:movieById(movieId);
        String type=((String)r.get("post_type")).toLowerCase(Locale.ROOT);String rawVisibility=(String)r.get("visibility");String visibility=rawVisibility.equals("FOLLOWERS_ONLY")?"followers":rawVisibility.equals("PRIVATE")?"private":"public";
        UUID pollId=jdbc.query("SELECT id FROM polls WHERE post_id=?",rs->rs.next()?UUID.fromString(rs.getString(1)):null,id);
        PollDto poll=pollId==null?null:pollDto(pollId,viewerId,null);
        UUID quote=(UUID)r.get("quoted_post_id");String originalAuthor=null,originalText=null;boolean unavailable=false;
        if(quote!=null){List<Map<String,Object>> q=jdbc.queryForList("SELECT a.display_name,a.username,p.content FROM cine_posts p JOIN social_accounts a ON a.id=p.author_id WHERE p.id=? AND p.deleted_at IS NULL AND "+visibilitySql(viewerId,"p")+" AND "+noBlockSql(viewerId,"p.author_id"),quote);if(q.isEmpty())unavailable=true;else{originalAuthor=publicName((String)q.get(0).get("display_name"));originalText=(String)q.get(0).get("content");}}
        String media=(String)r.get("media");String mediaUrl=null;if(media!=null){String raw=media;int dot=raw.lastIndexOf('.');try{mediaUrl="/api/feed/media/"+UUID.fromString(dot<0?raw:raw.substring(0,dot));}catch(IllegalArgumentException ignored){}}
        String content=(String)r.get("content");java.sql.Timestamp created=(java.sql.Timestamp)r.get("created_at");java.sql.Timestamp updated=(java.sql.Timestamp)r.get("updated_at");return new PostDto(id.toString(),String.valueOf(r.get("author_id")),publicName((String)r.get("display_name")),publicHandle((String)r.get("username")),initials(publicName((String)r.get("display_name"))),tone(publicHandle((String)r.get("username"))),"",created.toInstant(),updated.toInstant(),movie==null?"":movie.title(),movieId==null?"":movieId.toString(),content,type,visibility,Boolean.TRUE.equals(r.get("spoiler_flag")),topics(id),mediaUrl,((Number)r.get("likes")).intValue(),((Number)r.get("comments")).intValue(),((Number)r.get("reposts")).intValue(),Boolean.TRUE.equals(r.get("liked")),Boolean.TRUE.equals(r.get("saved")),Boolean.TRUE.equals(r.get("reposted")),poll,quote==null?null:quote.toString(),originalAuthor,originalText,unavailable,r.get("avatar_reference")==null?null:"/api/feed/users/"+r.get("author_id")+"/avatar");
    }

    private UserDto userFromRow(Map<String,Object> r,int viewerId){int id=((Number)r.get("id")).intValue();boolean follows=Boolean.TRUE.equals(r.get("followed_by_me"));boolean followsMe=Boolean.TRUE.equals(r.get("follows_me"));String name=publicName((String)r.get("display_name"));String handle=publicHandle((String)r.get("username"));String ref=(String)r.get("avatar_reference");String avatar=null;if(ref!=null&&!ref.isBlank())avatar="/api/feed/users/"+id+"/avatar";Object joinedRaw=r.get("created_at");Instant joined=joinedRaw instanceof java.sql.Timestamp t?t.toInstant():joinedRaw instanceof Instant i?i:null;return new UserDto(String.valueOf(id),name,handle,initials(name),tone(handle),Objects.toString(r.get("bio"),""),false,"",number(r,"followers"),number(r,"following"),follows,followsMe,follows&&followsMe,avatar,joined);}
    private CommentDto comment(Map<String,Object> r){boolean deleted=r.get("deleted_at")!=null;String name=deleted?"Deleted member":publicName((String)r.get("display_name"));String handle=deleted?"deleted":publicHandle((String)r.get("username"));return new CommentDto(r.get("id").toString(),r.get("post_id").toString(),r.get("parent_comment_id")==null?null:r.get("parent_comment_id").toString(),deleted?"":String.valueOf(r.get("author_id")),name,handle,initials(name),tone(handle),(String)r.get("content"),((java.sql.Timestamp)r.get("created_at")).toInstant(),!deleted&&!Objects.equals(r.get("created_at"),r.get("updated_at")),deleted||r.get("avatar_reference")==null?null:"/api/feed/users/"+r.get("author_id")+"/avatar");}
    private CommentDto commentById(int viewerId,UUID id){List<Map<String,Object>> r=jdbc.queryForList("SELECT c.id,c.post_id,c.parent_comment_id,c.author_id,c.content,c.created_at,c.updated_at,a.username,a.display_name,a.avatar_reference FROM post_comments c JOIN social_accounts a ON a.id=c.author_id WHERE c.id=? AND c.deleted_at IS NULL AND "+noBlockSql(viewerId,"c.author_id"),id);if(r.isEmpty())throw notFound("Comment not found.");return comment(r.get(0));}
    private void requireReadableComment(int viewerId,UUID commentId){List<Map<String,Object>> rows=jdbc.queryForList("SELECT post_id FROM post_comments WHERE id=? AND author_id=? AND deleted_at IS NULL",commentId,viewerId);if(rows.isEmpty())throw notFound("Comment not found.");requireReadablePost(viewerId,(UUID)rows.get(0).get("post_id"));}
    private PollDto pollDto(UUID id,int userId,UUID voted){
        Map<String,Object> p=jdbc.queryForMap("SELECT question,expires_at,(expires_at IS NOT NULL AND expires_at<=now()) closed FROM polls WHERE id=?",id);
        List<Map<String,Object>> options=jdbc.queryForList("SELECT o.id,o.option_text,count(v.user_id) votes FROM poll_options o LEFT JOIN poll_votes v ON v.option_id=o.id WHERE o.poll_id=? GROUP BY o.id ORDER BY o.position",id);
        Map<String,Integer> counts=new LinkedHashMap<>();List<String> texts=new ArrayList<>();int total=0;String voteText=null;
        for(Map<String,Object> o:options){int n=((Number)o.get("votes")).intValue();String text=(String)o.get("option_text");texts.add(text);counts.put(text,n);total+=n;if(voted!=null&&voted.equals(o.get("id")))voteText=text;}
        if(voted==null){UUID current=jdbc.query("SELECT option_id FROM poll_votes WHERE poll_id=? AND user_id=?",rs->rs.next()?UUID.fromString(rs.getString(1)):null,id,userId);if(current!=null)for(Map<String,Object> o:options)if(current.equals(o.get("id")))voteText=(String)o.get("option_text");}
        Object expires=p.get("expires_at");Instant closesAt=expires instanceof Instant i?i:expires instanceof java.time.OffsetDateTime o?o.toInstant():expires instanceof java.sql.Timestamp t?t.toInstant():null;
        return new PollDto(id.toString(),(String)p.get("question"),texts,counts,total,voteText,closesAt,Boolean.TRUE.equals(p.get("closed")));
    }

    private String scoreSql(String type,int userId){
        String engagement="(LEAST(20, (SELECT count(*) FROM post_reactions er WHERE er.post_id=p.id) + 2*(SELECT count(*) FROM post_comments ec WHERE ec.post_id=p.id AND ec.deleted_at IS NULL) + 2*(SELECT count(*) FROM post_reposts ep WHERE ep.post_id=p.id))::numeric / (1 + GREATEST(0,extract(epoch from (now()-p.created_at))/3600)/8))";
        if(type.equals("latest")||type.equals("following"))return "0.0";
        if(type.equals("trending"))return engagement;
        PreferencesDto prefs=preferences(userId);Set<String> genres=new HashSet<>(prefs.genres().stream().map(x->x.toLowerCase(Locale.ROOT)).toList());Set<String> languages=new HashSet<>(prefs.languages().stream().map(x->x.toLowerCase(Locale.ROOT)).toList());
        List<String> preferredIds=movieCatalogue().stream().filter(m->m.id().matches("\\d+")).filter(m->(!genres.isEmpty()&&m.genres().stream().anyMatch(g->genres.contains(g.toLowerCase(Locale.ROOT))))||(!languages.isEmpty()&&languages.contains(Objects.requireNonNullElse(m.language(),"").toLowerCase(Locale.ROOT)))).map(MovieDto::id).toList();
        String preferenceBoost=preferredIds.isEmpty()?"0": "CASE WHEN p.movie_id IN ("+String.join(",",preferredIds)+") THEN 4 ELSE 0 END";
        return "("+engagement+" + CASE WHEN EXISTS(SELECT 1 FROM user_follows f WHERE f.follower_id="+userId+" AND f.followed_id=p.author_id) THEN 8 ELSE 0 END + CASE WHEN p.movie_id IN (SELECT liked.movie_id FROM cine_posts liked JOIN post_reactions lr ON lr.post_id=liked.id WHERE lr.user_id="+userId+" AND liked.movie_id IS NOT NULL ORDER BY liked.created_at DESC LIMIT 20) THEN 3 ELSE 0 END + "+preferenceBoost+")";
    }
    private String visibilitySql(int viewerId,String alias){return "("+alias+".author_id="+viewerId+" OR "+alias+".visibility='PUBLIC' OR ("+alias+".visibility='FOLLOWERS_ONLY' AND EXISTS(SELECT 1 FROM user_follows vf WHERE vf.follower_id="+viewerId+" AND vf.followed_id="+alias+".author_id)))";}
    private String noBlockSql(int viewerId,String authorColumn){return "NOT EXISTS(SELECT 1 FROM user_blocks bl WHERE (bl.blocker_id="+viewerId+" AND bl.blocked_id="+authorColumn+") OR (bl.blocker_id="+authorColumn+" AND bl.blocked_id="+viewerId+")) ";}

    private void syncAccounts(){
        List<AuthUserJson> users=json.getData("data/auth-users.json",AuthUserJson.class);
        jdbc.batchUpdate("INSERT INTO social_accounts(id,username,display_name) VALUES (?,?,?) ON CONFLICT(id) DO UPDATE SET username=CASE WHEN social_accounts.username ~ '^user[0-9]+$' THEN EXCLUDED.username ELSE social_accounts.username END,display_name=CASE WHEN social_accounts.display_name IS NULL OR btrim(social_accounts.display_name)='' THEN EXCLUDED.display_name ELSE social_accounts.display_name END,updated_at=now()",users,100,(ps,u)->{ps.setInt(1,u.id);ps.setString(2,normalizeHandle(u.username,u.id));ps.setString(3,Objects.requireNonNullElse(u.name,"CineBook member"));});
    }
    private void requireReadablePost(int viewer,UUID id){if(postById(viewer,id).isEmpty())throw notFound("CineTake not found.");}
    private void requirePostOwner(int user,UUID id){Integer owner=jdbc.query("SELECT author_id FROM cine_posts WHERE id=? AND deleted_at IS NULL",rs->rs.next()?rs.getInt(1):null,id);if(owner==null||owner!=user)throw notFound("CineTake not found.");}
    private boolean isBlockedEitherWay(int a,int b){return Boolean.TRUE.equals(jdbc.query("SELECT EXISTS(SELECT 1 FROM user_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?))",rs->rs.next()&&rs.getBoolean(1),a,b,b,a));}
    private void requireAccount(int id){Integer count=jdbc.queryForObject("SELECT count(*) FROM social_accounts WHERE id=?",Integer.class,id);if(count==null||count==0)throw notFound("Account not found.");}
    private Integer currentMovie(UUID id){return jdbc.query("SELECT movie_id FROM cine_posts WHERE id=?",rs->rs.next()?(Integer)rs.getObject(1):null,id);}
    private String currentVisibility(UUID id){return jdbc.queryForObject("SELECT visibility FROM cine_posts WHERE id=?",String.class,id);}
    private String currentContent(UUID id){return jdbc.queryForObject("SELECT content FROM cine_posts WHERE id=?",String.class,id);}
    private void attachMedia(int user,UUID post,List<String> ids){int position=0;for(String raw:ids){UUID id=parseOptionalUuid(raw,"Media");int n=jdbc.update("UPDATE post_media SET post_id=?,position=? WHERE id=? AND owner_id=? AND post_id IS NULL",post,position++,id,user);if(n==0)throw badRequest("An image is unavailable or belongs to another account.");}}
    private void createPoll(UUID post,PollRequest request){UUID id=UUID.randomUUID();Instant expiry=request.closesAt();if(expiry!=null&&!expiry.isAfter(Instant.now()))throw badRequest("Poll closing time must be in the future.");jdbc.update("INSERT INTO polls(id,post_id,question,expires_at) VALUES (?,?,?,?)",id,post,request.question().trim(),expiry==null?null:java.sql.Timestamp.from(expiry));int i=0;for(String option:request.options()){String value=option.trim();if(value.isEmpty())throw badRequest("Poll choices cannot be empty.");jdbc.update("INSERT INTO poll_options(id,poll_id,option_text,position) VALUES (?,?,?,?)",UUID.randomUUID(),id,value,i++);}}
    private void validatePoll(PollRequest p){if(p.question()==null||p.question().trim().length()<4||p.question().length()>120)throw badRequest("Poll questions must be 4 to 120 characters.");if(p.options()==null||p.options().size()<2||p.options().size()>4)throw badRequest("Polls need two to four choices.");Set<String> distinct=new HashSet<>();for(String s:p.options())if(s==null||s.isBlank()||s.length()>60||!distinct.add(s.trim().toLowerCase(Locale.ROOT)))throw badRequest("Poll choices must be unique and 1 to 60 characters.");}
    private void validateContent(String content,PollRequest poll,List<String> media){if(content.length()>500)throw badRequest("CineTakes are limited to 500 characters.");if(content.isBlank()&&poll==null&&media.isEmpty())throw badRequest("Add text, an image, or a poll to continue.");}
    private Integer validateMovie(Integer movieId){if(movieId==null)return null;if(movieCatalogue().stream().noneMatch(m->m.id().equals(String.valueOf(movieId))))throw badRequest("Choose a movie from the CineBook catalogue.");return movieId;}
    private List<MovieDto> movieCatalogue(){List<MovieDto> cached=movieCache;if(cached!=null)return cached;synchronized(this){if(movieCache==null)movieCache=json.getData("data/cinebook_movies.json",MovieJson.class).stream().map(m->new MovieDto(String.valueOf(m.id),m.title,year(m.releaseDate),m.genres==null?List.of():m.genres,m.language,poster(m.poster))).toList();return movieCache;}}
    private MovieDto movieById(int id){return movieCatalogue().stream().filter(m->m.id().equals(String.valueOf(id))).findFirst().orElse(null);}
    private void saveTopics(UUID post,String content){Matcher matcher=TOPIC.matcher(content);Set<String> names=new LinkedHashSet<>();while(matcher.find())names.add(matcher.group(1).toLowerCase(Locale.ROOT));for(String name:names){jdbc.update("INSERT INTO topics(normalized_name,display_name) VALUES (?,?) ON CONFLICT(normalized_name) DO NOTHING",name,matcherDisplay(content,name));jdbc.update("INSERT INTO post_topics(post_id,topic_id) SELECT ?,id FROM topics WHERE normalized_name=? ON CONFLICT DO NOTHING",post,name);}}
    private List<String> topics(UUID post){return jdbc.query("SELECT t.normalized_name FROM topics t JOIN post_topics pt ON pt.topic_id=t.id WHERE pt.post_id=? ORDER BY t.normalized_name",(rs,n)->rs.getString(1),post);}
    private String normalizeFeedType(String s){String v=s==null?"for-you":s.toLowerCase(Locale.ROOT).replace('_','-');if(!Set.of("for-you","latest","following","trending").contains(v))throw badRequest("Choose For You, Latest, Following, or Trending.");return v;}
    private String normalizeVisibility(String v){if(v==null||v.equalsIgnoreCase("public"))return "PUBLIC";if(v.equalsIgnoreCase("followers")||v.equalsIgnoreCase("followers_only"))return "FOLLOWERS_ONLY";if(v.equalsIgnoreCase("private"))return "PRIVATE";throw badRequest("Unsupported post visibility.");}
    private String normalizePostType(String v,PollRequest poll,List<String> media){if(v!=null){String normalized=v.toUpperCase(Locale.ROOT);if(Set.of("DISCUSSION","REVIEW","POLL","IMAGE").contains(normalized))return normalized;}if(poll!=null)return "POLL";if(media!=null&&!media.isEmpty())return "IMAGE";return "DISCUSSION";}
    private String detectImage(byte[] bytes,String contentType){if(bytes.length<12)throw badRequest("The uploaded file is not a supported image.");String ext;if("image/png".equalsIgnoreCase(contentType))ext="png";else if("image/jpeg".equalsIgnoreCase(contentType))ext="jpg";else if("image/gif".equalsIgnoreCase(contentType))ext="gif";else throw badRequest("Only PNG, JPEG, and GIF images are supported.");try{if(ImageIO.read(new java.io.ByteArrayInputStream(bytes))==null)throw badRequest("The uploaded file is not a valid image.");}catch(IOException e){throw badRequest("The uploaded file is not a valid image.");}return ext;}
    private String cursorFor(Map<String,Object> row){return Base64.getUrlEncoder().withoutPadding().encodeToString((row.get("feed_score")+"|"+((java.sql.Timestamp)row.get("created_at")).toInstant()+"|"+row.get("id")).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private Cursor parseCursor(String raw){if(raw==null||raw.isBlank())return null;try{String[] p=new String(Base64.getUrlDecoder().decode(raw),java.nio.charset.StandardCharsets.UTF_8).split("\\|",3);return new Cursor(Double.parseDouble(p[0]),Instant.parse(p[1]),UUID.fromString(p[2]));}catch(Exception e){throw badRequest("Invalid pagination cursor.");}}
    private CommentCursor parseCommentCursor(String raw){if(raw==null||raw.isBlank())return null;try{String[] p=new String(Base64.getUrlDecoder().decode(raw),java.nio.charset.StandardCharsets.UTF_8).split("\\|",2);return new CommentCursor(Instant.parse(p[0]),UUID.fromString(p[1]));}catch(Exception e){throw badRequest("Invalid pagination cursor.");}}
    private UUIDKeyset parseUserCursor(String raw){if(raw==null||raw.isBlank())return null;try{String[] p=new String(Base64.getUrlDecoder().decode(raw),java.nio.charset.StandardCharsets.UTF_8).split("\\|",2);return new UUIDKeyset(p[0],Integer.parseInt(p[1]));}catch(Exception e){throw badRequest("Invalid pagination cursor.");}}
    private record Cursor(double score,Instant createdAt,UUID id){}
    private record CommentCursor(Instant createdAt,UUID id){}
    private record UUIDKeyset(String username,int id){}
    private String escapeLike(String v){return v.replace("\\","\\\\").replace("%","\\%").replace("_","\\_");}
    private String initials(String name){return Arrays.stream(Objects.requireNonNullElse(name,"CineBook").trim().split("\\s+")).filter(s->!s.isBlank()).limit(2).map(s->s.substring(0,1).toUpperCase(Locale.ROOT)).collect(Collectors.joining());}
    private String tone(String handle){return switch(Math.floorMod(Objects.requireNonNullElse(handle,"").hashCode(),4)){case 0->"blue";case 1->"rose";case 2->"gold";default->"violet";};}
    private int number(Map<String,Object> m,String k){Object v=m.get(k);return v instanceof Number n?n.intValue():0;}
    private String publicName(String raw){String value=Objects.toString(raw,"").trim();return value.isBlank()||value.matches("(?i)user\\s*#?\\d+")?"CineBook member":value;}
    private String publicHandle(String raw){String value=Objects.toString(raw,"").trim();return value.isBlank()||value.matches("(?i)user\\d+")?"cinemember":value;}
    private String normalizeHandle(String name,int id){String value=Objects.requireNonNullElse(name,"").trim();if(!value.matches("[A-Za-z0-9_.-]{3,30}")||value.matches("(?i)user\\d+"))value="cine-member-"+UUID.randomUUID().toString().substring(0,8);return value;}
    private String matcherDisplay(String content,String normalized){Matcher m=TOPIC.matcher(content);while(m.find())if(m.group(1).equalsIgnoreCase(normalized))return m.group(1);return normalized;}
    private Integer year(String date){try{return date==null?null:Integer.valueOf(date.substring(0,4));}catch(Exception e){return null;}}
    private String poster(String raw){return raw==null?null:raw.startsWith("http")?raw:"/images"+(raw.startsWith("/")?raw:"/"+raw);}
    private List<String> boundedValues(List<String> values,int max,int maxLen){return values==null?List.of():values.stream().filter(Objects::nonNull).map(String::trim).filter(s->!s.isEmpty()&&s.length()<=maxLen).distinct().limit(max).toList();}
    private UUID parseOptionalUuid(String value,String label){if(value==null||value.isBlank())return null;try{return UUID.fromString(value);}catch(IllegalArgumentException ex){throw badRequest(label+" reference is invalid.");}}
    private ResponseStatusException badRequest(String s){return new ResponseStatusException(HttpStatus.BAD_REQUEST,s);}
    private ResponseStatusException notFound(String s){return new ResponseStatusException(HttpStatus.NOT_FOUND,s);}
}
