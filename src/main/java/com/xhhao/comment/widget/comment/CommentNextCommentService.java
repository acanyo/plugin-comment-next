package com.xhhao.comment.widget.comment;

import static run.halo.app.core.extension.content.Comment.CommentOwner.ownerIdentity;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.or;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.HtmlUtils;
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

    Mono<List<Comment>> visibleComments(Ref ref) {
        return accessService.getCurrentAccess().flatMap(access ->
            client.listAll(Comment.class, commentListOptions(ref, access), firstCommentSort())
                .filter(comment -> isVisibleComment(comment, access))
                .collectList());
    }

    Mono<ObjectNode> list(CommentNextCommentQuery query) {
        return Mono.zip(accessService.getCurrentAccess(), authorService.badgeContext())
            .flatMap(tuple -> {
                var access = tuple.getT1();
                var badgeContext = tuple.getT2();
                var pinned = query.separatesPinned() ? pinnedComments(query, access)
                    : Mono.just(List.<Comment>of());
                return Mono.zip(commentPage(query, access), pinned)
                    .flatMap(pages -> toCommentPageNode(pages.getT1(), pages.getT2(), query,
                        access, badgeContext));
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

    Mono<CommentNextCommentLocation> locate(CommentNextCommentLocationQuery query) {
        return accessService.getCurrentAccess().flatMap(access -> {
            if (query.target() == CommentNextCommentLocationQuery.Target.COMMENT) {
                return visibleComment(query.targetName(), query.subject(), access)
                    .flatMap(comment -> locateCommentPage(comment, query, access))
                    .map(page -> new CommentNextCommentLocation(query.targetName(), page, null));
            }
            return client.fetch(Reply.class, query.targetName())
                .filter(reply -> reply.getSpec() != null
                    && StringUtils.hasText(reply.getSpec().getCommentName()))
                .flatMap(reply -> visibleComment(reply.getSpec().getCommentName(), query.subject(), access)
                    .filter(comment -> isVisibleReply(reply, comment, access)))
                .flatMap(comment -> Mono.zip(
                    locateCommentPage(comment, query, access),
                    locateReplyPage(comment, query.targetName(), query.replySize(), access)
                ).map(pages -> new CommentNextCommentLocation(comment.getMetadata().getName(),
                    pages.getT1(), pages.getT2())));
        });
    }

    private Mono<Comment> visibleComment(String name, Ref subject, CommentNextAccessContext access) {
        return client.fetch(Comment.class, name)
            .filter(comment -> isVisibleComment(comment, access))
            .filter(comment -> matchesSubject(comment.getSpec().getSubjectRef(), subject));
    }

    private Mono<Integer> locateCommentPage(Comment comment, CommentNextCommentLocationQuery query,
        CommentNextAccessContext access) {
        if (query.separatePinned() && isTop(comment.getSpec())) {
            return Mono.just(1);
        }
        var options = paginatedCommentOptions(query.subject(), access, query.separatePinned());
        var names = query.sort() == CommentNextCommentQuery.SortMode.HOT
            ? hotComments(options, query.separatePinned()).flatMapMany(Flux::fromIterable)
                .map(item -> item.getMetadata().getName())
            : client.listAllNames(Comment.class, options, commentSort(query.sort(), query.separatePinned()));
        return pageContaining(names, comment.getMetadata().getName(), query.size());
    }

    private Mono<Integer> locateReplyPage(Comment comment, String name, int size,
        CommentNextAccessContext access) {
        var options = ListOptions.builder(notDeletingOptions())
            .andQuery(equal("spec.commentName", comment.getMetadata().getName())).build();
        var names = client.listAll(Reply.class, options, Sort.by("metadata.name"))
            .filter(reply -> isVisibleReply(reply, comment, access))
            .sort(replyComparator())
            .map(reply -> reply.getMetadata().getName());
        return pageContaining(names, name, size);
    }

    private Mono<Integer> pageContaining(Flux<String> names, String name, int size) {
        return names.index().filter(entry -> name.equals(entry.getT2())).next()
            .map(entry -> Math.toIntExact(entry.getT1() / size + 1));
    }

    Mono<ObjectNode> listFeatured(CommentNextFeaturedCommentQuery query) {
        return Mono.zip(accessService.getCurrentAccess(), authorService.badgeContext())
            .flatMap(tuple -> {
                var access = tuple.getT1();
                var badgeContext = tuple.getT2();
                return featuredItems(query, access, badgeContext)
                    .filter(item -> matchesKeyword(item, query.keyword()))
                    .sort(featuredComparator())
                    .collectList()
                    .map(items -> featuredPageNode(items, query));
            });
    }

    private Flux<FeaturedCommentItem> featuredItems(CommentNextFeaturedCommentQuery query,
        CommentNextAccessContext access, CommentNextBadgeContext badgeContext) {
        return switch (query.target()) {
            case COMMENT -> featuredComments(query, access, badgeContext);
            case REPLY -> featuredReplies(query, access, badgeContext);
            case ALL -> Flux.merge(
                featuredComments(query, access, badgeContext),
                featuredReplies(query, access, badgeContext)
            );
        };
    }

    private Flux<FeaturedCommentItem> featuredComments(CommentNextFeaturedCommentQuery query,
        CommentNextAccessContext access, CommentNextBadgeContext badgeContext) {
        return client.listAll(Comment.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(comment -> isFeatured(comment.getMetadata().getAnnotations()))
            .filter(comment -> isVisibleComment(comment, access))
            .filter(comment -> matchesSubject(comment.getSpec().getSubjectRef(), query))
            .collectList()
            .flatMapMany(comments -> counterService.fetchUpvotes(
                    CommentNextCounterService.COMMENTS_PLURAL,
                    extensionNames(comments)
                )
                .flatMapMany(upvotes -> Flux.fromIterable(comments)
                    .flatMapSequential(comment -> mapper.toCommentNode(
                            comment,
                            badgeContext,
                            upvotes.getOrDefault(comment.getMetadata().getName(), 0)
                        )
                        .map(node -> toFeaturedCommentItem(
                            node,
                            "comment",
                            "",
                            comment.getSpec().getSubjectRef(),
                            comment.getMetadata().getAnnotations(),
                            comment.getSpec()
                        ))
                    )
                )
            );
    }

    private Flux<FeaturedCommentItem> featuredReplies(CommentNextFeaturedCommentQuery query,
        CommentNextAccessContext access, CommentNextBadgeContext badgeContext) {
        return client.listAll(Comment.class, notDeletingOptions(), Sort.by("metadata.name"))
            .filter(comment -> isVisibleComment(comment, access))
            .collectMap(comment -> comment.getMetadata().getName())
            .flatMapMany(commentMap -> client.listAll(Reply.class, notDeletingOptions(),
                    Sort.by("metadata.name"))
                .filter(reply -> reply.getSpec() != null)
                .filter(reply -> isFeatured(reply.getMetadata().getAnnotations()))
                .filter(reply -> {
                    var comment = commentMap.get(reply.getSpec().getCommentName());
                    return comment != null
                        && isVisibleReply(reply, comment, access)
                        && matchesSubject(comment.getSpec().getSubjectRef(), query);
                })
                .collectList()
                .flatMapMany(replies -> counterService.fetchUpvotes(
                        CommentNextCounterService.REPLIES_PLURAL,
                        extensionNames(replies)
                    )
                    .flatMapMany(upvotes -> Flux.fromIterable(replies)
                        .flatMapSequential(reply -> {
                            var comment = commentMap.get(reply.getSpec().getCommentName());
                            return mapper.toReplyNode(
                                    reply,
                                    badgeContext,
                                    upvotes.getOrDefault(reply.getMetadata().getName(), 0)
                                )
                                .map(node -> toFeaturedCommentItem(
                                    node,
                                    "reply",
                                    reply.getSpec().getCommentName(),
                                    comment.getSpec().getSubjectRef(),
                                    reply.getMetadata().getAnnotations(),
                                    reply.getSpec()
                                ));
                        })
                    )
                )
            );
    }

    private Mono<ObjectNode> toCommentPageNode(ListResult<Comment> comments, List<Comment> pinned,
        CommentNextCommentQuery query, CommentNextAccessContext access,
        CommentNextBadgeContext badgeContext) {
        var displayedComments = new ArrayList<>(comments.getItems());
        displayedComments.addAll(pinned);
        return counterService.fetchUpvotes(
                CommentNextCounterService.COMMENTS_PLURAL,
                extensionNames(displayedComments)
            )
            .flatMap(upvotes -> Mono.zip(
                    commentNodes(comments.getItems(), query, access, badgeContext, upvotes),
                    commentNodes(pinned, query, access, badgeContext, upvotes),
                    firstCommentName(query.toRef(), access)
                )
                .map(tuple -> mapper.commentPageNode(comments, tuple.getT1(), tuple.getT2(),
                    tuple.getT3())));
    }

    private Mono<List<ObjectNode>> commentNodes(List<Comment> comments,
        CommentNextCommentQuery query, CommentNextAccessContext access,
        CommentNextBadgeContext badgeContext, Map<String, Integer> upvotes) {
        return Flux.fromIterable(comments)
            .flatMapSequential(comment -> toCommentNode(comment, query, access, badgeContext,
                upvotes.getOrDefault(comment.getMetadata().getName(), 0)))
            .collectList();
    }

    private Mono<List<Comment>> pinnedComments(CommentNextCommentQuery query,
        CommentNextAccessContext access) {
        return client.listAll(Comment.class, commentListOptions(query.toRef(), access, true),
                Sort.by(Sort.Order.asc("spec.priority"), Sort.Order.desc("spec.creationTime"),
                    Sort.Order.asc("metadata.name")))
            .collectList();
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
        var separatePinned = query.separatesPinned();
        var options = paginatedCommentOptions(query.toRef(), access, separatePinned);
        if (query.getSortMode() == CommentNextCommentQuery.SortMode.HOT) {
            return hotComments(options, separatePinned).map(comments -> new ListResult<>(
                query.getPage(), query.getSize(), comments.size(),
                ListResult.subList(comments, query.getPage(), query.getSize())
            ));
        }

        return client.listBy(Comment.class, options,
            PageRequestImpl.of(query.getPage(), query.getSize(),
                commentSort(query.getSortMode(), separatePinned))
        );
    }

    private Mono<List<Comment>> hotComments(ListOptions options, boolean separatePinned) {
        return client.listAll(Comment.class, options, firstCommentSort())
            .collectList()
            .flatMap(comments -> counterService.fetchUpvotes(
                    CommentNextCounterService.COMMENTS_PLURAL,
                    extensionNames(comments)
                )
                .map(upvotes -> comments.stream()
                    .sorted(hotCommentComparator(upvotes, separatePinned))
                    .toList()));
    }

    private boolean isVisibleComment(Comment comment, CommentNextAccessContext access) {
        if (ExtensionUtil.isDeleted(comment)) {
            return false;
        }
        var spec = comment.getSpec();
        if (spec == null) {
            return false;
        }
        if (access.canViewAll() || (!access.anonymous() && isOwner(spec.getOwner(), access.username()))) {
            return true;
        }
        return Boolean.TRUE.equals(spec.getApproved()) && !Boolean.TRUE.equals(spec.getHidden());
    }

    private ListOptions commentListOptions(Ref ref, CommentNextAccessContext access) {
        return commentListOptions(ref, access, null);
    }

    private ListOptions paginatedCommentOptions(Ref ref, CommentNextAccessContext access,
        boolean separatePinned) {
        return commentListOptions(ref, access, separatePinned ? Boolean.FALSE : null);
    }

    private ListOptions commentListOptions(Ref ref, CommentNextAccessContext access, Boolean top) {
        var builder = ListOptions.builder()
            .andQuery(isNull("metadata.deletionTimestamp"))
            .andQuery(equal("spec.subjectRef", Comment.toSubjectRefKey(ref)));
        if (top != null) {
            // Halo's non-nullable index maps an omitted top field to false.
            builder.andQuery(equal("spec.top", top));
        }
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

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }

    private boolean isVisibleReply(Reply reply, Comment comment, CommentNextAccessContext access) {
        if (ExtensionUtil.isDeleted(reply)) {
            return false;
        }

        var spec = reply.getSpec();
        if (spec == null || !comment.getMetadata().getName().equals(spec.getCommentName())) {
            return false;
        }
        if (access.canViewAll() || (!access.anonymous() && isOwner(spec.getOwner(), access.username()))) {
            return true;
        }
        return Boolean.TRUE.equals(spec.getApproved()) && !Boolean.TRUE.equals(spec.getHidden());
    }

    private boolean matchesSubject(Ref subjectRef, CommentNextFeaturedCommentQuery query) {
        return !query.hasSubject() || matchesSubject(subjectRef, query.toRef());
    }

    private boolean matchesSubject(Ref subjectRef, Ref expected) {
        return subjectRef != null && Comment.toSubjectRefKey(subjectRef)
            .equals(Comment.toSubjectRefKey(expected));
    }

    private boolean isOwner(Comment.CommentOwner owner, String username) {
        return owner != null
            && User.KIND.equals(owner.getKind())
            && StringUtils.hasText(username)
            && ownerIdentity(User.KIND, username).equals(ownerIdentity(owner.getKind(), owner.getName()));
    }

    private Sort commentSort(CommentNextCommentQuery.SortMode sort, boolean separatePinned) {
        var creationTimeOrder = sort == CommentNextCommentQuery.SortMode.EARLIEST
            ? Sort.Order.asc("spec.creationTime")
            : Sort.Order.desc("spec.creationTime");

        var chronologicalOrder = Sort.by(creationTimeOrder, Sort.Order.asc("metadata.name"));
        return separatePinned
            ? Sort.by(Sort.Order.desc("spec.top"), Sort.Order.asc("spec.priority")).and(chronologicalOrder)
            : chronologicalOrder;
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
            .comparing((Reply reply) -> !isTop(reply.getSpec()))
            .thenComparingInt(reply -> priority(reply.getSpec()))
            .thenComparing((Reply reply) -> creationTime(reply.getSpec()))
            .thenComparing(reply -> reply.getMetadata().getName());
    }

    private Comparator<Comment> hotCommentComparator(Map<String, Integer> upvotes,
        boolean separatePinned) {
        var popularityOrder = Comparator.comparingInt((Comment comment) ->
                upvotes.getOrDefault(comment.getMetadata().getName(), 0)).reversed()
            .thenComparing((Comment comment) -> creationTime(comment.getSpec()),
                Comparator.reverseOrder())
            .thenComparing(comment -> comment.getMetadata().getName());
        return separatePinned
            ? Comparator.comparingInt((Comment comment) -> priority(comment.getSpec()))
                .thenComparing(popularityOrder)
            : popularityOrder;
    }

    private Comparator<FeaturedCommentItem> featuredComparator() {
        return Comparator
            .comparing((FeaturedCommentItem item) -> !item.top())
            .thenComparingInt(FeaturedCommentItem::priority)
            .thenComparing(
                (FeaturedCommentItem item) -> safeInstant(item.featuredAt()),
                Comparator.reverseOrder()
            )
            .thenComparing(
                item -> safeInstant(item.creationTime()),
                Comparator.reverseOrder()
            )
            .thenComparing(FeaturedCommentItem::targetType)
            .thenComparing(FeaturedCommentItem::name);
    }

    private ObjectNode featuredPageNode(List<FeaturedCommentItem> items,
        CommentNextFeaturedCommentQuery query) {
        var page = Math.max(1, query.page());
        var size = Math.max(1, query.size());
        var pageItems = ListResult.subList(items, page, size);
        var pageResult = new ListResult<>(
            page,
            size,
            items.size(),
            pageItems
        );
        return mapper.pageNode(
            pageResult,
            pageItems.stream().map(FeaturedCommentItem::node).toList()
        );
    }

    private FeaturedCommentItem toFeaturedCommentItem(ObjectNode node,
        String targetType, String parentName, Ref subjectRef, Map<String, String> annotations,
        Comment.BaseCommentSpec spec) {
        var featuredAt = instant(text(annotations, CommentNextCommentAnnotations.FEATURED_AT));
        var subject = subjectRef == null ? "" : Comment.toSubjectRefKey(subjectRef);

        node.put("targetType", targetType);
        node.put("parentName", parentName);
        node.put("subject", subject);
        if (featuredAt != null) {
            node.put("featuredAt", featuredAt.toString());
        }

        return new FeaturedCommentItem(
            node,
            targetType,
            node.path("metadata").path("name").asText(""),
            parentName,
            subject,
            ownerDisplayName(spec.getOwner()),
            plainText(spec.getContent()),
            featuredAt,
            creationTime(spec),
            isTop(spec),
            priority(spec)
        );
    }

    private boolean matchesKeyword(FeaturedCommentItem item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        var normalizedKeyword = keyword.strip().toLowerCase();
        return contains(item.name(), normalizedKeyword)
            || contains(item.parentName(), normalizedKeyword)
            || contains(item.subject(), normalizedKeyword)
            || contains(item.authorName(), normalizedKeyword)
            || contains(item.content(), normalizedKeyword)
            || contains(item.targetType(), normalizedKeyword);
    }

    private boolean contains(String value, String keyword) {
        return StringUtils.hasText(value)
            && value.toLowerCase().contains(keyword);
    }

    private String ownerDisplayName(Comment.CommentOwner owner) {
        if (owner == null) {
            return "匿名用户";
        }
        if (StringUtils.hasText(owner.getDisplayName())) {
            return owner.getDisplayName().strip();
        }
        if (StringUtils.hasText(owner.getName())) {
            return owner.getName().strip();
        }
        return "匿名用户";
    }

    private String plainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        var withoutTags = html
            .replaceAll("(?is)<(script|style)[^>]*>.*?</\\1>", " ")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</p\\s*>", "\n")
            .replaceAll("<[^>]+>", " ");
        return HtmlUtils.htmlUnescape(withoutTags)
            .replaceAll("[\\t\\x0B\\f\\r ]+", " ")
            .replaceAll("\\n{3,}", "\n\n")
            .strip();
    }

    private String text(Map<String, String> annotations, String key) {
        return annotations == null ? "" : annotations.getOrDefault(key, "");
    }

    private boolean isFeatured(Map<String, String> annotations) {
        return annotations != null
            && Boolean.parseBoolean(annotations.get(CommentNextCommentAnnotations.FEATURED));
    }

    private Instant instant(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    private Instant safeInstant(Instant instant) {
        return instant == null ? Instant.EPOCH : instant;
    }

    private java.util.Collection<String> extensionNames(
        java.util.Collection<? extends run.halo.app.extension.Extension> items) {
        return items.stream()
            .map(item -> item.getMetadata().getName())
            .toList();
    }

    private Instant creationTime(Comment.BaseCommentSpec spec) {
        return spec == null || spec.getCreationTime() == null ? Instant.EPOCH : spec.getCreationTime();
    }

    private boolean isTop(Comment.BaseCommentSpec spec) {
        return spec != null && Boolean.TRUE.equals(spec.getTop());
    }

    private int priority(Comment.BaseCommentSpec spec) {
        return spec == null || spec.getPriority() == null ? 0 : spec.getPriority();
    }

    private record FeaturedCommentItem(
        ObjectNode node,
        String targetType,
        String name,
        String parentName,
        String subject,
        String authorName,
        String content,
        Instant featuredAt,
        Instant creationTime,
        boolean top,
        int priority
    ) {
    }
}
