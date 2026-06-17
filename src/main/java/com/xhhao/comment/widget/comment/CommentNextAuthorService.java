package com.xhhao.comment.widget.comment;

import com.fasterxml.jackson.core.type.TypeReference;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.CommentNextRoles;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.badge.CommentNextBadgeAnnotations;
import com.xhhao.comment.widget.badge.CommentNextBadgeIdentity;
import com.xhhao.comment.widget.badge.CommentNextBadgeProfileService;
import com.xhhao.comment.widget.badge.CommentNextBadgeProfileSnapshot;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.core.user.service.UserService;

@Component
@RequiredArgsConstructor
class CommentNextAuthorService {

    static final String ROLE_ADMIN = "admin";

    static final String ROLE_MEMBER = "member";

    static final String ROLE_ANONYMOUS = "anonymous";

    private static final String ANONYMOUS_DISPLAY_NAME = "匿名用户";

    private final UserService userService;

    private final RoleService roleService;

    private final SettingConfigGetter settingConfigGetter;

    private final CommentNextBadgeProfileService badgeProfileService;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
        JsonUtils.createObjectMapper();

    Mono<CommentNextBadgeContext> badgeContext() {
        return settingConfigGetter.getBadgeConfig()
            .defaultIfEmpty(SettingConfigGetter.BadgeConfig.empty())
            .map(CommentNextBadgeContext::new);
    }

    Mono<CommentNextAuthor> resolve(Comment.CommentOwner owner, CommentNextBadgeContext badgeContext) {
        if (owner == null) {
            return Mono.just(anonymousAuthor());
        }

        if (User.KIND.equals(owner.getKind())) {
            return resolveUserAuthor(owner, badgeContext);
        }

        if (Comment.CommentOwner.KIND_EMAIL.equals(owner.getKind())) {
            return resolveEmailAuthor(owner);
        }

        return Mono.just(anonymousAuthor());
    }

    private Mono<CommentNextAuthor> resolveUserAuthor(Comment.CommentOwner owner,
        CommentNextBadgeContext badgeContext) {
        var username = owner.getName();
        if (!StringUtils.hasText(username)) {
            return Mono.just(userAuthor(owner, ROLE_MEMBER));
        }

        var badgeConfig = badgeContext.settings();
        var adminUsernames = toAdminUsernames(badgeConfig);
        return Mono.zip(
                userService.getUserOrGhost(username),
                resolveUserRole(username, adminUsernames),
                badgeProfileService.fetchSnapshot(
                    CommentNextBadgeIdentity.user(username).orElseThrow()
                )
            )
            .map(tuple -> userAuthor(tuple.getT1(), tuple.getT2(), owner, tuple.getT3()))
            .defaultIfEmpty(userAuthor(owner, ROLE_MEMBER));
    }

    private Mono<CommentNextAuthor> resolveEmailAuthor(Comment.CommentOwner owner) {
        return CommentNextBadgeIdentity.email(owner.getName())
            .map(identity -> badgeProfileService.fetchSnapshot(identity)
                .map(snapshot -> emailAuthor(owner, snapshot)))
            .orElseGet(() -> Mono.just(emailAuthor(owner, CommentNextBadgeProfileSnapshot.empty())));
    }

    private Mono<String> resolveUserRole(String username, Set<String> adminUsernames) {
        if (isConfiguredAdmin(username, adminUsernames)) {
            return Mono.just(ROLE_ADMIN);
        }

        return roleService.getRolesByUsername(username)
            .any(CommentNextRoles.SUPER_ADMIN::equals)
            .map(isAdmin -> isAdmin ? ROLE_ADMIN : ROLE_MEMBER)
            .defaultIfEmpty(ROLE_MEMBER)
            .onErrorReturn(ROLE_MEMBER);
    }

    private boolean isConfiguredAdmin(String username, Set<String> adminUsernames) {
        return StringUtils.hasText(username)
            && adminUsernames.stream().anyMatch(username::equalsIgnoreCase);
    }

    private Set<String> toAdminUsernames(SettingConfigGetter.BadgeConfig badgeConfig) {
        var usernames = new LinkedHashSet<String>();
        badgeConfig.getAdminIdentifiers().forEach(identifier -> {
            if (identifier != null && StringUtils.hasText(identifier.getUsername())) {
                usernames.add(identifier.getUsername());
            }
        });
        return usernames;
    }

    private CommentNextAuthor userAuthor(User user, String role, Comment.CommentOwner fallback,
        CommentNextBadgeProfileSnapshot profile) {
        var spec = user.getSpec();
        var displayName = spec == null ? null : spec.getDisplayName();
        var avatar = spec == null ? null : spec.getAvatar();
        var email = spec == null ? null : spec.getEmail();
        var username = user.getMetadata().getName();
        var activeCommentCount = profile.activeCommentCount();
        var badges = userBadges(user);
        badges.addAll(profile.badges());
        badges = dedupeBadges(badges);

        return new CommentNextAuthor(
            username,
            firstText(displayName, fallback.getDisplayName(), username),
            avatar,
            User.KIND,
            role,
            activeCommentCount,
            badges
        );
    }

    private CommentNextAuthor userAuthor(Comment.CommentOwner owner, String role) {
        return new CommentNextAuthor(
            owner.getName(),
            firstText(owner.getDisplayName(), owner.getName(), ANONYMOUS_DISPLAY_NAME),
            null,
            User.KIND,
            role,
            0,
            List.of()
        );
    }

    private CommentNextAuthor emailAuthor(Comment.CommentOwner owner,
        CommentNextBadgeProfileSnapshot profile) {
        var displayName = firstText(owner.getDisplayName(), ANONYMOUS_DISPLAY_NAME);
        return new CommentNextAuthor(
            null,
            displayName,
            owner.getAnnotation(Comment.CommentOwner.AVATAR_ANNO),
            Comment.CommentOwner.KIND_EMAIL,
            ROLE_ANONYMOUS,
            profile.activeCommentCount(),
            profile.badges()
        );
    }

    private CommentNextAuthor anonymousAuthor() {
        return new CommentNextAuthor(
            null,
            ANONYMOUS_DISPLAY_NAME,
            null,
            Comment.CommentOwner.KIND_EMAIL,
            ROLE_ANONYMOUS,
            0,
            List.of()
        );
    }

    private List<CommentNextBadge> userBadges(User user) {
        var annotations = user.getMetadata().getAnnotations();
        if (annotations == null) {
            return new ArrayList<>();
        }

        var badges = new ArrayList<CommentNextBadge>();
        badges.addAll(readBadges(annotations, CommentNextBadgeAnnotations.USER_BADGES));
        return dedupeBadges(badges);
    }

    private List<CommentNextBadge> readBadges(Map<String, String> annotations, String annotationName) {
        var value = annotations.get(annotationName);
        if (!StringUtils.hasText(value)) {
            return Collections.emptyList();
        }

        try {
            return new ArrayList<>(objectMapper.readValue(
                value,
                new TypeReference<List<CommentNextBadge>>() {
                }
            ));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private List<CommentNextBadge> dedupeBadges(List<CommentNextBadge> badges) {
        var deduped = new LinkedHashMap<String, CommentNextBadge>();
        badges.stream()
            .filter(badge -> badge != null && StringUtils.hasText(badge.id()))
            .forEach(badge -> deduped.putIfAbsent(badge.id(), badge));
        return new ArrayList<>(deduped.values());
    }

    private String firstText(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return ANONYMOUS_DISPLAY_NAME;
    }
}
