package com.xhhao.comment.widget.style;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.style.CommentNextStyleStore.Operation;
import com.xhhao.comment.widget.style.CommentNextStyleStore.State;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.infra.ExternalUrlSupplier;
import run.halo.mcpserver.api.McpToolAnnotations;
import run.halo.mcpserver.api.McpToolDefinition;
import run.halo.mcpserver.api.McpToolException;
import run.halo.mcpserver.api.McpToolInvocation;
import run.halo.mcpserver.api.McpToolProvider;
import run.halo.mcpserver.api.McpToolResult;

@RequiredArgsConstructor
class CommentNextStyleMcpToolProvider implements McpToolProvider {

    private static final String CATEGORY = "评论样式设计";
    private static final String WORKBENCH_PATH = "/console/comments/comment-next-style";

    private final CommentNextStyleStore store;
    private final ExternalUrlSupplier externalUrl;
    private final ObjectMapper mapper = JsonUtils.createObjectMapper();

    @Override
    public Flux<McpToolDefinition> tools() {
        return Flux.just(readTool(), saveTool());
    }

    private McpToolDefinition readTool() {
        var title = "读取评论样式";
        return McpToolDefinition.builder()
            .name("get_comment_style")
            .title(title)
            .description("读取当前评论样式草稿、已发布样式、上一版和草稿版本号。返回工作台预览地址。"
                + "编辑前先读取 revision，并用于 save_comment_style_draft。")
            .category(CATEGORY)
            .inputSchema(Map.of("type", "object", "additionalProperties", false, "properties", Map.of()))
            .annotations(McpToolAnnotations.readOnly(title))
            // MCP Server checks the access key's grant for this tool before calling the provider.
            .permission(invocation -> Mono.just(true))
            .handler(invocation -> store.current().map(state -> result(state, "已读取评论样式。")))
            .build();
    }

    private McpToolDefinition saveTool() {
        var title = "保存评论样式草稿";
        return McpToolDefinition.builder()
            .name("save_comment_style_draft")
            .title(title)
            .description("按 revision 更新评论样式草稿。changes 中只填写要修改的字段，null 恢复主题默认值。"
                + "密度 compact 为紧凑，spacious 为宽松。列表 flat 为分隔线，cards 为卡片。"
                + "保存后把预览地址提供给用户，由用户在工作台确认并发布。遇到版本冲突时请重新读取。")
            .category(CATEGORY)
            .inputSchema(Map.of("type", "object", "additionalProperties", false,
                "required", List.of("revision", "changes"), "properties", Map.of(
                    "revision", Map.of("type", "integer",
                        "description", "get_comment_style 返回的 revision。"),
                    "changes", CommentNextStyle.mcpPatchSchema())))
            .annotations(new McpToolAnnotations(false, false, false, false, title))
            .permission(invocation -> Mono.just(true))
            .handler(this::save)
            .build();
    }

    private Mono<McpToolResult> save(McpToolInvocation invocation) {
        return Mono.defer(() -> {
            JsonNode input = mapper.valueToTree(invocation.arguments());
            var revision = input.path("revision");
            var changes = input.path("changes");
            if (!revision.isIntegralNumber() || !revision.canConvertToLong() || revision.longValue() < -1) {
                throw new McpToolException("INVALID_ARGUMENT", "请提供读取样式时返回的 revision。");
            }
            if (!changes.isObject() || input.size() != 2) {
                throw new McpToolException("INVALID_ARGUMENT", "请提供 revision 和 changes 对象。");
            }
            return store.current().flatMap(current -> {
                if (current.revision() != revision.longValue()) {
                    return Mono.error(conflict());
                }
                var draft = current.design().draft().apply(changes);
                return store.change(current.revision(), Operation.SAVE, draft);
            }).map(state -> result(state, "已保存草稿。请在工作台预览并发布。"));
        }).onErrorMap(IllegalArgumentException.class,
            error -> new McpToolException("INVALID_ARGUMENT", error.getMessage()))
            .onErrorMap(ResponseStatusException.class,
                error -> error.getStatusCode().value() == HttpStatus.CONFLICT.value() ? conflict() : error);
    }

    private McpToolResult result(State state, String message) {
        var design = mapper.convertValue(state.design(), new TypeReference<Map<String, Object>>() { });
        var base = externalUrl.getRaw();
        var previewUrl = (base == null ? "" : base.toString().replaceFirst("/$", "")) + WORKBENCH_PATH;
        return McpToolResult.success(Map.of("revision", state.revision(), "design", design,
            "previewUrl", previewUrl), message + " " + previewUrl);
    }

    private static McpToolException conflict() {
        return new McpToolException("CONFLICT", "样式已更新，请重新读取草稿后再保存。");
    }
}
