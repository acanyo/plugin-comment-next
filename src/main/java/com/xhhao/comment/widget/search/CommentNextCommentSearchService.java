package com.xhhao.comment.widget.search;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
class CommentNextCommentSearchService {

    private final ReactiveExtensionClient client;

    private final CommentNextCommentSearchItemMapper mapper;

    Mono<CommentNextCommentSearchPage> search(CommentNextCommentSearchQuery query) {
        var criteria = query.criteria();
        return listUserEmails()
            .flatMap(userEmails -> searchItems(criteria, userEmails)
                .filter(criteria::matches)
                .sort(itemComparator())
                .collectList()
                .map(items -> CommentNextCommentSearchPage.from(
                    items,
                    query.page(),
                    query.size()
                ))
            );
    }

    private Mono<Map<String, Comment>> listComments() {
        return client.listAll(Comment.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(comment -> comment.getSpec() != null)
            .collectMap(comment -> comment.getMetadata().getName());
    }

    private Mono<Map<String, String>> listUserEmails() {
        return client.listAll(User.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(user -> user.getSpec() != null)
            .collectMap(
                user -> user.getMetadata().getName(),
                user -> text(user.getSpec().getEmail())
            );
    }

    private Flux<CommentNextCommentSearchItem> searchItems(
        CommentNextCommentSearchCriteria criteria,
        Map<String, String> userEmails
    ) {
        if (criteria.target() == CommentNextCommentSearchCriteria.Target.COMMENT) {
            return searchComments(userEmails);
        }

        return listComments()
            .flatMapMany(commentMap -> {
                var replies = searchReplies(commentMap, userEmails);
                if (criteria.target() == CommentNextCommentSearchCriteria.Target.REPLY) {
                    return replies;
                }
                var comments = Flux.fromIterable(commentMap.values())
                    .map(comment -> mapper.fromComment(
                        comment,
                        registeredEmail(comment.getSpec(), userEmails)
                    ));
                return Flux.concat(comments, replies);
            });
    }

    private Flux<CommentNextCommentSearchItem> searchComments(Map<String, String> userEmails) {
        return client.listAll(Comment.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(comment -> comment.getSpec() != null)
            .map(comment -> mapper.fromComment(
                comment,
                registeredEmail(comment.getSpec(), userEmails)
            ));
    }

    private Flux<CommentNextCommentSearchItem> searchReplies(
        Map<String, Comment> commentMap,
        Map<String, String> userEmails
    ) {
        return client.listAll(Reply.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(reply -> reply.getSpec() != null)
            .map(reply -> mapper.fromReply(
                reply,
                commentMap.get(reply.getSpec().getCommentName()),
                registeredEmail(reply.getSpec(), userEmails)
            ));
    }

    private Comparator<CommentNextCommentSearchItem> itemComparator() {
        return Comparator
            .comparing(
                (CommentNextCommentSearchItem item) -> safeInstant(item.creationTime()),
                Comparator.reverseOrder()
            )
            .thenComparing(CommentNextCommentSearchItem::targetType)
            .thenComparing(CommentNextCommentSearchItem::name);
    }

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }

    private Instant safeInstant(Instant value) {
        return value == null ? Instant.EPOCH : value;
    }

    private String registeredEmail(
        Comment.BaseCommentSpec spec,
        Map<String, String> userEmails
    ) {
        var owner = spec.getOwner();
        if (owner == null || !User.KIND.equals(owner.getKind())) {
            return "";
        }
        return userEmails.getOrDefault(owner.getName(), "");
    }

    private String text(String value) {
        return value == null ? "" : value;
    }
}
