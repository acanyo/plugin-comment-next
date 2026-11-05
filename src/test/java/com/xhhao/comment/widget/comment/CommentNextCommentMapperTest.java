package com.xhhao.comment.widget.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.iplocation.CommentNextIpLocationService;
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
        var locations = mock(CommentNextIpLocationService.class);
        when(locations.resolve(any())).thenReturn(Mono.just(""));
        var mapper = new CommentNextCommentMapper(authors, locations);

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
        }
    }
}
