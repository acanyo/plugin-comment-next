package com.xhhao.comment.widget.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.Ref;

class CommentNextCommentSearchItemMapperTest {

    private final CommentNextCommentSearchItemMapper mapper =
        new CommentNextCommentSearchItemMapper();

    @Test
    void mapsRegisteredUserEmailForConsoleSearch() {
        var comment = commentOwnedBy(User.KIND, "halo-user");

        var item = mapper.fromComment(comment, "user@example.com");

        assertThat(item.username()).isEqualTo("halo-user");
        assertThat(item.email()).isEqualTo("user@example.com");
        assertThat(item.ipAddress()).isEqualTo("203.0.113.42");
        assertThat(item.subject()).isEqualTo("content.halo.run/Post/post-1");
    }

    @Test
    void mapsAnonymousEmailFromOwnerIdentity() {
        var comment = commentOwnedBy(Comment.CommentOwner.KIND_EMAIL, "guest@example.com");

        var item = mapper.fromComment(comment, "ignored@example.com");

        assertThat(item.username()).isEmpty();
        assertThat(item.email()).isEqualTo("guest@example.com");
    }

    private Comment commentOwnedBy(String kind, String name) {
        var owner = new Comment.CommentOwner();
        owner.setKind(kind);
        owner.setName(name);
        owner.setDisplayName("Commenter");

        var subject = new Ref();
        subject.setGroup("content.halo.run");
        subject.setVersion("v1alpha1");
        subject.setKind("Post");
        subject.setName("post-1");

        var spec = new Comment.CommentSpec();
        spec.setOwner(owner);
        spec.setSubjectRef(subject);
        spec.setContent("<p>Hello</p>");
        spec.setIpAddress("203.0.113.42");
        spec.setApproved(true);
        spec.setHidden(false);
        spec.setCreationTime(Instant.parse("2026-09-10T00:00:00Z"));

        var metadata = new Metadata();
        metadata.setName("comment-1");

        var comment = new Comment();
        comment.setMetadata(metadata);
        comment.setSpec(spec);
        return comment;
    }
}
