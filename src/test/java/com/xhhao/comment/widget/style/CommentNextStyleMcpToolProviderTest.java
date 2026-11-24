package com.xhhao.comment.widget.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.style.CommentNextStyleProfile.Spec;
import java.math.BigInteger;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolException;
import run.halo.mcpserver.api.McpToolInvocation;
import run.halo.mcpserver.api.McpToolResult;

class CommentNextStyleMcpToolProviderTest {

    private static final String GET_STYLE = "get_comment_style";
    private static final String SAVE_DRAFT = "save_comment_style_draft";
    private static final String WORKBENCH = "/console/comments/comment-next-style";
    private static final ObjectMapper MAPPER = JsonUtils.createObjectMapper();

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final ExternalUrlSupplier externalUrl = mock(ExternalUrlSupplier.class);
    private final CommentNextStyleStore store = new CommentNextStyleStore(client);
    private final CommentNextStyleMcpToolProvider provider = new CommentNextStyleMcpToolProvider(store, externalUrl);

    @Test
    void contributesReadAndDraftToolsTogether() {
        assertThat(provider.tools().map(McpToolDefinition::name).collectList().block())
            .containsExactlyInAnyOrder(GET_STYLE, SAVE_DRAFT);

        var read = tool(GET_STYLE);
        var save = tool(SAVE_DRAFT);
        assertThat(read.category()).isNotBlank().isEqualTo(save.category());
        assertThat(read.annotations().readOnlyHint()).isTrue();
        assertThat(save.annotations().readOnlyHint()).isFalse();
        assertThat(save.annotations().destructiveHint()).isFalse();
        assertThat(save.inputSchema()).containsEntry("additionalProperties", false);
        assertThat(save.inputSchema().get("required"))
            .isIn(List.of("revision", "changes"), List.of("changes", "revision"));
    }

    @Test
    void readsTheInitialStylesAndLinksToTheWorkbench() throws Exception {
        when(client.fetch(eq(CommentNextStyleProfile.class), eq("comment-next-style"))).thenReturn(Mono.empty());

        var local = call(GET_STYLE, Map.of());
        assertThat(local.structuredContent()).containsEntry("revision", -1L).containsEntry("previewUrl", WORKBENCH);
        assertThat(design(local).path("draft").isObject()).isTrue();
        assertThat(design(local).path("published")).isEqualTo(design(local).path("draft"));

        when(externalUrl.getRaw()).thenReturn(URI.create("https://blog.example.com/").toURL());
        assertThat(call(GET_STYLE, Map.of()).structuredContent())
            .containsEntry("previewUrl", "https://blog.example.com" + WORKBENCH);
        verify(client, never()).create(any(CommentNextStyleProfile.class));
        verify(client, never()).update(any(CommentNextStyleProfile.class));
    }

    @Test
    void createsTheFirstDraftFromTheInitialRevision() {
        when(client.fetch(eq(CommentNextStyleProfile.class), eq("comment-next-style"))).thenReturn(Mono.empty());
        when(client.create(any(CommentNextStyleProfile.class))).thenAnswer(call -> {
            CommentNextStyleProfile created = call.getArgument(0);
            created.getMetadata().setVersion(0L);
            return Mono.just(created);
        });

        var result = call(SAVE_DRAFT, Map.of("revision", -1, "changes", Map.of("density", "compact")));

        assertThat(result.structuredContent()).containsEntry("revision", 0L);
        assertThat(design(result).path("draft").path("density").asText()).isEqualTo("compact");
        assertThat(design(result).path("published").hasNonNull("density")).isFalse();
        verify(client, never()).update(any(CommentNextStyleProfile.class));
    }

