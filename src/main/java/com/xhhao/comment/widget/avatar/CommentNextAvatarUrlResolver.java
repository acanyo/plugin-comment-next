package com.xhhao.comment.widget.avatar;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.user.service.UserService;

@Component
public class CommentNextAvatarUrlResolver {

    private final UserService userService;

    public CommentNextAvatarUrlResolver(UserService userService) {
        this.userService = userService;
    }

    public Mono<String> resolve(Comment.CommentOwner owner) {
        if (owner == null) {
            return Mono.just(CommentNextWeAvatarUrl.defaultAvatar());
        }

        var annotatedAvatar = owner.getAnnotation(Comment.CommentOwner.AVATAR_ANNO);
        if (StringUtils.hasText(annotatedAvatar)) {
            return Mono.just(annotatedAvatar.strip());
        }

        if (Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())) {
            return Mono.just(CommentNextWeAvatarUrl.forEmail(owner.getName()));
        }

        if (User.KIND.equals(owner.getKind()) && StringUtils.hasText(owner.getName())) {
            return userService.getUserOrGhost(owner.getName().strip())
                .flatMap(user -> Mono.justOrEmpty(user.getSpec()))
                .flatMap(spec -> Mono.justOrEmpty(spec.getAvatar()))
                .filter(StringUtils::hasText)
                .map(String::strip)
                .defaultIfEmpty(CommentNextWeAvatarUrl.defaultAvatar())
                .onErrorReturn(CommentNextWeAvatarUrl.defaultAvatar());
        }

        return Mono.just(CommentNextWeAvatarUrl.defaultAvatar());
    }
}
