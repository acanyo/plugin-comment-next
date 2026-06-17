package com.xhhao.comment.widget.badge;

import com.xhhao.comment.widget.comment.CommentNextBadge;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

@Component
@RequiredArgsConstructor
public class CommentNextBadgeProfileService {

    private final ReactiveExtensionClient client;

    public Mono<CommentNextBadgeProfileSnapshot> fetchSnapshot(CommentNextBadgeIdentity identity) {
        return fetch(identity)
            .map(this::toSnapshot)
            .defaultIfEmpty(CommentNextBadgeProfileSnapshot.empty());
    }

    public Flux<CommentNextBadgeProfile> listAll() {
        return client.listAll(
            CommentNextBadgeProfile.class,
            notDeletingOptions(),
            Sort.by("metadata.name")
        );
    }

    public Mono<Boolean> syncProfile(CommentNextBadgeIdentity identity, long activeCommentCount,
        List<CommentNextBadge> badges) {
        var normalizedBadges = badges == null ? List.<CommentNextBadge>of() : List.copyOf(badges);
        var nextCount = Math.max(0, activeCommentCount);

        return fetch(identity)
            .flatMap(profile -> {
                if (nextCount == 0 && normalizedBadges.isEmpty()) {
                    return client.delete(profile).thenReturn(true).onErrorReturn(false);
                }
                if (sameSpec(profile, identity, nextCount, normalizedBadges)) {
                    return Mono.just(false);
                }
                applySpec(profile, identity, nextCount, normalizedBadges);
                return client.update(profile).thenReturn(true).onErrorReturn(false);
            })
            .switchIfEmpty(Mono.defer(() -> {
                if (nextCount == 0 && normalizedBadges.isEmpty()) {
                    return Mono.just(false);
                }
                return client.create(newProfile(identity, nextCount, normalizedBadges))
                    .thenReturn(true)
                    .onErrorReturn(false);
            }));
    }

    public static Optional<CommentNextBadgeIdentity> identityOf(CommentNextBadgeProfile profile) {
        var spec = profile.getSpec();
        if (spec == null) {
            return Optional.empty();
        }
        return CommentNextBadgeIdentity.of(spec.getIdentityType(), spec.getIdentity());
    }

    private Mono<CommentNextBadgeProfile> fetch(CommentNextBadgeIdentity identity) {
        return client.fetch(CommentNextBadgeProfile.class, identity.profileName());
    }

    private CommentNextBadgeProfileSnapshot toSnapshot(CommentNextBadgeProfile profile) {
        var spec = profile.getSpec();
        if (spec == null) {
            return CommentNextBadgeProfileSnapshot.empty();
        }
        return new CommentNextBadgeProfileSnapshot(spec.getActiveCommentCount(), spec.getBadges());
    }

    private CommentNextBadgeProfile newProfile(CommentNextBadgeIdentity identity,
        long activeCommentCount, List<CommentNextBadge> badges) {
        var profile = new CommentNextBadgeProfile();
        var metadata = new Metadata();
        metadata.setName(identity.profileName());
        profile.setMetadata(metadata);
        profile.setApiVersion(CommentNextBadgeProfile.GROUP + "/" + CommentNextBadgeProfile.VERSION);
        profile.setKind(CommentNextBadgeProfile.KIND);
        applySpec(profile, identity, activeCommentCount, badges);
        return profile;
    }

    private void applySpec(CommentNextBadgeProfile profile, CommentNextBadgeIdentity identity,
        long activeCommentCount, List<CommentNextBadge> badges) {
        var spec = profile.getSpec() == null ? new CommentNextBadgeProfile.Spec() : profile.getSpec();
        spec.setIdentityType(identity.type());
        spec.setIdentity(identity.identity());
        spec.setActiveCommentCount(Math.max(0, activeCommentCount));
        spec.setBadges(badges);
        profile.setSpec(spec);
    }

    private boolean sameSpec(CommentNextBadgeProfile profile, CommentNextBadgeIdentity identity,
        long activeCommentCount, List<CommentNextBadge> badges) {
        var spec = profile.getSpec();
        return spec != null
            && identity.type().equals(spec.getIdentityType())
            && identity.identity().equals(spec.getIdentity())
            && spec.getActiveCommentCount() == activeCommentCount
            && spec.getBadges().equals(badges);
    }

    private ListOptions notDeletingOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .build();
    }
}
