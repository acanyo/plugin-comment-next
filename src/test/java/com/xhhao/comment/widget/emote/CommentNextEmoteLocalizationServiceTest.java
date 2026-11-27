package com.xhhao.comment.widget.emote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.emote.CommentNextEmoteLocalizationService.StartRequest;
import com.xhhao.comment.widget.emote.CommentNextEmoteLocalizationService.State;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Group;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class CommentNextEmoteLocalizationServiceTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);
    private final CommentNextEmoteAssetFetcher fetcher = mock(CommentNextEmoteAssetFetcher.class);
    private final CommentNextEmoteAttachmentUploader uploader = mock(CommentNextEmoteAttachmentUploader.class);
    private final CommentNextEmoteLocalizationService service =
        new CommentNextEmoteLocalizationService(client, settings, fetcher, uploader);

    @AfterEach
    void tearDown() {
        service.stop();
    }

    @Test
    void localizesExternalImagesDeduplicatesUrlsAndPreservesFailures() {
        var policy = policy("default-policy", "本地存储");
        var attachmentGroup = attachmentGroup("emote-folder", "评论表情");
        when(client.fetch(Policy.class, "default-policy")).thenReturn(Mono.just(policy));
        when(client.fetch(Group.class, "emote-folder")).thenReturn(Mono.just(attachmentGroup));

        var siteUri = URI.create("https://blog.example.com/");
        when(uploader.resolveSiteBaseUri(siteUri)).thenReturn(siteUri);

        var preExisting = new Attachment();
        preExisting.setMetadata(metadata("att-reused"));
        preExisting.getMetadata().setAnnotations(Map.of(
            CommentNextEmoteAttachmentUploader.SOURCE_URL_ANNOTATION,
            "https://cdn.remote.test/reused.png"
        ));
        when(client.listAll(eq(Attachment.class), any(), any())).thenReturn(Flux.just(preExisting));
        when(uploader.resolveExistingPermalink(eq(preExisting), eq(true), eq(siteUri)))
            .thenReturn(Mono.just("https://blog.example.com/upload/reused.png"));

        var emoteGroup = emoteGroup("group-alu", "阿鲁", List.of(
            item("<img src=\"/upload/already-local.png\" alt=\"local\">", "local"),
            item("<img src=\"https://cdn.remote.test/reused.png\" alt=\"reused\">", "reused"),
            item("<img src=\"https://cdn.remote.test/a.png\" origin=\"https://cdn.remote.test/a-origin.png\" alt=\"a1\">", "a1"),
            item("<img src=\"https://cdn.remote.test/a-origin.png\" alt=\"a2\">", "a2"),
            item("<img src=\"https://cdn.remote.test/broken.png\" alt=\"broken\">", "broken")
        ));
        when(client.fetch(CommentNextEmoteGroup.class, "group-alu")).thenReturn(Mono.just(emoteGroup));
        when(client.update(any(CommentNextEmoteGroup.class)))
            .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        when(fetcher.fetchImage("https://cdn.remote.test/a-origin.png"))
            .thenReturn(Mono.just(new CommentNextEmoteAssetFetcher.DownloadedImage(
                URI.create("https://cdn.remote.test/a-origin.png"),
                new byte[] {1, 2, 3},
                MediaType.IMAGE_PNG,
                "png"
            )));
        when(fetcher.fetchImage("https://cdn.remote.test/broken.png"))
            .thenReturn(Mono.error(new IllegalStateException("HTTP 404")));

        when(uploader.upload(
            eq("admin"),
            eq("default-policy"),
            eq("emote-folder"),
            anyString(),
            any(byte[].class),
            eq(MediaType.IMAGE_PNG),
            eq("https://cdn.remote.test/a-origin.png"),
            eq(true),
            eq(siteUri)
        )).thenReturn(Mono.just("https://blog.example.com/upload/a-origin.png"));

        var started = service.start(
            new StartRequest(List.of("group-alu"), "default-policy", "emote-folder", true),
            "admin",
            siteUri
        ).block();

        assertThat(started).isNotNull();
        assertThat(started.state()).isEqualTo(State.RUNNING);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var current = service.status().block();
            assertThat(current).isNotNull();
            assertThat(current.state()).isEqualTo(State.COMPLETED);
            assertThat(current.totalItems()).isEqualTo(5);
            assertThat(current.processedItems()).isEqualTo(5);
            assertThat(current.skippedItems()).isEqualTo(1);
            assertThat(current.localizedItems()).isEqualTo(1);
            assertThat(current.reusedItems()).isEqualTo(2);
            assertThat(current.failedItems()).isEqualTo(1);
            assertThat(current.failures()).hasSize(1);
            assertThat(current.failures().getFirst().sourceUrl())
                .isEqualTo("https://cdn.remote.test/broken.png");
        });

        verify(fetcher, times(1)).fetchImage("https://cdn.remote.test/a-origin.png");
        verify(client, times(1)).update(emoteGroup);
        assertThat(emoteGroup.getSpec().getItems().get(1).getIcon())
            .contains("https://blog.example.com/upload/reused.png");
        assertThat(emoteGroup.getSpec().getItems().get(2).getIcon())
            .contains("https://blog.example.com/upload/a-origin.png")
            .doesNotContain("origin=");
        assertThat(emoteGroup.getSpec().getItems().get(3).getIcon())
            .contains("https://blog.example.com/upload/a-origin.png");
        assertThat(emoteGroup.getSpec().getItems().get(4).getIcon())
            .contains("https://cdn.remote.test/broken.png");
    }

    @Test
    void rejectsConcurrentStartWhileRunningAndSupportsCancel() {
        var policy = policy("default-policy", "本地存储");
        when(client.fetch(Policy.class, "default-policy")).thenReturn(Mono.just(policy));

        var siteUri = URI.create("https://blog.example.com/");
        when(uploader.resolveSiteBaseUri(siteUri)).thenReturn(siteUri);

        var emoteGroup = emoteGroup("group-slow", "Slow", List.of(
            item("<img src=\"https://cdn.remote.test/slow.png\" alt=\"slow\">", "slow")
        ));
        when(client.fetch(CommentNextEmoteGroup.class, "group-slow")).thenReturn(Mono.just(emoteGroup));

        var pendingDownload = Sinks.<CommentNextEmoteAssetFetcher.DownloadedImage>one();
        when(fetcher.fetchImage("https://cdn.remote.test/slow.png")).thenReturn(pendingDownload.asMono());

        service.start(
            new StartRequest(List.of("group-slow"), "default-policy", "", true),
            "admin",
            siteUri
        ).block();

        await().atMost(Duration.ofSeconds(3))
            .untilAsserted(() -> verify(fetcher).fetchImage("https://cdn.remote.test/slow.png"));

        assertThatThrownBy(() -> service.start(
            new StartRequest(List.of("group-slow"), "default-policy", "", true),
            "admin",
            siteUri
        ).block()).isInstanceOf(ResponseStatusException.class);

        var cancelled = service.cancel().block();
        assertThat(cancelled).isNotNull();
        assertThat(cancelled.state()).isEqualTo(State.CANCELLED);
    }

    @Test
    void buildsOptionsWithHiddenGroupsFilteredAndDefaultPolicySelected() {
        var p1 = policy("oss-policy", "OSS");
        var p2 = policy("default-policy", "本地存储");
        var visibleGroup = attachmentGroup("emotes", "评论表情");
        var hiddenGroup = attachmentGroup("hidden-group", "隐藏组");
        hiddenGroup.getMetadata().setLabels(Map.of(Group.HIDDEN_LABEL, "true"));

        when(client.listAll(eq(Policy.class), any(), any())).thenReturn(Flux.just(p1, p2));
        when(client.listAll(eq(Group.class), any(), any())).thenReturn(Flux.just(visibleGroup, hiddenGroup));
        when(settings.getUploadConfig()).thenReturn(Mono.just(SettingConfigGetter.UploadConfig.empty()));
        when(uploader.resolveSiteBaseUri(any())).thenReturn(URI.create("https://blog.example.com/"));
        when(uploader.hasConfiguredExternalUrl()).thenReturn(true);

        var options = service.options(URI.create("https://blog.example.com/")).block();

        assertThat(options).isNotNull();
        assertThat(options.policies()).hasSize(2);
        assertThat(options.attachmentGroups()).extracting(CommentNextEmoteLocalizationService.AttachmentGroupOption::name)
            .containsExactly("emotes");
        assertThat(options.defaultPolicyName()).isEqualTo("default-policy");
        assertThat(options.externalBaseUrl()).isEqualTo("https://blog.example.com");
        assertThat(options.externalUrlConfigured()).isTrue();
    }

    private Policy policy(String name, String displayName) {
        var policy = new Policy();
        policy.setMetadata(metadata(name));
        var spec = new Policy.PolicySpec();
        spec.setDisplayName(displayName);
        policy.setSpec(spec);
        return policy;
    }

    private Group attachmentGroup(String name, String displayName) {
        var group = new Group();
        group.setMetadata(metadata(name));
        var spec = new Group.GroupSpec();
        spec.setDisplayName(displayName);
        group.setSpec(spec);
        return group;
    }

    private CommentNextEmoteGroup emoteGroup(String name, String displayName, List<CommentNextEmoteGroup.Item> items) {
        var group = new CommentNextEmoteGroup();
        group.setMetadata(metadata(name));
        var spec = new CommentNextEmoteGroup.Spec();
        spec.setDisplayName(displayName);
        spec.setType("image");
        spec.setProvider("OWO");
        spec.setItems(new ArrayList<>(items));
        group.setSpec(spec);
        return group;
    }

    private CommentNextEmoteGroup.Item item(String icon, String text) {
        var item = new CommentNextEmoteGroup.Item();
        item.setIcon(icon);
        item.setText(text);
        return item;
    }

    private Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
