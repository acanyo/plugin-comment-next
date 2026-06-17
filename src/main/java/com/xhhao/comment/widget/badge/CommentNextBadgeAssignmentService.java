package com.xhhao.comment.widget.badge;

import static run.halo.app.extension.index.query.Queries.equal;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class CommentNextBadgeAssignmentService {

    private final ReactiveExtensionClient client;

    public Mono<List<CommentNextBadgeAssignment>> listEnabledAssignments() {
        var options = ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(equal("spec.enabled", true))
            .build();

        return client.listAll(CommentNextBadgeAssignment.class, options, Sort.by("metadata.name"))
            .collectList();
    }
}
