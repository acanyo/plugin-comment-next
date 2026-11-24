package com.xhhao.comment.widget.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.style.CommentNextStyleStore.Operation;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;

class CommentNextStyleStoreTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final CommentNextStyleStore store = new CommentNextStyleStore(client);

    @Test
    void savesDraftAndPublishesOrRestoresIndependently() {
        var stored = new AtomicReference<CommentNextStyleProfile>();
        when(client.fetch(eq(CommentNextStyleProfile.class), eq("comment-next-style")))
            .thenAnswer(unused -> Mono.justOrEmpty(stored.get()));
        when(client.create(any(CommentNextStyleProfile.class))).thenAnswer(invocation -> {
            CommentNextStyleProfile profile = invocation.getArgument(0);
            profile.getMetadata().setVersion(0L);
            stored.set(profile);
            return Mono.just(profile);
        });
        when(client.update(any(CommentNextStyleProfile.class))).thenAnswer(invocation -> {
            CommentNextStyleProfile profile = invocation.getArgument(0);
            profile.getMetadata().setVersion(profile.getMetadata().getVersion() + 1);
            return Mono.just(profile);
        });

        var style = new CommentNextStyle("#244C3C", "#9ed4bb", 12, "compact", "cards");
        assertThat(store.current().block().revision()).isEqualTo(-1);
        var saved = store.change(-1, Operation.SAVE, style).block();
        assertThat(saved.design().draft()).isEqualTo(style);
        assertThat(store.published().block()).isEqualTo(CommentNextStyle.DEFAULT);

        assertThatThrownBy(() -> store.change(-1, Operation.PUBLISH, style).block())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                conflict -> assertThat(conflict.getStatusCode().value()).isEqualTo(409));

        var applied = store.change(saved.revision(), Operation.PUBLISH, style).block();
        assertThat(applied.design().published()).isEqualTo(style);
        assertThat(applied.design().previous()).isEqualTo(CommentNextStyle.DEFAULT);

        var reverted = store.change(applied.revision(), Operation.ROLLBACK, null).block();
        assertThat(reverted.design().draft()).isEqualTo(style);
        assertThat(reverted.design().published()).isEqualTo(CommentNextStyle.DEFAULT);
        assertThat(reverted.design().previous()).isEqualTo(style);
    }

    @Test
    void mergesValidatedFieldsAndSupportsClearingAsAnExplicitChange() throws Exception {
        var mapper = JsonUtils.createObjectMapper();
        var before = new CommentNextStyle("#244c3c", "#9ed4bb", 12, "compact", "cards");
        var after = before.apply(mapper.readTree("""
            {"radius": 4, "lightAccent": null}
            """));

        assertThat(after).isEqualTo(new CommentNextStyle(null, "#9ed4bb", 4, "compact", "cards"));
        for (var invalid : new String[] {
            "{\"css\": \"a{display:none}\"}",
            "{\"lightAccent\": \"url(https://example.test)\"}",
            "{\"radius\": 12.5}",
            "{\"radius\": 500}"
        }) {
            assertThatThrownBy(() -> before.apply(mapper.readTree(invalid)))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
