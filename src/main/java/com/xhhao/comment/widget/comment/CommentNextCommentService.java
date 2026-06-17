package com.xhhao.comment.widget.comment;

import static run.halo.app.core.extension.content.Comment.CommentOwner.ownerIdentity;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.or;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;

@Service
@RequiredArgsConstructor
class CommentNextCommentService {

    private final ReactiveExtensionClient client;

    private final CommentNextAccessService accessService;

    private final CommentNextAuthorService authorService;

    private final CommentNextCommentMapper mapper;

    private final CommentNextCounterService counterService;

    Mono<ObjectNode> list(CommentNextCommentQuery query) {
        return Mono.zip(accessService.getCurrentAccess(), authorService.badgeContext())
            .flatMap(tuple -> {
                var access = tuple.getT1();
                var badgeContext = tuple.getT2();
                return commentPage(query, access)
                    .flatMap(comments -> toCommentPageNode(comments, query, access, badgeContext));
            });
    }

    Mono<ObjectNode> listReplies(CommentNextReplyQuery query) {
        return Mono.zip(accessService.getCurrentAccess(), authorService.badgeContext())
            .flatMap(tuple -> {
                var access = tuple.getT1();
                var badgeContext = tuple.getT2();
                return client.fetch(Comment.class, query.getCommentName())
                    .filter(comment -> isVisibleComment(comment, access))
                    .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
                    .flatMap(comment -> replyPageNode(comment, query.getPage(), query.getSize(),
                        access, badgeContext));
            });
    }

    private Mono<ObjectNode> toCommentPageNode(ListResult<Comment> comments,
        CommentNextCommentQuery query, CommentNextAccessContext access,
        CommentNextBadgeContext badgeContext) {
        return counterService.fetchUpvotes(
                CommentNextCounterService.COMMENTS_PLURAL,
                extensionNames(comments.getItems())
            )
            .flatMap(upvotes -> Mono.zip(
                    Flux.fromIterable(comments.getItems())
                        .flatMapSequential(comment -> toCommentNode(comment, query, access,
                            badgeContext, upvotes.getOrDefault(comment.getMetadata().getName(), 0)))
                        .collectList(),
                    firstCommentName(query.toRef(), access)
                )
                .map(tuple -> mapper.pageNode(comments, tuple.getT1(), tuple.getT2())));
    }

    private Mono<ObjectNode> toCommentNode(Comment comment, CommentNextCommentQuery query,
        CommentNextAccessContext access, CommentNextBadgeContext badgeContext, int upvotes) {
        return mapper.toCommentNode(comment, badgeContext, upvotes)
            .flatMap(commentNode -> {
                if (!query.isWithReplies()) {
                    return Mono.just(commentNode);
                }
                return replyPageNode(comment, 1, query.getReplySize(), access, badgeContext)
                    .doOnNext(repliesNode -> commentNode.set("replies", repliesNode))
                    .thenReturn(commentNode);
            });
    }

    private Mono<ObjectNode> replyPageNode(Comment comment, int page, int replySize,
        CommentNextAccessContext access, CommentNextBadgeContext badgeContext) {
        return client.list(Reply.class,
                reply -> isVisibleReply(reply, comment, access),
                replyComparator(),
                page,
                replySize
            )
            .flatMap(replies -> counterService.fetchUpvotes(
                    CommentNextCounterService.REPLIES_PLURAL,
                    extensionNames(replies.getItems())
                )
                .flatMap(upvotes -> Flux.fromIterable(replies.getItems())
                    .flatMapSequential(reply -> mapper.toReplyNode(reply, badgeContext,
                        upvotes.getOrDefault(reply.getMetadata().getName(), 0)))
                    .collectList()
                    .map(items -> mapper.pageNode(replies, items)))
            );
    }

    private Mono<ListResult<Comment>> commentPage(CommentNextCommentQuery query,
        CommentNextAccessContext access) {
        if (query.getSortMode() == CommentNextCommentQuery.SortMode.HOT) {
            return hotCommentPage(query, access);
        }

        return client.listBy(Comment.class, commentListOptions(query.toRef(), access),
            PageRequestImpl.of(query.getPage(), query.getSize(), commentSort(query))
        );
    }

