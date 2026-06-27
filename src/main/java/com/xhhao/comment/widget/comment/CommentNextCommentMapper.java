package com.xhhao.comment.widget.comment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.comment.utils.JsonUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.MetadataOperator;

@Component
@RequiredArgsConstructor
class CommentNextCommentMapper {

    private final CommentNextAuthorService authorService;

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    Mono<ObjectNode> toCommentNode(Comment comment, CommentNextBadgeContext badgeContext,
        int upvotes) {
        var commentNode = objectMapper.createObjectNode();
        commentNode.set("metadata", objectMapper.valueToTree(comment.getMetadata()));
        commentNode.set("spec", sanitizedCommentSpec(comment.getSpec()));
        commentNode.set("status", objectMapper.valueToTree(comment.getStatus()));
        commentNode.set("stats", statsNode(upvotes));
        applyModerationState(commentNode, comment.getMetadata(), comment.getSpec());

        return authorService.resolve(comment.getSpec().getOwner(), badgeContext)
            .map(this::ownerNode)
            .doOnNext(ownerNode -> commentNode.set("owner", ownerNode))
            .thenReturn(commentNode);
    }

    Mono<ObjectNode> toReplyNode(Reply reply, CommentNextBadgeContext badgeContext,
        int upvotes) {
        var replyNode = objectMapper.createObjectNode();
        replyNode.set("metadata", objectMapper.valueToTree(reply.getMetadata()));
        replyNode.set("spec", sanitizedReplySpec(reply.getSpec()));
        replyNode.set("status", objectMapper.valueToTree(reply.getStatus()));
        replyNode.set("stats", statsNode(upvotes));
        applyModerationState(replyNode, reply.getMetadata(), reply.getSpec());

        return authorService.resolve(reply.getSpec().getOwner(), badgeContext)
            .map(this::ownerNode)
            .doOnNext(ownerNode -> replyNode.set("owner", ownerNode))
            .thenReturn(replyNode);
    }

    ObjectNode pageNode(ListResult<?> page, java.util.List<ObjectNode> items) {
        return pageNode(page, items, "");
    }

    ObjectNode pageNode(ListResult<?> page, java.util.List<ObjectNode> items,
        String firstCommentName) {
        var pageNode = objectMapper.createObjectNode();
        pageNode.put("page", page.getPage());
        pageNode.put("size", page.getSize());
        pageNode.put("total", page.getTotal());
        pageNode.put("totalPages", page.getTotalPages());
        pageNode.put("hasNext", page.hasNext());
        pageNode.put("hasPrevious", page.hasPrevious());
        pageNode.put("first", page.isFirst());
        pageNode.put("last", page.isLast());
        if (StringUtils.hasText(firstCommentName)) {
            pageNode.put("firstCommentName", firstCommentName);
        }
        ArrayNode itemNodes = pageNode.putArray("items");
        items.forEach(itemNodes::add);
        return pageNode;
    }

    private ObjectNode ownerNode(CommentNextAuthor author) {
        var ownerNode = objectMapper.createObjectNode();
        ownerNode.put("displayName", author.displayName());
        if (StringUtils.hasText(author.avatar())) {
            ownerNode.put("avatar", author.avatar());
        }
        if (StringUtils.hasText(author.kind())) {
            ownerNode.put("kind", author.kind());
        }
        if (StringUtils.hasText(author.role())) {
            ownerNode.put("role", author.role());
        }
        ownerNode.put("activeCommentCount", author.activeCommentCount());
        var badges = ownerNode.putArray("badges");
        author.badges().forEach(badge -> badges.add(objectMapper.valueToTree(badge)));
        return ownerNode;
    }

    private ObjectNode sanitizedCommentSpec(Comment.CommentSpec spec) {
        var specNode = objectMapper.<ObjectNode>valueToTree(spec);
        specNode.remove("ipAddress");
        specNode.remove("location");
        specNode.set("owner", sanitizedOwnerRef(spec.getOwner()));
        return specNode;
    }

    private ObjectNode sanitizedReplySpec(Reply.ReplySpec spec) {
        var specNode = objectMapper.<ObjectNode>valueToTree(spec);
        specNode.remove("ipAddress");
        specNode.remove("location");
        specNode.set("owner", sanitizedOwnerRef(spec.getOwner()));
        return specNode;
    }

    private ObjectNode sanitizedOwnerRef(Comment.CommentOwner owner) {
        var ownerNode = objectMapper.createObjectNode();
        if (owner == null) {
            ownerNode.put("name", "");
            ownerNode.put("kind", Comment.CommentOwner.KIND_EMAIL);
            return ownerNode;
        }

        ownerNode.put("name", "");
        ownerNode.put("displayName", owner.getDisplayName());
        ownerNode.put("kind", owner.getKind());

        var annotations = objectMapper.createObjectNode();
        var website = owner.getAnnotation(Comment.CommentOwner.WEBSITE_ANNO);
        if (StringUtils.hasText(website)) {
            annotations.put(Comment.CommentOwner.WEBSITE_ANNO, website);
        }
        if (Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind()) && StringUtils.hasText(owner.getName())) {
            annotations.put(Comment.CommentOwner.EMAIL_HASH_ANNO, sha256(owner.getName().toLowerCase()));
        }
        ownerNode.set("annotations", annotations);
        return ownerNode;
    }

    private ObjectNode statsNode(int upvotes) {
        var statsNode = objectMapper.createObjectNode();
        statsNode.put("upvote", Math.max(upvotes, 0));
        return statsNode;
    }

    private void applyModerationState(ObjectNode node, MetadataOperator metadata,
        Comment.BaseCommentSpec spec) {
        node.put("top", spec != null && Boolean.TRUE.equals(spec.getTop()));
        node.put("priority", spec == null || spec.getPriority() == null ? 0 : spec.getPriority());
        node.put("featured", isFeatured(metadata));
    }

    private boolean isFeatured(MetadataOperator metadata) {
        var annotations = metadata == null ? null : metadata.getAnnotations();
        return annotations != null
            && Boolean.parseBoolean(annotations.get(CommentNextCommentAnnotations.FEATURED));
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            var bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            return "";
        }
    }

}
