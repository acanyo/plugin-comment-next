package com.xhhao.comment.widget.comment;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.snippet.CommentNextSnippetAnchor;
import com.xhhao.comment.widget.snippet.CommentNextSnippetCodec;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;

@Component
@RequiredArgsConstructor
class CommentNextSnippetService {
    private final CommentNextCommentService comments;
    private final CommentNextAuthorService authors;
    private final CommentNextCommentMapper mapper;
    private final SettingConfigGetter settings;

    Mono<ObjectNode> list(CommentNextCommentQuery query, String key, int page) {
        return settings.getBasicConfig().flatMap(config -> {
            if (!config.isEnableSnippetComment()) return Mono.just(empty(key, page));
            var ref = query.toRef();
            if (!"content.halo.run".equals(ref.getGroup())
                || !("Post".equals(ref.getKind()) || "SinglePage".equals(ref.getKind()))) {
                return Mono.just(empty(key, page));
            }
            return comments.visibleComments(ref).flatMap(items -> {
                var groups = new LinkedHashMap<String, Thread>();
                for (var comment : items) {
                    CommentNextSnippetCodec.read(comment).ifPresent(anchor ->
                        groups.computeIfAbsent(CommentNextSnippetCodec.key(anchor),
                            ignored -> new Thread(anchor, new ArrayList<>())).comments().add(comment));
                }
                if (key.isBlank()) {
                    var result = JsonUtils.createObjectMapper().createObjectNode();
                    var nodes = result.putArray("threads");
                    groups.forEach((id, thread) -> {
                        var node = nodes.addObject();
                        node.put("key", id);
                        node.set("anchor", JsonUtils.createObjectMapper().valueToTree(thread.anchor()));
                        node.put("total", thread.comments().size());
                    });
                    return Mono.just(result);
                }
                var keys = new java.util.HashSet<>(java.util.Arrays.asList(key.split(",")));
                if (keys.size() > 100) return Mono.just(empty(key, page));
                var selected = new ArrayList<Comment>();
                groups.forEach((id, thread) -> { if (keys.contains(id)) selected.addAll(thread.comments()); });
                if (selected.isEmpty()) return Mono.just(empty(key, page));
                selected.sort(java.util.Comparator.comparing(comment ->
                    java.util.Optional.ofNullable(comment.getSpec().getCreationTime()).orElse(java.time.Instant.EPOCH)));
                var all = selected.reversed();
                int from = Math.min(all.size(), (page - 1) * 5);
                int to = Math.min(all.size(), from + 5);
                return authors.badgeContext().flatMap(context ->
                    Flux.fromIterable(all.subList(from, to))
                        .concatMap(comment -> mapper.toCommentNode(comment, context, 0))
                        .collectList().map(nodes -> {
                            var result = empty(key, page);
                            result.put("total", all.size());
                            result.put("hasNext", to < all.size());
                            var array = result.putArray("items");
                            nodes.forEach(array::add);
                            return result;
                        }));
            });
        });
    }

    private ObjectNode empty(String key, int page) {
        var result = JsonUtils.createObjectMapper().createObjectNode();
        if (key.isBlank()) result.putArray("threads");
        else {
            result.putArray("items");
            result.put("total", 0);
            result.put("page", page);
            result.put("hasNext", false);
        }
        return result;
    }

    private record Thread(CommentNextSnippetAnchor anchor, List<Comment> comments) {
    }
}
