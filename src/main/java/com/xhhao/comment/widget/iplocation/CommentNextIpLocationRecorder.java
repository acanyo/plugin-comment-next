package com.xhhao.comment.widget.iplocation;

import java.time.Duration;
import java.util.HashMap;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Component
@RequiredArgsConstructor
class CommentNextIpLocationRecorder {

    private final ReactiveExtensionClient client;
    private final CommentNextIpLocationService locations;

    <E extends AbstractExtension> Mono<Outcome> record(Class<E> type, String name,
        Function<E, ? extends Comment.BaseCommentSpec> specOf) {
        return Mono.defer(() -> client.fetch(type, name))
            .filter(this::needsLocation)
            .mapNotNull(specOf)
            .mapNotNull(Comment.BaseCommentSpec::getIpAddress)
            .filter(StringUtils::hasText)
            .flatMap(ip -> locations.resolve(ip)
                .defaultIfEmpty("")
                .flatMap(location -> {
                    if (!StringUtils.hasText(location)) {
                        return Mono.just(Outcome.UNRESOLVED);
                    }
                    return save(type, name, specOf, ip, location)
                        .map(saved -> Outcome.SAVED)
                        .defaultIfEmpty(Outcome.SKIPPED);
                }))
            .defaultIfEmpty(Outcome.SKIPPED)
            .timeout(Duration.ofSeconds(30))
            .onErrorResume(error -> {
                log.warn("Could not record IP location for {} {} ({})", type.getSimpleName(),
                    name, error.getClass().getSimpleName());
                return Mono.just(Outcome.FAILED);
            });
    }

    private <E extends AbstractExtension> Mono<E> save(Class<E> type, String name,
        Function<E, ? extends Comment.BaseCommentSpec> specOf, String ip, String location) {
        return Mono.defer(() -> client.fetch(type, name))
            .filter(this::needsLocation)
            .filter(extension -> {
                var spec = specOf.apply(extension);
                return spec != null && ip.equals(spec.getIpAddress());
            })
            .flatMap(extension -> {
                var annotations = new HashMap<>(MetadataUtil.nullSafeAnnotations(extension));
                annotations.put(CommentNextIpLocationAnnotation.NAME, location);
                extension.getMetadata().setAnnotations(annotations);
                return client.update(extension);
            })
            .retryWhen(Retry.max(3).filter(OptimisticLockingFailureException.class::isInstance));
    }

    boolean needsLocation(AbstractExtension extension) {
        return extension.getMetadata() != null
            && !ExtensionUtil.isDeleted(extension)
            && CommentNextIpLocationAnnotation.read(extension.getMetadata()).isEmpty();
    }

    enum Outcome {
        SAVED, SKIPPED, UNRESOLVED, FAILED
    }
}
