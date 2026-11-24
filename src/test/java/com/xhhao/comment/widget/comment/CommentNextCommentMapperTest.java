package com.xhhao.comment.widget.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.iplocation.CommentNextIpLocationAnnotation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.Metadata;

class CommentNextCommentMapperTest {

    @Test
    void commentsAndRepliesExposeTheSameNormalizedHashAsRankingsWithoutPrivateAnnotations() {
        var authors = mock(CommentNextAuthorService.class);
        when(authors.resolve(any(), any())).thenReturn(Mono.just(new CommentNextAuthor(
            "", "Reader", "", "Email", "anonymous", 0, List.of(), null)));
        var settings = mock(SettingConfigGetter.class);
        var mapper = new CommentNextCommentMapper(authors, settings);

        var owner = new Comment.CommentOwner();
        owner.setName(" READER@Example.com ");
        owner.setDisplayName("Reader");
        owner.setKind(Comment.CommentOwner.KIND_EMAIL);
        owner.setAnnotations(Map.of(
            Comment.CommentOwner.WEBSITE_ANNO, "https://reader.example.com",
            Comment.CommentOwner.EMAIL_HASH_ANNO, "untrusted-hash",
            "Email", "private@example.com"));
        var comment = new Comment();
        comment.setMetadata(new Metadata());
        comment.setSpec(new Comment.CommentSpec());
        comment.getSpec().setOwner(owner);
        comment.getSpec().setIpAddress("192.0.2.1");
        var reply = new Reply();
        reply.setMetadata(new Metadata());
        reply.setSpec(new Reply.ReplySpec());
        reply.getSpec().setOwner(owner);
        reply.getSpec().setIpAddress("192.0.2.1");

        var context = mock(CommentNextBadgeContext.class);
        for (var json : List.of(mapper.toCommentNode(comment, context, 0).block(),
            mapper.toReplyNode(reply, context, 0).block())) {
            var publicOwner = json.path("spec").path("owner");
            assertThat(publicOwner.path("name").asText()).isEmpty();
            assertThat(publicOwner.path("annotations").path("email-hash").asText())
                .isEqualTo("d108b279434fe1d54ac0f1da633564604b26c2e0e221d108b0fbadb87aba02c0");
            assertThat(publicOwner.path("annotations").path("website").asText())
                .isEqualTo("https://reader.example.com");
            assertThat(json.toString()).doesNotContain("READER@Example.com", "private@example.com",
                "192.0.2.1", "untrusted-hash");
            assertThat(json.path("spec").has("ipLocation")).isFalse();
        }
        verifyNoInteractions(settings);
    }

    @Test
    void returnsOnlyTheSavedLocationWhenDisplayIsEnabled() {
        var authors = mock(CommentNextAuthorService.class);
        when(authors.resolve(any(), any())).thenReturn(Mono.just(new CommentNextAuthor(
            "", "Reader", "", "Email", "anonymous", 0, List.of(), null)));
        var settings = mock(SettingConfigGetter.class);
        var config = new SettingConfigGetter.IpLocationConfig();
        when(settings.getIpLocationConfig()).thenReturn(Mono.just(config));
        var mapper = new CommentNextCommentMapper(authors, settings);

        var comment = new Comment();
        comment.setMetadata(new Metadata());
        comment.getMetadata().setAnnotations(Map.of(CommentNextIpLocationAnnotation.NAME, "广东"));
        comment.setSpec(new Comment.CommentSpec());
        comment.getSpec().setIpAddress("192.0.2.1");
        var reply = new Reply();
        reply.setMetadata(new Metadata());
        reply.getMetadata().setAnnotations(Map.of(CommentNextIpLocationAnnotation.NAME, "日本"));
        reply.setSpec(new Reply.ReplySpec());
        reply.getSpec().setIpAddress("2001:db8::1");
        var context = mock(CommentNextBadgeContext.class);

        var commentJson = mapper.toCommentNode(comment, context, 0).block();
        var replyJson = mapper.toReplyNode(reply, context, 0).block();
        assertThat(commentJson.path("spec").path("ipLocation").asText()).isEqualTo("广东");
        assertThat(replyJson.path("spec").path("ipLocation").asText()).isEqualTo("日本");
        assertThat(commentJson.toString()).doesNotContain("192.0.2.1", CommentNextIpLocationAnnotation.NAME);
        assertThat(replyJson.toString()).doesNotContain("2001:db8::1", CommentNextIpLocationAnnotation.NAME);

        config.setEnabled(false);
        assertThat(mapper.toCommentNode(comment, context, 0).block().toString()).doesNotContain("广东");
        assertThat(mapper.toReplyNode(reply, context, 0).block().toString()).doesNotContain("日本");
    }
}
