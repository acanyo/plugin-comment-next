package com.xhhao.comment.widget.iplocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class CommentNextIpLocationRecorderTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final CommentNextIpLocationService locations = mock(CommentNextIpLocationService.class);
    private final CommentNextIpLocationRecorder recorder = new CommentNextIpLocationRecorder(client, locations);

    @Test
    void retriesTheWriteAgainstLatestCommentWithoutRepeatingTheLookup() {
        var first = comment("8.8.8.8");
        var beforeConflict = comment("8.8.8.8");
        var latest = comment("8.8.8.8");
        latest.getMetadata().setAnnotations(Map.of("existing-annotation", "kept"));
        when(client.fetch(Comment.class, "comment"))
            .thenReturn(Mono.just(first))
            .thenReturn(Mono.just(beforeConflict))
            .thenReturn(Mono.just(latest));
        when(locations.resolve("8.8.8.8")).thenReturn(Mono.just("广东"));
        when(client.update(same(beforeConflict)))
            .thenReturn(Mono.error(new OptimisticLockingFailureException("conflict")));
        when(client.update(same(latest))).thenReturn(Mono.just(latest));

        recorder.record(Comment.class, "comment", Comment::getSpec).block();

        verify(locations).resolve("8.8.8.8");
        verify(client).update(same(latest));
        assertThat(latest.getMetadata().getAnnotations())
            .containsEntry("existing-annotation", "kept")
            .containsEntry(CommentNextIpLocationAnnotation.NAME, "广东")
            .doesNotContainValue("8.8.8.8");
    }

    @Test
    void savesTheReplyLocationAfterTheLookupCompletes() {
        var reply = new Reply();
        reply.setMetadata(new Metadata());
        reply.setSpec(new Reply.ReplySpec());
        reply.getSpec().setIpAddress("2001:4860::8888");
        var lookup = Sinks.<String>one();
        when(client.fetch(Reply.class, "reply")).thenReturn(Mono.just(reply));
        when(locations.resolve("2001:4860::8888")).thenReturn(lookup.asMono());
        when(client.update(same(reply))).thenReturn(Mono.just(reply));

        var recording = recorder.record(Reply.class, "reply", Reply::getSpec).toFuture();
        verify(client, never()).update(any());
        lookup.emitValue("日本", Sinks.EmitFailureHandler.FAIL_FAST);
        recording.join();

        verify(client).update(same(reply));
        assertThat(reply.getMetadata().getAnnotations())
            .containsEntry(CommentNextIpLocationAnnotation.NAME, "日本");
    }

    @Test
    void skipsTheWriteIfTheIpChangedDuringTheLookup() {
        when(client.fetch(Comment.class, "comment"))
            .thenReturn(Mono.just(comment("8.8.8.8")))
            .thenReturn(Mono.just(comment("1.1.1.1")));
        when(locations.resolve("8.8.8.8")).thenReturn(Mono.just("广东"));

        recorder.record(Comment.class, "comment", Comment::getSpec).block();

        verify(client, never()).update(any());
    }

    private Comment comment(String ipAddress) {
        var comment = new Comment();
        comment.setMetadata(new Metadata());
        comment.setSpec(new Comment.CommentSpec());
        comment.getSpec().setIpAddress(ipAddress);
        return comment;
    }
}
