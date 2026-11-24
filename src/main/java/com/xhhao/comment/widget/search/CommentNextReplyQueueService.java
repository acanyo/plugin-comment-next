package com.xhhao.comment.widget.search;

import static java.nio.charset.StandardCharsets.UTF_8;
import static run.halo.app.core.extension.content.Comment.CommentOwner.ownerIdentity;
import static run.halo.app.extension.index.query.Queries.equal;

import com.xhhao.comment.utils.CommonUtils;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Post;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;
import run.halo.app.infra.AnonymousUserConst;

@Service
@RequiredArgsConstructor
class CommentNextReplyQueueService {

    private final ReactiveExtensionClient client;

    Flux<Comment> listComments(CommentNextCommentSearchQuery.ReplyQueue queue, String postName) {
        var options = publicOptions();
        if (StringUtils.hasText(postName)) {
            options.andQuery(equal("spec.subjectRef", Comment.toSubjectRefKey(Ref.of(postName, Post.GVK))));
        }
        return currentUsername().flatMapMany(username -> {
            var dismissed = dismissedComments(username);
            var replied = queue == CommentNextCommentSearchQuery.ReplyQueue.WAITING
                ? repliedComments(username)
                : Mono.just(Set.<String>of());
            return Mono.zip(dismissed, replied)
                .flatMapMany(state -> client.listAll(Comment.class, options.build(), Sort.by("metadata.name"))
                    .filter(comment -> eligible(comment, username))
                    .filter(comment -> {
                        var name = comment.getMetadata().getName();
                        return queue == CommentNextCommentSearchQuery.ReplyQueue.DISMISSED
                            ? state.getT1().contains(name)
                            : !state.getT1().contains(name) && !state.getT2().contains(name);
                    }));
        });
    }

    Mono<Void> setDismissed(String commentName, boolean dismissed) {
        return currentUsername().flatMap(username -> client.fetch(Comment.class, commentName)
            .filter(comment -> eligible(comment, username))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(comment -> saveEntry(username, commentName, dismissed)));
    }

    private Mono<Set<String>> dismissedComments(String username) {
        var options = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(equal("spec.username", username))
            .build();
        return client.listAll(CommentNextReplyQueueEntry.class, options, Sort.by("metadata.name"))
            .mapNotNull(CommentNextReplyQueueEntry::getSpec)
            .filter(spec -> spec.isDismissed() && username.equals(spec.getUsername()))
            .mapNotNull(CommentNextReplyQueueEntry.Spec::getCommentName)
            .collect(Collectors.toSet());
    }

    private Mono<Set<String>> repliedComments(String username) {
        var options = publicOptions().andQuery(equal("spec.owner", ownerIdentity(User.KIND, username)));
        return client.listAll(Reply.class, options.build(), Sort.by("metadata.name"))
            .filter(reply -> !ExtensionUtil.isDeleted(reply))
            .mapNotNull(Reply::getSpec)
            .filter(spec -> isPublic(spec) && isWrittenBy(spec, username))
            .filter(spec -> !StringUtils.hasText(spec.getQuoteReply()))
            .mapNotNull(Reply.ReplySpec::getCommentName)
            .collect(Collectors.toSet());
    }

    private Mono<Void> saveEntry(String username, String commentName, boolean dismissed) {
        var name = UUID.nameUUIDFromBytes((username + "\0" + commentName).getBytes(UTF_8)).toString();
        return Mono.defer(() -> client.fetch(CommentNextReplyQueueEntry.class, name)
                .switchIfEmpty(Mono.defer(() -> dismissed
                    ? createEntry(name, username, commentName)
                    : Mono.empty()))
                .filter(entry -> entry.getSpec().isDismissed() != dismissed)
                .flatMap(entry -> {
                    entry.getSpec().setDismissed(dismissed);
                    return client.update(entry);
                }))
            .retryWhen(Retry.max(3).filter(OptimisticLockingFailureException.class::isInstance))
            .then();
    }

    private Mono<CommentNextReplyQueueEntry> createEntry(
        String name, String username, String commentName) {
        var entry = new CommentNextReplyQueueEntry();
        var metadata = new Metadata();
        metadata.setName(name);
        entry.setMetadata(metadata);
        entry.getSpec().setUsername(username);
        entry.getSpec().setCommentName(commentName);
        entry.getSpec().setDismissed(true);
        return client.create(entry)
            .onErrorResume(error -> client.fetch(CommentNextReplyQueueEntry.class, name)
                .switchIfEmpty(Mono.error(error)));
    }

    private static Mono<String> currentUsername() {
        return CommonUtils.getCurrentUserName()
            .filter(username -> !AnonymousUserConst.isAnonymousUser(username))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN)));
    }

    private static boolean eligible(Comment comment, String username) {
        return !ExtensionUtil.isDeleted(comment)
            && isPublic(comment.getSpec())
            && !isWrittenBy(comment.getSpec(), username);
    }

    private static boolean isPublic(Comment.BaseCommentSpec spec) {
        return spec != null && Boolean.TRUE.equals(spec.getApproved()) && !Boolean.TRUE.equals(spec.getHidden());
    }

    private static boolean isWrittenBy(Comment.BaseCommentSpec spec, String username) {
        var owner = spec.getOwner();
        return owner != null && User.KIND.equals(owner.getKind()) && username.equals(owner.getName());
    }

    private static ListOptions.ListOptionsBuilder publicOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(equal("spec.approved", true))
            .andQuery(equal("spec.hidden", false));
    }
}
