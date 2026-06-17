package com.xhhao.comment.widget.badge;

import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CommentNextBadgeStatsScheduler {

    private final CommentNextBadgeStatsService badgeStatsService;

    private final AtomicBoolean syncing = new AtomicBoolean(false);

    @Scheduled(initialDelay = 60_000, fixedDelay = 600_000)
    public void syncBadgeProfiles() {
        if (!syncing.compareAndSet(false, true)) {
            return;
        }

        badgeStatsService.syncBadgeProfiles()
            .doOnNext(result -> log.debug(
                "Synced comment next badge profiles: activeProfiles={}, updatedProfiles={}, activeComments={}",
                result.activeProfiles(),
                result.updatedProfiles(),
                result.activeComments()
            ))
            .doOnError(error -> log.warn("Failed to sync comment next badge profiles", error))
            .doFinally(signalType -> syncing.set(false))
            .subscribe();
    }
}
