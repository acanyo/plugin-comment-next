package com.xhhao.comment.widget.comment;

import com.xhhao.comment.utils.CommonUtils;
import com.xhhao.comment.widget.CommentNextRoles;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.core.user.service.UserService;

@Component
@RequiredArgsConstructor
class CommentNextAccessService {

    private final UserService userService;

    Mono<CommentNextAccessContext> getCurrentAccess() {
        var username = CommonUtils.getCurrentUserName();
        var canViewAll = userService.hasSufficientRoles(Set.of(CommentNextRoles.COMMENT_VIEW))
            .defaultIfEmpty(false)
            .onErrorReturn(false);

        return Mono.zip(username, canViewAll)
            .map(tuple -> new CommentNextAccessContext(tuple.getT1(), tuple.getT2()));
    }
}
