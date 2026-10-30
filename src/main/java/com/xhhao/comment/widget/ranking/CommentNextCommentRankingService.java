package com.xhhao.comment.widget.ranking;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.avatar.CommentNextWeAvatarUrl;
import com.xhhao.comment.widget.badge.CommentNextBadgeIdentity;
import com.xhhao.comment.widget.badge.CommentNextBadgeProfileService;
import com.xhhao.comment.widget.comment.CommentNextCounterService;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

/** Aggregates approved comments and replies into a public reader ranking. */
@Service
@RequiredArgsConstructor
public class CommentNextCommentRankingService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;
    private static final int MAX_EXCERPT_LENGTH = 96;

    private final ReactiveExtensionClient client;
    private final CommentNextCounterService counterService;
    private final SettingConfigGetter settingConfigGetter;
    private final CommentNextBadgeProfileService badgeProfileService;

    private final Map<CommentNextCommentRankingPeriod, Mono<List<RankedReader>>>
        rankingCache = new ConcurrentHashMap<>();

    public Mono<CommentNextCommentRankingResponse> list(String periodValue, String limitValue) {
        var period = CommentNextCommentRankingPeriod.from(periodValue);
        var limit = parseLimit(limitValue);
        return Mono.zip(
                rankingCache.computeIfAbsent(period, this::cachedRanking),
                settingConfigGetter.getRankingConfig()
            )
            .flatMapMany(tuple -> Flux.fromIterable(tuple.getT1())
                .filter(reader -> !tuple.getT2().excludes(reader.identity(), reader.memberEmail())))
            .take(limit)
            .flatMapSequential(reader -> badgeProfileService.fetchSnapshot(reader.identity())
                .map(snapshot -> reader.item().withLevelBadge(snapshot.badges().stream()
                    .filter(badge -> badge != null && "level".equals(badge.tone()))
                    .findFirst().orElse(null))), 8)
            .collectList()
            .map(items -> new CommentNextCommentRankingResponse(period.value(), limit, items));
    }

    private Mono<List<RankedReader>> cachedRanking(
        CommentNextCommentRankingPeriod period
    ) {
        // One public request must not scan every comment for every visitor. Recalculate
        // the window at each refresh so a cache spanning midnight stays correct.
        return Mono.defer(() -> Mono.zip(
                userMap(),
                activeOwners(period.start(ZoneId.systemDefault()))
            )
            .flatMap(tuple -> rankingWithUpvotes(tuple.getT1(), tuple.getT2())))
            .cache(Duration.ofMinutes(1));
    }

    private Mono<Map<String, User>> userMap() {
        return client.listAll(User.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(user -> user.getMetadata() != null && StringUtils.hasText(user.getMetadata().getName()))
            .collectMap(user -> user.getMetadata().getName(), user -> user);
    }

    private Mono<List<RankingOwner>> activeOwners(Instant start) {
        return client.listAll(Comment.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(comment -> isPublic(comment.getSpec()))
            .collectList()
            .flatMap(comments -> {
                var visibleCommentNames = comments.stream()
                    .map(comment -> comment.getMetadata().getName())
                    .collect(Collectors.toSet());
                var commentOwners = Flux.fromIterable(comments)
                    .flatMap(comment -> ownerOf(comment, comment.getSpec(), start,
                        CommentNextCounterService.COMMENTS_PLURAL));
                var replyOwners = visibleReplyOwners(visibleCommentNames, start);
                return Flux.concat(commentOwners, replyOwners).collectList();
            });
    }

    private Flux<RankingOwner> visibleReplyOwners(Set<String> visibleCommentNames, Instant start) {
        return client.listAll(Reply.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(reply -> reply.getSpec() != null
                && visibleCommentNames.contains(reply.getSpec().getCommentName()))
            .flatMap(reply -> ownerOf(reply, reply.getSpec(), start,
                CommentNextCounterService.REPLIES_PLURAL));
    }

    private Mono<RankingOwner> ownerOf(
        run.halo.app.extension.Extension extension,
        Comment.BaseCommentSpec spec,
        Instant start,
        String plural
    ) {
        if (!isPublic(spec) || spec.getOwner() == null) {
            return Mono.empty();
        }

        var owner = spec.getOwner();
        var identity = ownerIdentity(owner);
        if (identity == null) {
            return Mono.empty();
        }

        var creationTime = creationTime(extension, spec);
        if (creationTime.isBefore(start)) {
            return Mono.empty();
        }

        return Mono.just(new RankingOwner(
            identity,
            owner,
            commentExcerpt(spec),
            creationTime,
            plural,
            extension.getMetadata().getName()
        ));
    }

    private boolean isPublic(Comment.BaseCommentSpec spec) {
        return spec != null
            && Boolean.TRUE.equals(spec.getApproved())
            && !Boolean.TRUE.equals(spec.getHidden());
    }

    private Mono<List<RankedReader>> rankingWithUpvotes(
        Map<String, User> users,
        List<RankingOwner> owners
    ) {
        var commentNames = owners.stream()
            .filter(owner -> CommentNextCounterService.COMMENTS_PLURAL.equals(owner.plural()))
            .map(RankingOwner::resourceName)
            .toList();
        var replyNames = owners.stream()
            .filter(owner -> CommentNextCounterService.REPLIES_PLURAL.equals(owner.plural()))
            .map(RankingOwner::resourceName)
            .toList();
        return Mono.zip(
            counterService.fetchUpvotes(CommentNextCounterService.COMMENTS_PLURAL, commentNames),
            counterService.fetchUpvotes(CommentNextCounterService.REPLIES_PLURAL, replyNames)
        ).map(tuple -> rank(users, owners, tuple.getT1(), tuple.getT2()));
    }

    private List<RankedReader> rank(
        Map<String, User> users,
        List<RankingOwner> owners,
        Map<String, Integer> commentUpvotes,
        Map<String, Integer> replyUpvotes
    ) {
        var counts = new HashMap<String, RankingAccumulator>();
        owners.forEach(entry -> {
            var accumulator = counts.computeIfAbsent(entry.identity(), ignored ->
                new RankingAccumulator(entry.owner(), users.get(entry.userName())));
            var upvotes = CommentNextCounterService.COMMENTS_PLURAL.equals(entry.plural())
                ? commentUpvotes : replyUpvotes;
            accumulator.accept(entry, upvotes.getOrDefault(entry.resourceName(), 0));
        });

        return counts.values().stream()
            .map(RankingAccumulator::toReader)
            .sorted(Comparator.comparingLong((RankedReader reader) -> reader.item().commentCount())
                .reversed()
                .thenComparing(reader -> reader.item().displayName(),
                    String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    private String ownerIdentity(Comment.CommentOwner owner) {
        if (owner == null || !StringUtils.hasText(owner.getName())) {
            return null;
        }

        if (User.KIND.equals(owner.getKind())) {
            return "user:" + owner.getName().trim();
        }
        if (Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())) {
            return "email:" + owner.getName().trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private Instant creationTime(
        run.halo.app.extension.Extension extension,
        Comment.BaseCommentSpec spec
    ) {
        if (spec.getCreationTime() != null) {
            return spec.getCreationTime();
        }
        if (extension.getMetadata() != null && extension.getMetadata().getCreationTimestamp() != null) {
            return extension.getMetadata().getCreationTimestamp();
        }
        return Instant.EPOCH;
    }

    private int parseLimit(String value) {
        try {
            var parsed = Integer.parseInt(value == null ? "" : value.trim());
            return Math.min(Math.max(parsed, 1), MAX_LIMIT);
        } catch (NumberFormatException ignored) {
            return DEFAULT_LIMIT;
        }
    }

    private String commentExcerpt(Comment.BaseCommentSpec spec) {
        var source = StringUtils.hasText(spec.getContent()) ? spec.getContent() : spec.getRaw();
        if (!StringUtils.hasText(source)) {
            return "";
        }

        var text = Jsoup.parse(source).text().replaceAll("\\s+", " ").trim();
        if (text.length() <= MAX_EXCERPT_LENGTH) {
            return text;
        }
        return text.substring(0, MAX_EXCERPT_LENGTH).stripTrailing() + "…";
    }

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }

    private record RankingOwner(
        String identity,
        Comment.CommentOwner owner,
        String latestComment,
        Instant createdAt,
        String plural,
        String resourceName
    ) {
        String userName() {
            return User.KIND.equals(owner.getKind()) ? owner.getName() : null;
        }
    }

    // Identity and email stay inside the cache and never enter the public response.
    private record RankedReader(
        CommentNextBadgeIdentity identity,
        String memberEmail,
        CommentNextCommentRankingItem item
    ) {
    }

    private static final class RankingAccumulator {
        private Comment.CommentOwner owner;
        private final User user;
        private long count;
        private long upvoteCount;
        private String latestComment = "";
        private Instant latestCommentAt = Instant.EPOCH;

        private RankingAccumulator(Comment.CommentOwner owner, User user) {
            this.owner = owner;
            this.user = user;
        }

        private void accept(RankingOwner entry, int upvotes) {
            count++;
            upvoteCount += Math.max(0, upvotes);
            if (!entry.createdAt().isBefore(latestCommentAt)) {
                owner = entry.owner();
                latestComment = entry.latestComment();
                latestCommentAt = entry.createdAt();
            }
        }

        private RankedReader toReader() {
            return new RankedReader(
                CommentNextBadgeIdentity.fromOwner(owner).orElseThrow(),
                user != null && user.getSpec() != null ? user.getSpec().getEmail() : null,
                toItem()
            );
        }

        private CommentNextCommentRankingItem toItem() {
            if (user != null && user.getSpec() != null) {
                var displayName = firstText(
                    user.getSpec().getDisplayName(),
                    owner.getDisplayName(),
                    user.getMetadata().getName()
                );
                return new CommentNextCommentRankingItem(
                    displayName,
                    firstText(
                        user.getSpec().getAvatar(),
                        owner.getAnnotation(Comment.CommentOwner.AVATAR_ANNO),
                        CommentNextWeAvatarUrl.defaultAvatar()
                    ),
                    User.KIND,
                    count,
                    upvoteCount,
                    latestComment,
                    latestCommentAt,
                    null
                );
            }

            if (User.KIND.equals(owner.getKind())) {
                return new CommentNextCommentRankingItem(
                    firstText(owner.getDisplayName(), owner.getName()),
                    firstText(owner.getAnnotation(Comment.CommentOwner.AVATAR_ANNO),
                        CommentNextWeAvatarUrl.defaultAvatar()),
                    User.KIND,
                    count,
                    upvoteCount,
                    latestComment,
                    latestCommentAt,
                    null
                );
            }

            var email = owner.getName() == null ? "" : owner.getName().trim();
            var avatar = firstText(
                owner.getAnnotation(Comment.CommentOwner.AVATAR_ANNO),
                CommentNextWeAvatarUrl.forEmail(email)
            );
            return new CommentNextCommentRankingItem(
                firstText(owner.getDisplayName(), "匿名用户"),
                avatar,
                Comment.CommentOwner.KIND_EMAIL,
                count,
                upvoteCount,
                latestComment,
                latestCommentAt,
                null
            );
        }

        private static String firstText(String... values) {
            for (var value : values) {
                if (StringUtils.hasText(value)) {
                    return value.trim();
                }
            }
            return "匿名用户";
        }
    }
}
