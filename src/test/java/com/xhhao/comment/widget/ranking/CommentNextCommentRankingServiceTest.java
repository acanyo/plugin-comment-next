package com.xhhao.comment.widget.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.comment.CommentNextCounterService;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.badge.CommentNextBadgeIdentity;
import com.xhhao.comment.widget.badge.CommentNextBadgeProfile;
import com.xhhao.comment.widget.badge.CommentNextBadgeProfileService;
import com.xhhao.comment.widget.comment.CommentNextBadge;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.Counter;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class CommentNextCommentRankingServiceTest {

    private static final String READER_EMAIL_HASH =
        "d108b279434fe1d54ac0f1da633564604b26c2e0e221d108b0fbadb87aba02c0";

    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);

    @org.junit.jupiter.api.BeforeEach
    void defaultSettings() {
        when(settings.getRankingConfig()).thenReturn(Mono.just(new CommentNextRankingConfig()));
    }

    @Test
    void groupsAnonymousEmailWithoutExposingItAndIgnoresPrivateContent() {
        var client = rankingClient();
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                comment("public-1", " Reader@Example.com ", true, false),
                comment("public-2", "reader@example.com", true, false),
                comment("unapproved", "reader@example.com", false, false),
                comment("hidden-parent", "other@example.com", true, true)
            ));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                reply("reply-1", "public-1", "READER@example.com", true, false),
                reply("reply-hidden", "hidden-parent", "other@example.com", true, false),
                reply("hidden-reply", "public-1", "reader@example.com", true, true),
                reply("reply-pending", "public-1", "reader@example.com", false, false)
            ));

        var result = rankingService(client).list("all", "10").block();

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().displayName()).isEqualTo("读者");
        assertThat(result.items().getFirst().commentCount()).isEqualTo(3);
        assertThat(result.items().getFirst().repliesCount()).isEqualTo(1);
        assertThat(result.items().getFirst().emailHash()).isEqualTo(READER_EMAIL_HASH);
        assertThat(result.items().getFirst().website()).isNull();
        assertThat(result.items().getFirst().avatar()).contains("weavatar.com/avatar/");
        assertThat(result.items().getFirst().latestComment()).isEqualTo("reply-1 content");
        assertThat(result.items().getFirst().latestCommentAt())
            .isEqualTo(Instant.parse("2026-09-13T00:00:00Z"));
        assertThat(result.toString()).doesNotContain("reader@example.com");
        var json = JsonUtils.createObjectMapper().valueToTree(result);
        assertThat(json.path("items").get(0).path("emailHash").asText())
            .isEqualTo(READER_EMAIL_HASH);
        assertThat(json.toString()).doesNotContain("Reader@Example.com", "READER@example.com",
            "reader@example.com", "memberEmail", "identity");
    }

    @Test
    void usesWebsiteFromLatestPublicActivityWithoutFallingBackToOlderWebsites() {
        var client = rankingClient();
        var comment = comment("public", "reader@example.com", true, false);
        comment.getSpec().getOwner().setAnnotations(Map.of(Comment.CommentOwner.WEBSITE_ANNO,
            "https://older.example.com"));
        var reply = reply("public-reply", "public", " READER@example.com ", true, false);
        reply.getSpec().getOwner().setAnnotations(Map.of(Comment.CommentOwner.WEBSITE_ANNO,
            " https://reader.example.com "));
        var hidden = reply("hidden", "public", "reader@example.com", true, true);
        hidden.getSpec().setCreationTime(Instant.parse("2026-09-14T00:00:00Z"));
        hidden.getSpec().getOwner().setAnnotations(Map.of(Comment.CommentOwner.WEBSITE_ANNO,
            "https://private.example.com"));
        var pending = comment("pending", "reader@example.com", false, false);
        pending.getSpec().setCreationTime(Instant.parse("2026-09-15T00:00:00Z"));
        pending.getSpec().getOwner().setAnnotations(Map.of(Comment.CommentOwner.WEBSITE_ANNO,
            "https://pending.example.com"));
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(comment, pending));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(hidden, reply));

        var result = rankingService(client).list("all", "10").block();

        var item = result.items().getFirst();
        assertThat(item.website()).isEqualTo("https://reader.example.com");
        assertThat(item.emailHash()).isEqualTo(READER_EMAIL_HASH);
        assertThat(item.commentCount()).isEqualTo(2);
        assertThat(item.repliesCount()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("private.example.com", "pending.example.com");

        reply.getSpec().getOwner().setAnnotations(Map.of(Comment.CommentOwner.WEBSITE_ANNO, " "));
        assertThat(rankingService(client).list("all", "10").block().items().getFirst().website())
            .isNull();
    }

    @Test
    void preservesSeparateMemberAndAnonymousRowsAndSortingByTotalActivity() {
        var client = rankingClient();
        var member = new User();
        member.setMetadata(metadata("member"));
        member.getSpec().setDisplayName("A member");
        member.getSpec().setEmail(" Reader@Example.com ");
        var memberComment = comment("member-comment", "unused@example.com", true, false);
        memberComment.getSpec().getOwner().setKind(User.KIND);
        memberComment.getSpec().getOwner().setName("member");
        memberComment.getSpec().getOwner().setAnnotations(Map.of(Comment.CommentOwner.WEBSITE_ANNO,
            "https://member.example.com"));
        var anonymousComment = comment("anonymous-comment", "reader@example.com", true, false);
        anonymousComment.getSpec().getOwner().setDisplayName("Z anonymous");
        var anonymousReply = reply("anonymous-reply", "member-comment", "reader@example.com", true, false);
        anonymousReply.getSpec().getOwner().setDisplayName("Z anonymous");
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(member));
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(memberComment, anonymousComment));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(anonymousReply));

        var result = rankingService(client).list("all", "10").block();

        assertThat(result.items()).hasSize(2);
        var anonymousItem = result.items().getFirst();
        assertThat(anonymousItem.kind()).isEqualTo(Comment.CommentOwner.KIND_EMAIL);
        assertThat(anonymousItem.commentCount()).isEqualTo(2);
        assertThat(anonymousItem.repliesCount()).isEqualTo(1);
        var memberItem = result.items().get(1);
        assertThat(memberItem.kind()).isEqualTo(User.KIND);
        assertThat(memberItem.commentCount()).isEqualTo(1);
        assertThat(memberItem.repliesCount()).isZero();
        assertThat(memberItem.website()).isEqualTo("https://member.example.com");
        assertThat(result.items()).extracting(CommentNextCommentRankingItem::emailHash)
            .containsExactly(READER_EMAIL_HASH, READER_EMAIL_HASH);
        assertThat(result.toString()).doesNotContain("Reader@Example.com", "reader@example.com");
    }

    @Test
    void missingMemberProfileDoesNotHashTheUsernameAsAnEmail() {
        var client = rankingClient();
        var comment = comment("former-member", "unused@example.com", true, false);
        comment.getSpec().getOwner().setKind(User.KIND);
        comment.getSpec().getOwner().setName("deleted-user");
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(comment));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());

        var item = rankingService(client).list("all", "10").block().items().getFirst();

        assertThat(item.emailHash()).isNull();
        assertThat(item.website()).isNull();
        assertThat(item.commentCount()).isEqualTo(1);
        assertThat(item.repliesCount()).isZero();
    }

    @Test
    void countsRepliesInThePeriodEvenWhenTheirParentWasPublishedBeforeIt() {
        var client = rankingClient();
        var parent = comment("old-parent", "other@example.com", true, false);
        parent.getSpec().setCreationTime(Instant.EPOCH);
        var reply = reply("recent-reply", "old-parent", "reader@example.com", true, false);
        reply.getSpec().setCreationTime(Instant.now());
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(parent));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(reply));

        var result = rankingService(client).list("year", "10").block();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().commentCount()).isEqualTo(1);
        assertThat(result.items().getFirst().repliesCount()).isEqualTo(1);
        assertThat(result.items().getFirst().emailHash()).isEqualTo(READER_EMAIL_HASH);
    }

    @Test
    void normalizesPeriodAndClampsLimit() {
        assertThat(CommentNextCommentRankingPeriod.from("MONTH"))
            .isEqualTo(CommentNextCommentRankingPeriod.MONTH);
        assertThat(CommentNextCommentRankingPeriod.from("invalid"))
            .isEqualTo(CommentNextCommentRankingPeriod.ALL);

        var client = rankingClient();
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());

        var result = rankingService(client).list("invalid", "999").block();

        assertThat(result).isNotNull();
        assertThat(result.period()).isEqualTo("all");
        assertThat(result.limit()).isEqualTo(50);
        assertThat(result.items()).isEmpty();
    }

    @Test
    void usesRealMemberAvatarAndFallsBackToCommentOwnerAvatar() {
        var client = rankingClient();
        var member = new User();
        member.setMetadata(metadata("member"));
        member.getSpec().setDisplayName("站点读者");

        var publicComment = comment("member-comment", "reader@example.com", true, false);
        var owner = publicComment.getSpec().getOwner();
        owner.setKind(User.KIND);
        owner.setName("member");
        owner.setAnnotations(Map.of(Comment.CommentOwner.AVATAR_ANNO,
            "https://example.com/portrait.jpg"));

        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(member));
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(publicComment));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());

        var service = rankingService(client);
        assertThat(service.list("all", "10").block().items().getFirst().avatar())
            .isEqualTo("https://example.com/portrait.jpg");
        assertThat(service.list("all", "10").block().items().getFirst().emailHash()).isNull();

        member.getSpec().setAvatar("https://example.com/current-avatar.jpg");
        assertThat(rankingService(client).list("all", "10").block()
            .items().getFirst().avatar())
            .isEqualTo("https://example.com/current-avatar.jpg");
    }

    @Test
    void selectsLatestVisibleContentByDateAndStripsHtml() {
        var client = rankingClient();
        var older = comment("z-older", "reader@example.com", true, false);
        older.getSpec().setContent("<p>older public content</p>");
        var latest = comment("a-latest", "reader@example.com", true, false);
        latest.getSpec().setCreationTime(Instant.parse("2026-09-14T00:00:00Z"));
        latest.getSpec().setContent("<p>Latest <strong>public</strong> comment</p><script>secret</script>");
        var hidden = comment("hidden", "reader@example.com", true, true);
        hidden.getSpec().setCreationTime(Instant.parse("2026-09-15T00:00:00Z"));
        hidden.getSpec().setContent("hidden content must never appear");

        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(latest, hidden, older));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(reply("older-reply", "a-latest", "reader@example.com", true, false)));

        var result = rankingService(client).list("all", "10").block();

        assertThat(result.items().getFirst().commentCount()).isEqualTo(3);
        assertThat(result.items().getFirst().latestComment()).isEqualTo("Latest public comment");
        assertThat(result.items().getFirst().latestCommentAt())
            .isEqualTo(Instant.parse("2026-09-14T00:00:00Z"));
        assertThat(result.toString()).doesNotContain("secret", "hidden content", "reader@example.com");
    }

    @Test
    void sumsUpvotesOnlyForPublicCommentsAndRepliesInSelectedPeriod() {
        var client = rankingClient();
        var recent = Instant.now().minusSeconds(60);
        var publicComment = comment("same-name", "reader@example.com", true, false);
        publicComment.getSpec().setCreationTime(recent);
        var negative = comment("negative", "reader@example.com", true, false);
        negative.getSpec().setCreationTime(recent);
        var missingCounter = comment("missing-counter", "reader@example.com", true, false);
        missingCounter.getSpec().setCreationTime(recent);
        var old = comment("old", "reader@example.com", true, false);
        old.getSpec().setCreationTime(Instant.EPOCH);
        var publicReply = reply("same-name", "same-name", "reader@example.com", true, false);
        publicReply.getSpec().setCreationTime(recent);

        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(publicComment, negative, missingCounter, old,
                comment("hidden", "reader@example.com", true, true),
                comment("pending", "reader@example.com", false, false)));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(publicReply,
                reply("hidden-parent", "hidden", "reader@example.com", true, false)));
        when(client.fetch(Counter.class, "comments.content.halo.run/same-name"))
            .thenReturn(Mono.just(counter(7)));
        when(client.fetch(Counter.class, "replies.content.halo.run/same-name"))
            .thenReturn(Mono.just(counter(11)));
        when(client.fetch(Counter.class, "comments.content.halo.run/negative"))
            .thenReturn(Mono.just(counter(-5)));

        var result = rankingService(client).list("year", "10").block();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().commentCount()).isEqualTo(4);
        assertThat(result.items().getFirst().repliesCount()).isEqualTo(1);
        assertThat(result.items().getFirst().upvoteCount()).isEqualTo(18);
        verify(client, never()).fetch(Counter.class, "comments.content.halo.run/old");
        verify(client, never()).fetch(Counter.class, "comments.content.halo.run/hidden");
        verify(client, never()).fetch(Counter.class, "comments.content.halo.run/pending");
        verify(client, never()).fetch(Counter.class, "replies.content.halo.run/hidden-parent");
    }

    private ReactiveExtensionClient rankingClient() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.fetch(eq(Counter.class), anyString())).thenReturn(Mono.empty());
        when(client.fetch(eq(CommentNextBadgeProfile.class), anyString())).thenReturn(Mono.empty());
        return client;
    }

    private CommentNextCommentRankingService rankingService(ReactiveExtensionClient client) {
        return new CommentNextCommentRankingService(client, new CommentNextCounterService(client),
            settings, new CommentNextBadgeProfileService(client));
    }

    @Test
    void excludesUsersAndEmailsBeforeLimitWithoutHidingOtherReadersReplies() {
        var client = rankingClient();
        var host = comment("host-comment", "unused@example.com", true, false);
        host.getSpec().getOwner().setKind(User.KIND);
        host.getSpec().getOwner().setName("host");
        var reader = comment("reader-comment", "reader@example.com", true, false);
        reader.getSpec().getOwner().setDisplayName("读者 Z");
        var readerReply = reply("reader-reply", "host-comment", "reader@example.com", true, false);
        readerReply.getSpec().getOwner().setDisplayName("读者 Z");
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(host, reader,
                comment("email-comment", "Excluded@Example.com", true, false)));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                readerReply,
                reply("email-reply", "reader-comment", "excluded@example.com", true, false)));
        var service = rankingService(client);
        assertThat(service.list("all", "1").block().items().getFirst().displayName()).isEqualTo("读者");
        var config = new CommentNextRankingConfig();
        config.setExcludedUsers(List.of(new CommentNextRankingConfig.ExcludedUser(" host ")));
        config.setExcludedEmails(List.of(new CommentNextRankingConfig.ExcludedEmail(" EXCLUDED@example.com ")));
        when(settings.getRankingConfig()).thenReturn(Mono.just(config));

        var result = service.list("all", "1").block();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().displayName()).isEqualTo("读者 Z");
        assertThat(result.items().getFirst().commentCount()).isEqualTo(2);
        assertThat(result.items().getFirst().repliesCount()).isEqualTo(1);
        assertThat(result.toString()).doesNotContain("excluded@example.com", "reader@example.com", "host");
    }

    @Test
    void appliesUpdatedExclusionsToCachedRankingAndMatchesMemberEmail() {
        var client = rankingClient();
        var member = new User();
        member.setMetadata(metadata("host"));
        member.getSpec().setEmail("Host@Example.com");
        var host = comment("host-comment", "unused@example.com", true, false);
        host.getSpec().getOwner().setKind(User.KIND);
        host.getSpec().getOwner().setName("host");
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(member));
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(host));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        var service = rankingService(client);
        assertThat(service.list("all", "10").block().items()).hasSize(1);

        var config = new CommentNextRankingConfig();
        config.setExcludedEmails(List.of(new CommentNextRankingConfig.ExcludedEmail(" host@example.COM ")));
        when(settings.getRankingConfig()).thenReturn(Mono.just(config));
        assertThat(service.list("all", "10").block().items()).isEmpty();
        verify(client, times(1)).listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class));

        when(settings.getRankingConfig()).thenReturn(Mono.just(new CommentNextRankingConfig()));
        assertThat(service.list("all", "10").block().items()).hasSize(1);
    }

    @Test
    void usesTheSameLifetimeLevelBadgeAsCommentsAcrossRankingPeriods() {
        var client = rankingClient();
        var comment = comment("recent-comment", "Reader@example.com", true, false);
        comment.getSpec().setCreationTime(Instant.now().minusSeconds(60));
        comment.getSpec().getOwner().setAnnotations(Map.of(Comment.CommentOwner.WEBSITE_ANNO,
            "https://reader.example.com"));
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(comment));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        var level = new CommentNextBadge("level-100", "资深读者", "level",
            "mdi:star-outline", "#31536f", "累计评论 100 条");
        var profile = new CommentNextBadgeProfile();
        profile.getSpec().setActiveCommentCount(120);
        profile.getSpec().setBadges(List.of(
            new CommentNextBadge("custom", "共建者", "custom", null, null, null), level));
        when(client.fetch(CommentNextBadgeProfile.class,
            CommentNextBadgeIdentity.email("reader@example.com").orElseThrow().profileName()))
            .thenReturn(Mono.just(profile));

        var service = rankingService(client);
        for (var period : List.of("week", "month", "quarter", "year", "all")) {
            var item = service.list(period, "10").block().items().getFirst();
            assertThat(item.commentCount()).isEqualTo(1);
            assertThat(item.repliesCount()).isZero();
            assertThat(item.website()).isEqualTo("https://reader.example.com");
            assertThat(item.emailHash()).isEqualTo(READER_EMAIL_HASH);
            assertThat(item.levelBadge()).isEqualTo(level);
        }
        profile.getSpec().setBadges(List.of());
        assertThat(service.list("all", "10").block().items().getFirst().levelBadge()).isNull();
    }

    private Counter counter(int upvotes) {
        var counter = new Counter();
        counter.setUpvote(upvotes);
        return counter;
    }

    private Comment comment(String name, String email, boolean approved, boolean hidden) {
        var spec = new Comment.CommentSpec();
        spec.setOwner(owner(email));
        spec.setApproved(approved);
        spec.setHidden(hidden);
        spec.setContent("<p>" + name + " content</p>");
        spec.setCreationTime(Instant.parse("2026-09-10T00:00:00Z"));

        var comment = new Comment();
        comment.setMetadata(metadata(name));
        comment.setSpec(spec);
        return comment;
    }

    private Reply reply(String name, String parentName, String email, boolean approved,
        boolean hidden) {
        var spec = new Reply.ReplySpec();
        spec.setCommentName(parentName);
        spec.setOwner(owner(email));
        spec.setApproved(approved);
        spec.setHidden(hidden);
        spec.setContent("<p>" + name + " content</p>");
        spec.setCreationTime(Instant.parse("2026-09-13T00:00:00Z"));

        var reply = new Reply();
        reply.setMetadata(metadata(name));
        reply.setSpec(spec);
        return reply;
    }

    private Comment.CommentOwner owner(String email) {
        var owner = new Comment.CommentOwner();
        owner.setKind(Comment.CommentOwner.KIND_EMAIL);
        owner.setName(email);
        owner.setDisplayName("读者");
        return owner;
    }

    private Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
