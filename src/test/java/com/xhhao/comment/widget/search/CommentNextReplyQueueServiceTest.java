package com.xhhao.comment.widget.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class CommentNextReplyQueueServiceTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);

    private final CommentNextReplyQueueService service = new CommentNextReplyQueueService(client);

    @Test
    void waitsForTheCurrentUsersDirectPublicReply() {
        var deleted = comment("deleted", "reader");
        deleted.getMetadata().setDeletionTimestamp(Instant.now());
        var pending = comment("pending", "reader");
        pending.getSpec().setApproved(false);
        var hidden = comment("hidden", "reader");
        hidden.getSpec().setHidden(true);

        when(client.listAll(eq(Comment.class), any(), any())).thenReturn(Flux.just(
            comment("unanswered", "reader"),
            comment("answered", "reader"),
            comment("dismissed", "reader"),
            comment("dismissed-by-others", "reader"),
            comment("self", "keeper"),
            deleted, pending, hidden
        ));
        var otherAuthor = reply("unanswered", "another-keeper");
        var pendingReply = reply("unanswered", "keeper");
        pendingReply.getSpec().setApproved(false);
        var nested = reply("unanswered", "keeper");
        nested.getSpec().setQuoteReply("another-reply");
        when(client.listAll(eq(Reply.class), any(), any())).thenReturn(Flux.just(
            otherAuthor, pendingReply, nested, reply("answered", "keeper")
        ));
        when(client.listAll(eq(CommentNextReplyQueueEntry.class), any(), any()))
            .thenReturn(Flux.just(entry("keeper", "dismissed"), entry("other", "dismissed-by-others")));

        assertThat(list(CommentNextCommentSearchQuery.ReplyQueue.WAITING))
            .containsExactly("unanswered", "dismissed-by-others");
        assertThat(list(CommentNextCommentSearchQuery.ReplyQueue.DISMISSED))
            .containsExactly("dismissed");
    }

    @Test
    void dismissesAndRestoresIndependentlyForEachAccount() {
        var entries = new HashMap<String, CommentNextReplyQueueEntry>();
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", "reader")));
        when(client.fetch(eq(CommentNextReplyQueueEntry.class), anyString())).thenAnswer(call ->
            Mono.justOrEmpty(entries.get(call.<String>getArgument(1))));
        when(client.create(any(CommentNextReplyQueueEntry.class))).thenAnswer(call -> {
            var entry = call.<CommentNextReplyQueueEntry>getArgument(0);
            entries.put(entry.getMetadata().getName(), entry);
            return Mono.just(entry);
        });
        when(client.update(any(CommentNextReplyQueueEntry.class))).thenAnswer(call ->
            Mono.just(call.<CommentNextReplyQueueEntry>getArgument(0)));

        dismiss("keeper", true);
        dismiss("keeper", true);
        dismiss("another-keeper", true);
        dismiss("keeper", false);

        assertThat(entries.values()).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.getSpec().getCommentName()).isEqualTo("comment");
            assertThat(entry.getSpec().isDismissed())
                .isEqualTo("another-keeper".equals(entry.getSpec().getUsername()));
        });
    }

    @Test
    void rejectsAnonymousChanges() {
        assertThatThrownBy(() -> service.setDismissed("comment", true).block())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    private List<String> list(CommentNextCommentSearchQuery.ReplyQueue queue) {
        return service.listComments(queue, "").map(comment -> comment.getMetadata().getName())
            .collectList().contextWrite(authentication("keeper")).block();
    }

    private void dismiss(String username, boolean dismissed) {
        service.setDismissed("comment", dismissed).contextWrite(authentication(username)).block();
    }

    private static Context authentication(String username) {
        return ReactiveSecurityContextHolder.withAuthentication(
            new UsernamePasswordAuthenticationToken(username, "", List.of()));
    }

    private static Comment comment(String name, String author) {
        var comment = new Comment();
        var metadata = new Metadata();
        metadata.setName(name);
        comment.setMetadata(metadata);
        var spec = new Comment.CommentSpec();
        spec.setApproved(true);
        spec.setOwner(owner(author));
        comment.setSpec(spec);
        return comment;
    }

    private static Reply reply(String commentName, String author) {
        var reply = new Reply();
        var spec = new Reply.ReplySpec();
        spec.setCommentName(commentName);
        spec.setApproved(true);
        spec.setOwner(owner(author));
        reply.setSpec(spec);
        return reply;
    }

    private static Comment.CommentOwner owner(String username) {
        var owner = new Comment.CommentOwner();
        owner.setKind(User.KIND);
        owner.setName(username);
        return owner;
    }

    private static CommentNextReplyQueueEntry entry(String username, String commentName) {
        var entry = new CommentNextReplyQueueEntry();
        entry.getSpec().setUsername(username);
        entry.getSpec().setCommentName(commentName);
        entry.getSpec().setDismissed(true);
        return entry;
    }
}
