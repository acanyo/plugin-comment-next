package com.xhhao.comment.widget.iplocation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static run.halo.app.extension.index.query.Queries.greaterThan;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.SettingConfigGetter.IpLocationConfig;
import com.xhhao.comment.widget.iplocation.CommentNextIpLocationBackfill.Progress;
import com.xhhao.comment.widget.iplocation.CommentNextIpLocationBackfill.State;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class CommentNextIpLocationBackfillTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final CommentNextIpLocationService locations = mock(CommentNextIpLocationService.class);
    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);
    private final CommentNextIpLocationCache cache = new CommentNextIpLocationCache();
    private final CommentNextIpLocationBackfill backfill = new CommentNextIpLocationBackfill(
        client, new CommentNextIpLocationRecorder(client, locations), cache, settings);

    @AfterEach
    void stop() {
        backfill.stop();
    }

    @Test
    void requiresTheSavedDisplayAndOnlineLookupSettings() {
        when(settings.getIpLocationConfig())
            .thenReturn(Mono.just(IpLocationConfig.empty()))
            .thenReturn(Mono.just(IpLocationConfig.empty().setEnabled(false).setOnlineLookupEnabled(true)));

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> backfill.start().block())
                .isInstanceOf(ServerWebInputException.class);
        }

        assertThat(backfill.status().block().lookupEnabled()).isFalse();
        verifyNoInteractions(client, locations);
    }

    @Test
    void runsOnceInTheBackgroundAndProcessesCommentsAndRepliesAcrossPages() {
        when(settings.getIpLocationConfig()).thenReturn(Mono.just(enabled()));
        cache.put("failed-earlier", "", 15);
        cache.put("successful-earlier", "广东", 15);

        var existing = comment("first", "1.1.1.1");
        existing.getMetadata().setAnnotations(Map.of(CommentNextIpLocationAnnotation.NAME, "广东"));
        var missing = comment("second", "8.8.8.8");
        var noIp = reply("no-ip", null);
        var noMatch = reply("no-match", "2001:4860::8888");
        var writeFailure = reply("write-failure", "9.9.9.9");
        var replies = List.of(noIp, noMatch, writeFailure);

        var firstPage = ListOptions.builder().andQuery(ExtensionUtil.notDeleting()).build();
        var afterFirst = ListOptions.builder().andQuery(ExtensionUtil.notDeleting())
            .andQuery(greaterThan("metadata.name", "first")).build();
        when(client.listBy(eq(Comment.class), eq(firstPage), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 2, List.of(existing))));
        when(client.listBy(eq(Comment.class), eq(afterFirst), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(missing))));
        when(client.listBy(eq(Reply.class), eq(firstPage), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 100, replies.size(), replies)));
        when(client.fetch(Comment.class, "second")).thenReturn(Mono.just(missing));
        replies.forEach(reply -> when(client.fetch(Reply.class, reply.getMetadata().getName()))
            .thenReturn(Mono.just(reply)));
        when(client.update(same(missing))).thenReturn(Mono.just(missing));
        when(client.update(same(writeFailure))).thenReturn(Mono.error(new IllegalStateException()));

        var lookup = Sinks.<String>one();
        when(locations.resolve("8.8.8.8")).thenReturn(lookup.asMono());
        when(locations.resolve("2001:4860::8888")).thenReturn(Mono.empty());
        when(locations.resolve("9.9.9.9")).thenReturn(Mono.just("日本"));

        assertThat(backfill.start().block().progress().state()).isEqualTo(State.RUNNING);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> verify(locations).resolve("8.8.8.8"));

        assertThat(backfill.start().block().progress()).isEqualTo(new Progress(State.RUNNING, 0, 1, 0, 0));
        lookup.emitValue("美国", Sinks.EmitFailureHandler.FAIL_FAST);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(backfill.status().block().progress())
            .isEqualTo(new Progress(State.COMPLETED, 1, 2, 1, 1)));

        verify(client).listBy(eq(Comment.class), eq(firstPage), any());
        verify(client).listBy(eq(Comment.class), eq(afterFirst), any());
        verify(client, never()).fetch(Comment.class, "first");
        assertThat(missing.getMetadata().getAnnotations())
            .containsEntry(CommentNextIpLocationAnnotation.NAME, "美国");
        assertThat(cache.get("failed-earlier")).isEmpty();
        assertThat(cache.get("successful-earlier")).contains("广东");
    }

    private IpLocationConfig enabled() {
        return IpLocationConfig.empty().setOnlineLookupEnabled(true);
    }

    private Comment comment(String name, String ipAddress) {
        var comment = new Comment();
        comment.setMetadata(metadata(name));
        comment.setSpec(new Comment.CommentSpec());
        comment.getSpec().setIpAddress(ipAddress);
        return comment;
    }

    private Reply reply(String name, String ipAddress) {
        var reply = new Reply();
        reply.setMetadata(metadata(name));
        reply.setSpec(new Reply.ReplySpec());
        reply.getSpec().setIpAddress(ipAddress);
        return reply;
    }

    private Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
