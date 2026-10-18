package com.xhhao.comment.widget.comment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static run.halo.app.extension.index.query.Queries.and;
import static run.halo.app.extension.index.query.Queries.equal;
import static run.halo.app.extension.index.query.Queries.isNull;
import static run.halo.app.extension.index.query.Queries.or;

import com.xhhao.comment.widget.interactionplus.AuthorIdentityLoader;
import com.xhhao.comment.widget.iplocation.CommentNextIpLocationService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.index.query.Condition;
import run.halo.app.infra.AnonymousUserConst;

class CommentNextCommentListTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final CommentNextAccessService access = mock(CommentNextAccessService.class);
    private final CommentNextAuthorService authors = mock(CommentNextAuthorService.class);
    private final CommentNextIpLocationService locations = mock(CommentNextIpLocationService.class);
    private final CommentNextCounterService counters = mock(CommentNextCounterService.class);
    private final CommentNextCommentService service = new CommentNextCommentService(client, access,
        authors, new CommentNextCommentMapper(authors, locations), counters);

    private final Comment pin = comment("pin", true);
    private final List<Comment> regular = List.of(comment("one", false), comment("two", null),
        comment("three", false));

    @BeforeEach
    void setUp() {
        when(access.getCurrentAccess()).thenReturn(Mono.just(
            new CommentNextAccessContext(AnonymousUserConst.PRINCIPAL, false)));
        when(authors.badgeContext()).thenReturn(Mono.just(new CommentNextBadgeContext(null,
            AuthorIdentityLoader.disabled())));
        when(authors.resolve(any(), any())).thenReturn(Mono.just(
            new CommentNextAuthor("reader", "Reader", "", "Email", "anonymous", 0, List.of(), null)));
        when(locations.resolve(any())).thenReturn(Mono.just(""));
        when(counters.fetchUpvotes(any(), anyCollection()))
            .thenReturn(Mono.just(Map.of("pin", 100, "one", 3, "two", 2, "three", 1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"latest", "earliest", "hot"})
    void pinsDoNotConsumeNormalPageSlotsOrCounts(String sort) {
        stubLists(publicVisibility(), regular);

        var result = service.list(query(sort, 2)).block();

        assertEquals(3, result.path("total").asInt());
        assertEquals(2, result.path("totalPages").asInt());
        assertEquals(1, result.path("items").size());
        assertEquals("three", result.path("items").get(0).path("metadata").path("name").asText());
        assertEquals(1, result.path("pinnedItems").size());
        assertEquals("pin", result.path("pinnedItems").get(0).path("metadata").path("name").asText());
        assertEquals(100, result.path("pinnedItems").get(0).path("stats").path("upvote").asInt());
        assertEquals("pin", result.path("firstCommentName").asText());
    }

    @Test
    void pinnedOnlyPageHasNoNormalCommentsOrPhantomPages() {
        stubLists(publicVisibility(), List.of());
        var result = service.list(query("latest", 1)).block();

        assertEquals(0, result.path("total").asInt());
        assertEquals(0, result.path("items").size());
        assertEquals(1, result.path("pinnedItems").size());
        assertEquals(false, result.path("hasNext").asBoolean());
    }

    @Test
    void pinnedQueryKeepsOwnerAndPublicVisibilityRestrictions() {
        when(access.getCurrentAccess()).thenReturn(Mono.just(new CommentNextAccessContext("reader", false)));
        stubLists(or(equal("spec.owner", Comment.CommentOwner.ownerIdentity(User.KIND, "reader")),
            publicVisibility()), regular);

        var result = service.list(query("latest", 1)).block();
        assertEquals(2, result.path("items").size());
        assertEquals(1, result.path("pinnedItems").size());
    }

    @Test
    void moderatorsKeepTheirExistingVisibilityForBothSections() {
        when(access.getCurrentAccess()).thenReturn(Mono.just(new CommentNextAccessContext("admin", true)));
        stubLists(null, regular);

        var result = service.list(query("latest", 1)).block();
        assertEquals(2, result.path("items").size());
        assertEquals(1, result.path("pinnedItems").size());
    }

    private void stubLists(Condition visibility, List<Comment> ordinary) {
        var all = ListOptions.builder()
            .andQuery(isNull("metadata.deletionTimestamp"))
            .andQuery(equal("spec.subjectRef", "content.halo.run/Post/post"));
        // Halo indexes an omitted top field as false; null queries on this index are invalid.
        var normal = ListOptions.builder(all.build())
            .andQuery(equal("spec.top", false));
        var pinned = ListOptions.builder(all.build()).andQuery(equal("spec.top", true));
        var first = ListOptions.builder(all.build());
        if (visibility != null) {
            normal.andQuery(visibility);
            pinned.andQuery(visibility);
            first.andQuery(visibility);
        }
        var normalQuery = normal.build();
        var pinnedQuery = pinned.build();
        var firstQuery = first.build();

        when(client.listBy(eq(Comment.class), argThat(normalQuery::equals), any()))
            .thenAnswer(invocation -> {
                PageRequest page = invocation.getArgument(2);
                return Mono.just(new ListResult<>(page.getPageNumber(), page.getPageSize(),
                    ordinary.size(), ListResult.subList(ordinary, page.getPageNumber(), page.getPageSize())));
            });
        when(client.listBy(eq(Comment.class), argThat(firstQuery::equals), any()))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 1, List.of(pin))));
        when(client.listAll(eq(Comment.class), argThat(pinnedQuery::equals), any()))
            .thenReturn(Flux.just(pin));
        when(client.listAll(eq(Comment.class), argThat(normalQuery::equals), any()))
            .thenReturn(Flux.fromIterable(ordinary));
    }

    private Condition publicVisibility() {
        return and(equal("spec.hidden", false), equal("spec.approved", true));
    }

    private CommentNextCommentQuery query(String sort, int page) {
        return new CommentNextCommentQuery(MockServerRequest.builder()
            .queryParam("group", "content.halo.run").queryParam("kind", "Post")
            .queryParam("version", "v1alpha1").queryParam("name", "post")
            .queryParam("sort", sort).queryParam("page", Integer.toString(page))
            .queryParam("size", "2").queryParam("withReplies", "false").build());
    }

    private Comment comment(String name, Boolean top) {
        var comment = new Comment();
        var metadata = new Metadata();
        metadata.setName(name);
        comment.setMetadata(metadata);
        var spec = new Comment.CommentSpec();
        spec.setTop(top);
        spec.setApproved(true);
        spec.setHidden(false);
        spec.setCreationTime(Instant.parse("2026-10-18T00:00:00Z"));
        var owner = new Comment.CommentOwner();
        owner.setKind("Email");
        owner.setName("reader@example.com");
        spec.setOwner(owner);
        comment.setSpec(spec);
        return comment;
    }
}
