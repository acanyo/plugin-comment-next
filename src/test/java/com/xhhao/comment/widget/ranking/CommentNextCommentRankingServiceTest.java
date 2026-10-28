package com.xhhao.comment.widget.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.comment.CommentNextCounterService;
import java.time.Instant;
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

    @Test
    void groupsAnonymousEmailWithoutExposingItAndIgnoresPrivateContent() {
        var client = rankingClient();
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(client.listAll(eq(Comment.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                comment("public-1", "Reader@Example.com", true, false),
                comment("public-2", "reader@example.com", true, false),
                comment("unapproved", "reader@example.com", false, false),
                comment("hidden-parent", "other@example.com", true, true)
            ));
        when(client.listAll(eq(Reply.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(
                reply("reply-1", "public-1", "READER@example.com", true, false),
                reply("reply-hidden", "hidden-parent", "other@example.com", true, false),
                reply("reply-pending", "public-1", "reader@example.com", false, false)
            ));

        var result = rankingService(client).list("all", "10").block();

        assertThat(result).isNotNull();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().displayName()).isEqualTo("读者");
        assertThat(result.items().getFirst().commentCount()).isEqualTo(3);
        assertThat(result.items().getFirst().avatar()).contains("weavatar.com/avatar/");
        assertThat(result.items().getFirst().latestComment()).isEqualTo("reply-1 content");
        assertThat(result.items().getFirst().latestCommentAt())
            .isEqualTo(Instant.parse("2026-09-13T00:00:00Z"));
        assertThat(result.toString()).doesNotContain("reader@example.com");
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
        assertThat(result.items().getFirst().upvoteCount()).isEqualTo(18);
        verify(client, never()).fetch(Counter.class, "comments.content.halo.run/old");
        verify(client, never()).fetch(Counter.class, "comments.content.halo.run/hidden");
        verify(client, never()).fetch(Counter.class, "comments.content.halo.run/pending");
        verify(client, never()).fetch(Counter.class, "replies.content.halo.run/hidden-parent");
    }

    private ReactiveExtensionClient rankingClient() {
        var client = mock(ReactiveExtensionClient.class);
        when(client.fetch(eq(Counter.class), anyString())).thenReturn(Mono.empty());
        return client;
    }

    private CommentNextCommentRankingService rankingService(ReactiveExtensionClient client) {
        return new CommentNextCommentRankingService(client, new CommentNextCounterService(client));
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
