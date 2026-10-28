package com.xhhao.comment.widget.search;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
class CommentNextCommentEmailReplacementService {

    private final ReactiveExtensionClient client;

    Mono<CommentNextCommentEmailReplacementResult> replaceEmail(
        CommentNextCommentEmailReplacementRequest request
    ) {
        var oldEmail = normalizeEmail(request == null ? null : request.oldEmail());
        var newEmail = normalizeEmail(request == null ? null : request.newEmail());
        validate(oldEmail, newEmail);

        return commentTargets()
            .filter(target -> hasEmailOwner(target, oldEmail))
            .concatMap(target -> replaceOwner(target, newEmail))
            .count()
            .map(count -> new CommentNextCommentEmailReplacementResult(
                Math.toIntExact(count)
            ));
    }

    private Flux<AbstractExtension> commentTargets() {
        return Flux.concat(
            client.listAll(Comment.class, notDeletingOptions(), Sort.by("metadata.name"))
                .cast(AbstractExtension.class),
            client.listAll(Reply.class, notDeletingOptions(), Sort.by("metadata.name"))
                .cast(AbstractExtension.class)
        );
    }

    private Mono<AbstractExtension> replaceOwner(AbstractExtension target, String newEmail) {
        var owner = spec(target).getOwner();
        owner.setName(newEmail);
        updateEmailHash(owner, newEmail);
        return client.update(target).map(updated -> (AbstractExtension) updated);
    }

    private boolean hasEmailOwner(AbstractExtension target, String oldEmail) {
        var targetSpec = spec(target);
        if (targetSpec == null || targetSpec.getOwner() == null) {
            return false;
        }

        var owner = targetSpec.getOwner();
        return Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())
            && oldEmail.equalsIgnoreCase(normalizeEmail(owner.getName()));
    }

    private Comment.BaseCommentSpec spec(AbstractExtension target) {
        if (target instanceof Comment comment) {
            return comment.getSpec();
        }
        if (target instanceof Reply reply) {
            return reply.getSpec();
        }
        return null;
    }

    private void updateEmailHash(Comment.CommentOwner owner, String email) {
        var annotations = owner.getAnnotations() == null
            ? new LinkedHashMap<String, String>()
            : new LinkedHashMap<>(owner.getAnnotations());
        annotations.put(Comment.CommentOwner.EMAIL_HASH_ANNO, sha256(email));
        owner.setAnnotations(annotations);
    }

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }

    private void validate(String oldEmail, String newEmail) {
        if (!StringUtils.hasText(oldEmail) || !StringUtils.hasText(newEmail)) {
            throw badRequest("旧邮箱和新邮箱不能为空。");
        }
        if (oldEmail.length() > 64 || newEmail.length() > 64) {
            throw badRequest("邮箱长度不能超过 64 个字符。");
        }
        if (oldEmail.equalsIgnoreCase(newEmail)) {
            throw badRequest("新邮箱不能与旧邮箱相同。");
        }
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.strip() : "";
    }

    private String sha256(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(value.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception error) {
            throw new IllegalStateException("Failed to calculate email hash.", error);
        }
    }
}
