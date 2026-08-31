package com.xhhao.comment.widget.emote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
                var packs = objectMapper.createArrayNode();
                groups.forEach(group -> appendGroup(root, packs, group));
                // Keep the legacy display-name keyed fields for older clients,
                // while the array is the collision-safe contract used by the
                // current frontend.
                root.set("packs", packs);
                return root;
            });
    }

    private void appendGroup(ObjectNode root, ArrayNode packs, CommentNextEmoteGroup group) {
        var spec = group.getSpec();
        if (spec == null || !StringUtils.hasText(spec.getDisplayName())) {
            return;
        }

        var provider = normalizeProvider(spec.getProvider());
        var groupNode = objectMapper.createObjectNode();
        groupNode.put("provider", provider);
        groupNode.put("type", provider.equals("LOTTIE") ? "lottie" : normalizeType(spec.getType()));
        var container = objectMapper.createArrayNode();
        Optional.ofNullable(spec.getItems()).orElseGet(java.util.List::of)
            .stream()
            .forEach(item -> {
                if (item == null) {
                    return;
                }
                var itemNode = objectMapper.createObjectNode();
                itemNode.put("text", Optional.ofNullable(item.getText()).orElse(""));
                if (provider.equals("LOTTIE")) {
                    if (!StringUtils.hasText(item.getAnimationName())
                        || !StringUtils.hasText(item.getContentUrl())) {
                        return;
                    }
                    itemNode.put("type", "lottie");
                    itemNode.put("animationName", item.getAnimationName());
                    itemNode.put("contentUrl", item.getContentUrl());
                    if (StringUtils.hasText(item.getFormat())) {
                        itemNode.put("format", item.getFormat());
                    }
                    if (item.getDefaults() != null) {
                        itemNode.set("defaults", objectMapper.valueToTree(item.getDefaults()));
                    }
                } else {
                    if (!StringUtils.hasText(item.getIcon())) {
                        return;
                    }
                    itemNode.put("icon", item.getIcon());
                }
                container.add(itemNode);
            });
        groupNode.set("container", container);
        root.set(spec.getDisplayName(), groupNode);

        var packNode = groupNode.deepCopy();
        packNode.put("id", groupId(group));
        packNode.put("name", spec.getDisplayName());
        packNode.put("provider", provider);
        packs.add(packNode);
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

    private String normalizeProvider(String provider) {
        return "LOTTIE".equalsIgnoreCase(provider) ? "LOTTIE" : "OWO";
    }

    private String groupId(CommentNextEmoteGroup group) {
        return Optional.ofNullable(group.getMetadata())
            .map(metadata -> metadata.getName())
            .filter(StringUtils::hasText)
            .orElseGet(() -> "comment-next-emote-" + createStableId(displayName(group)));
    }

    private String createStableId(String value) {
        return value == null ? "group" : value.trim().toLowerCase()
            .replaceAll("[^a-z0-9\\u3400-\\u9fff]+", "-")
            .replaceAll("^-|-$", "");
    }
}