    private Mono<ListResult<Comment>> hotCommentPage(CommentNextCommentQuery query,
        CommentNextAccessContext access) {
        return client.listAll(Comment.class, commentListOptions(query.toRef(), access),
                firstCommentSort())
            .collectList()
            .flatMap(comments -> counterService.fetchUpvotes(
                    CommentNextCounterService.COMMENTS_PLURAL,
                    extensionNames(comments)
                )
                .map(upvotes -> {
                    var sortedComments = comments.stream()
                        .sorted(hotCommentComparator(upvotes))
                        .toList();
                    return new ListResult<>(
                        query.getPage(),
                        query.getSize(),
                        sortedComments.size(),
                        ListResult.subList(sortedComments, query.getPage(), query.getSize())
                    );
                }));
    }

    private boolean isVisibleComment(Comment comment, CommentNextAccessContext access) {
        if (ExtensionUtil.isDeleted(comment)) {
            return false;
        }
        var spec = comment.getSpec();
        if (spec == null) {
            return false;
        }
        if (access.canViewAll() || isOwner(spec.getOwner(), access.username())) {
            return true;
        }
        return Boolean.TRUE.equals(spec.getApproved()) && !Boolean.TRUE.equals(spec.getHidden());
    }

    private ListOptions commentListOptions(Ref ref, CommentNextAccessContext access) {
        var builder = ListOptions.builder()
            .andQuery(isNull("metadata.deletionTimestamp"))
            .andQuery(equal("spec.subjectRef", Comment.toSubjectRefKey(ref)));
        var visibleQuery = and(
            equal("spec.hidden", false),
            equal("spec.approved", true)
        );

        if (access.anonymous()) {
            builder.andQuery(visibleQuery);
        } else if (!access.canViewAll()) {
            builder.andQuery(or(
                equal("spec.owner", ownerIdentity(User.KIND, access.username())),
                visibleQuery
            ));
        }
        return builder.build();
    }

    private boolean isVisibleReply(Reply reply, Comment comment, CommentNextAccessContext access) {
        if (ExtensionUtil.isDeleted(reply)) {
            return false;
        }

        var spec = reply.getSpec();
        if (spec == null || !comment.getMetadata().getName().equals(spec.getCommentName())) {
            return false;
        }
        if (access.canViewAll() || isOwner(spec.getOwner(), access.username())) {
            return true;
        }
        return Boolean.TRUE.equals(spec.getApproved()) && !Boolean.TRUE.equals(spec.getHidden());
    }

    private boolean isOwner(Comment.CommentOwner owner, String username) {
        return owner != null
            && User.KIND.equals(owner.getKind())
            && StringUtils.hasText(username)
            && ownerIdentity(User.KIND, username).equals(ownerIdentity(owner.getKind(), owner.getName()));
    }

    private Sort commentSort(CommentNextCommentQuery query) {
        var creationTimeOrder = query.getSortMode() == CommentNextCommentQuery.SortMode.EARLIEST
            ? Sort.Order.asc("spec.creationTime")
            : Sort.Order.desc("spec.creationTime");

        return Sort.by(
            Sort.Order.desc("spec.top"),
            Sort.Order.asc("spec.priority"),
            creationTimeOrder,
            Sort.Order.asc("metadata.name")
        );
    }

    private Mono<String> firstCommentName(Ref ref, CommentNextAccessContext access) {
        return client.listBy(Comment.class, commentListOptions(ref, access),
                PageRequestImpl.of(1, 1, firstCommentSort())
            )
            .map(page -> page.getItems().stream()
                .findFirst()
                .map(comment -> comment.getMetadata().getName())
                .orElse("")
            );
    }

    private Sort firstCommentSort() {
        return Sort.by(
            Sort.Order.asc("spec.creationTime"),
            Sort.Order.asc("metadata.name")
        );
    }

    private Comparator<Reply> replyComparator() {
        return Comparator
            .comparing((Reply reply) -> creationTime(reply.getSpec()))
            .thenComparing(reply -> reply.getMetadata().getName());
    }

    private Comparator<Comment> hotCommentComparator(Map<String, Integer> upvotes) {
        return Comparator
            .comparingInt((Comment comment) ->
                upvotes.getOrDefault(comment.getMetadata().getName(), 0))
            .reversed()
            .thenComparing((Comment comment) -> creationTime(comment.getSpec()),
                Comparator.reverseOrder())
            .thenComparing(comment -> comment.getMetadata().getName());
    }

    private Collection<String> extensionNames(Collection<? extends run.halo.app.extension.Extension> items) {
        return items.stream()
            .map(item -> item.getMetadata().getName())
            .toList();
    }

    private Instant creationTime(Comment.BaseCommentSpec spec) {
        return spec.getCreationTime() == null ? Instant.EPOCH : spec.getCreationTime();
    }
}
