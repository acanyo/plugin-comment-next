package com.xhhao.comment.widget.style;

import com.xhhao.comment.widget.style.CommentNextStyleProfile.Spec;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Service
@RequiredArgsConstructor
public class CommentNextStyleStore {

    private static final String PROFILE_NAME = "comment-next-style";
    private static final Spec DEFAULT = new Spec(null, null, null);
    private final ReactiveExtensionClient client;

    public Mono<CommentNextStyle> published() {
        return current().map(state -> state.design().published());
    }

    Mono<State> current() {
        return fetch().map(this::view).defaultIfEmpty(new State(-1, DEFAULT));
    }

    Mono<State> change(long revision, Operation operation, CommentNextStyle draft) {
        return fetch().defaultIfEmpty(newProfile()).flatMap(profile -> {
            var current = view(profile);
            if (current.revision() != revision) {
                return Mono.error(conflict());
            }
            var next = nextDesign(current.design(), operation, draft);
            if (next.equals(current.design())) {
                return Mono.just(current);
            }
            profile.setSpec(next);
            return (revision == -1 ? client.create(profile) : client.update(profile)).map(this::view);
        }).onErrorMap(error ->
            error instanceof OptimisticLockingFailureException || error instanceof DuplicateKeyException
                || (error instanceof ResponseStatusException status && status.getStatusCode() == HttpStatus.CONFLICT),
            error -> conflict());
    }

    private Mono<CommentNextStyleProfile> fetch() {
        return client.fetch(CommentNextStyleProfile.class, PROFILE_NAME);
    }

    private static Spec nextDesign(Spec current, Operation operation, CommentNextStyle draft) {
        return switch (operation) {
            case SAVE -> new Spec(draft, current.published(), current.previous());
            case PUBLISH -> new Spec(draft, draft,
                draft.equals(current.published()) ? current.previous() : current.published());
            case ROLLBACK -> current.previous() == null ? current
                : new Spec(current.draft(), current.previous(), current.published());
        };
    }

    private static CommentNextStyleProfile newProfile() {
        var profile = new CommentNextStyleProfile();
        var metadata = new Metadata();
        metadata.setName(PROFILE_NAME);
        profile.setMetadata(metadata);
        profile.setSpec(DEFAULT);
        return profile;
    }

    private State view(CommentNextStyleProfile profile) {
        var version = profile.getMetadata().getVersion();
        return new State(version == null ? -1 : version,
            profile.getSpec() == null ? DEFAULT : profile.getSpec());
    }

    private static ResponseStatusException conflict() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
            "样式已在其他窗口更新，请重新读取后再保存。");
    }

    record State(long revision, Spec design) {
    }

    enum Operation {
        SAVE, PUBLISH, ROLLBACK
    }
}