    @Test
    void savesOnlyDraftFieldsAndRejectsStaleRevisions() {
        var originalDraft = new CommentNextStyle("#244c3c", "#9ed4bb", 12, "compact", "cards");
        var published = new CommentNextStyle("#726344", null, 5, "spacious", "flat");
        var previous = new CommentNextStyle(null, null, 7, null, "cards");
        var profile = stubProfile(3, new Spec(originalDraft, published, previous));
        when(client.update(any(CommentNextStyleProfile.class))).thenAnswer(call -> {
            CommentNextStyleProfile updated = call.getArgument(0);
            updated.getMetadata().setVersion(4L);
            return Mono.just(updated);
        });

        var changes = new LinkedHashMap<String, Object>();
        changes.put("lightAccent", null);
        changes.put("radius", 6);
        var result = call(SAVE_DRAFT, Map.of("revision", 3, "changes", changes));

        assertThat(result.error()).isFalse();
        assertThat(result.structuredContent()).containsEntry("revision", 4L).containsEntry("previewUrl", WORKBENCH);
        var savedDraft = new CommentNextStyle(null, "#9ed4bb", 6, "compact", "cards");
        assertThat(profile.getSpec()).isEqualTo(new Spec(savedDraft, published, previous));
        assertThat(design(result).path("draft").path("darkAccent").asText()).isEqualTo("#9ed4bb");
        assertThat(design(result).path("draft").hasNonNull("lightAccent")).isFalse();
        assertThat(design(result).path("published").path("radius").asInt()).isEqualTo(5);

        assertToolError("CONFLICT", Map.of("revision", 3, "changes", Map.of("radius", 10)));
        assertThat(profile.getSpec()).isEqualTo(new Spec(savedDraft, published, previous));
        verify(client).update(any(CommentNextStyleProfile.class));
    }

    @Test
    void reportsAConcurrentWriteAsAnMcpConflict() {
        stubProfile(3, new Spec(null, null, null));
        when(client.update(any(CommentNextStyleProfile.class)))
            .thenReturn(Mono.error(new OptimisticLockingFailureException("another editor saved first")));

        assertToolError("CONFLICT", Map.of("revision", 3, "changes", Map.of("radius", 10)));
    }

    @Test
    void validatesDraftArgumentsBeforeWriting() {
        var state = new Spec(null, null, null);
        var profile = stubProfile(3, state);
        var changes = Map.of("radius", 6);

        for (var invalid : List.<Map<String, Object>>of(
            Map.of("changes", changes),
            Map.of("revision", -2, "changes", changes),
            Map.of("revision", "3", "changes", changes),
            Map.of("revision", 3.5, "changes", changes),
            Map.of("revision", new BigInteger("9223372036854775808"), "changes", changes),
            Map.of("revision", 3, "changes", changes, "publish", true),
            Map.of("revision", 3, "changes", List.of()),
            Map.of("revision", 3, "changes", Map.of("radius", "6")),
            Map.of("revision", 3, "changes", Map.of("radius", 25)),
            Map.of("revision", 3, "changes", Map.of("density", "airy")),
            Map.of("revision", 3, "changes", Map.of("css", "body { display: none; }"))
        )) {
            assertToolError("INVALID_ARGUMENT", invalid);
        }

        assertThat(profile.getSpec()).isEqualTo(state);
        verify(client, never()).create(any(CommentNextStyleProfile.class));
        verify(client, never()).update(any(CommentNextStyleProfile.class));
    }

    private CommentNextStyleProfile stubProfile(long revision, Spec spec) {
        var profile = new CommentNextStyleProfile();
        var metadata = new Metadata();
        metadata.setName("comment-next-style");
        metadata.setVersion(revision);
        profile.setMetadata(metadata);
        profile.setSpec(spec);
        when(client.fetch(eq(CommentNextStyleProfile.class), eq("comment-next-style"))).thenReturn(Mono.just(profile));
        return profile;
    }

    private McpToolDefinition tool(String name) {
        return provider.tools().filter(definition -> name.equals(definition.name())).blockFirst();
    }

    private McpToolResult call(String name, Map<String, Object> arguments) {
        return tool(name).handler().execute(new McpToolInvocation(name, arguments)).block();
    }

    private static JsonNode design(McpToolResult result) {
        return MAPPER.valueToTree(result.structuredContent().get("design"));
    }

    private void assertToolError(String code, Map<String, Object> arguments) {
        assertThatThrownBy(() -> call(SAVE_DRAFT, arguments))
            .isInstanceOfSatisfying(McpToolException.class, error -> assertThat(error.code()).isEqualTo(code));
    }
}
