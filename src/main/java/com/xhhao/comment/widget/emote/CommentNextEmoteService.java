package com.xhhao.comment.widget.emote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.comment.utils.JsonUtils;
import java.util.Comparator;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

import static run.halo.app.extension.index.query.Queries.equal;

@Component
@RequiredArgsConstructor
public class CommentNextEmoteService {

    private final ReactiveExtensionClient client;

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    public Mono<ObjectNode> enabledEmotePacks() {
        var options = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(equal("spec.enabled", true))
            .build();

        return client.listAll(CommentNextEmoteGroup.class, options, Sort.by("metadata.name"))
            .sort(Comparator
                .comparingInt(this::priority)
                .thenComparing(this::displayName))
            .collectList()
            .map(groups -> {
                var root = objectMapper.createObjectNode();
                groups.forEach(group -> appendGroup(root, group));
                return root;
            });
    }

    private void appendGroup(ObjectNode root, CommentNextEmoteGroup group) {
        var spec = group.getSpec();
        if (spec == null || !StringUtils.hasText(spec.getDisplayName())) {
            return;
        }

        var groupNode = objectMapper.createObjectNode();
        groupNode.put("type", normalizeType(spec.getType()));
        var container = objectMapper.createArrayNode();
        Optional.ofNullable(spec.getItems()).orElseGet(java.util.List::of)
            .stream()
            .filter(item -> item != null && StringUtils.hasText(item.getIcon()))
            .forEach(item -> {
                var itemNode = objectMapper.createObjectNode();
                itemNode.put("icon", item.getIcon());
                itemNode.put("text", Optional.ofNullable(item.getText()).orElse(""));
                container.add(itemNode);
            });
        groupNode.set("container", container);
        root.set(spec.getDisplayName(), groupNode);
    }

    private int priority(CommentNextEmoteGroup group) {
        return Optional.ofNullable(group.getSpec())
            .map(CommentNextEmoteGroup.Spec::getPriority)
            .orElse(0);
    }

    private String displayName(CommentNextEmoteGroup group) {
        return Optional.ofNullable(group.getSpec())
            .map(CommentNextEmoteGroup.Spec::getDisplayName)
            .orElse("");
    }

    private String normalizeType(String type) {
        return "image".equals(type) ? "image" : "emoticon";
    }
}
