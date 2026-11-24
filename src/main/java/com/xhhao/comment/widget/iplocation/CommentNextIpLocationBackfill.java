package com.xhhao.comment.widget.iplocation;

import static run.halo.app.extension.index.query.Queries.greaterThan;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.iplocation.CommentNextIpLocationRecorder.Outcome;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.Comparator;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ReactiveExtensionClient;

@Slf4j
@Service
@RequiredArgsConstructor
class CommentNextIpLocationBackfill {

    private static final String NAME_FIELD = "metadata.name";
    private static final int PAGE_SIZE = 100;
    private static final Duration LOOKUP_PAUSE = Duration.ofMillis(200);
    private static final String DISABLED_MESSAGE = "请先保存并开启 IP 属地显示和在线属地查询。";

    private final ReactiveExtensionClient client;
    private final CommentNextIpLocationRecorder recorder;
    private final CommentNextIpLocationCache cache;
    private final SettingConfigGetter settings;
    private final Disposable.Swap task = Disposables.swap();
    private volatile Progress progress = new Progress(State.IDLE, 0, 0, 0, 0);

    Mono<Status> status() {
        return lookupEnabled().map(enabled -> new Status(enabled, progress));
    }

    Mono<Status> start() {
        return lookupEnabled().map(this::startIfIdle);
    }

    private synchronized Status startIfIdle(boolean enabled) {
        if (progress.state() == State.RUNNING) {
            return new Status(enabled, progress);
        }
        if (!enabled) {
            throw new ServerWebInputException(DISABLED_MESSAGE);
        }

        var started = new Progress(State.RUNNING, 0, 0, 0, 0);
        progress = started;
        task.update(Mono.defer(this::run)
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(null, this::finishWithError, () -> finish(State.COMPLETED)));
        return new Status(true, started);
    }

    private Mono<Void> run() {
        // Retry misses from earlier runs and keep the shared cache for this run.
        cache.clearMisses();
        return Flux.concat(scan(Comment.class, Comment::getSpec), scan(Reply.class, Reply::getSpec))
            .doOnNext(outcome -> progress = progress.count(outcome))
            .then();
    }

    private <E extends AbstractExtension> Flux<Outcome> scan(Class<E> type,
        Function<E, ? extends Comment.BaseCommentSpec> specOf) {
        return page(type, null)
            .expand(current -> {
                if (!current.hasNext()) {
                    return Mono.empty();
                }
                return current.getItems().stream()
                    .map(item -> item.getMetadata().getName())
                    .max(Comparator.naturalOrder())
                    .map(after -> page(type, after))
                    .orElseGet(Mono::empty);
            })
            .concatMap(current -> Flux.fromIterable(current.getItems())
                .concatMap(item -> {
                    if (!recorder.needsLocation(item)) {
                        return Mono.just(Outcome.SKIPPED);
                    }
                    return recorder.record(type, item.getMetadata().getName(), specOf)
                        .delayUntil(outcome -> outcome == Outcome.SKIPPED
                            ? Mono.empty() : Mono.delay(LOOKUP_PAUSE));
                }));
    }

    private <E extends AbstractExtension> Mono<ListResult<E>> page(Class<E> type, String after) {
        return Mono.defer(() -> lookupEnabled().flatMap(enabled -> {
            if (!enabled) {
                return Mono.error(new LookupDisabledException());
            }
            var options = ListOptions.builder().andQuery(ExtensionUtil.notDeleting());
            if (after != null) {
                options.andQuery(greaterThan(NAME_FIELD, after));
            }
            return client.listBy(type, options.build(), PageRequestImpl.of(1, PAGE_SIZE, Sort.by(NAME_FIELD)));
        }));
    }

    private Mono<Boolean> lookupEnabled() {
        return settings.getIpLocationConfig()
            .map(config -> config.isEnabled() && config.isOnlineLookupEnabled())
            .defaultIfEmpty(false);
    }

    private void finishWithError(Throwable error) {
        if (error instanceof LookupDisabledException) {
            finish(State.STOPPED);
            return;
        }
        log.warn("IP location backfill stopped ({})", error.getClass().getSimpleName());
        finish(State.FAILED);
    }

    private synchronized void finish(State state) {
        var current = progress;
        progress = new Progress(state, current.saved(), current.skipped(), current.unresolved(), current.failed());
    }

    @PreDestroy
    synchronized void stop() {
        task.dispose();
    }

    record Status(boolean lookupEnabled, Progress progress) {
    }

    record Progress(State state, long saved, long skipped, long unresolved, long failed) {
        Progress count(Outcome outcome) {
            return new Progress(state, saved + (outcome == Outcome.SAVED ? 1 : 0),
                skipped + (outcome == Outcome.SKIPPED ? 1 : 0),
                unresolved + (outcome == Outcome.UNRESOLVED ? 1 : 0),
                failed + (outcome == Outcome.FAILED ? 1 : 0));
        }
    }

    enum State {
        IDLE, RUNNING, COMPLETED, STOPPED, FAILED
    }

    private static class LookupDisabledException extends RuntimeException {
    }
}
