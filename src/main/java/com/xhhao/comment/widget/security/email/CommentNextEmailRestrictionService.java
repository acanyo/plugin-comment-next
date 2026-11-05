package com.xhhao.comment.widget.security.email;

import com.xhhao.comment.widget.CommentNextRoles;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;

/** Checks anonymous addresses without publishing the configured or administrator addresses. */
@Service
@RequiredArgsConstructor
public class CommentNextEmailRestrictionService {

    static final String REJECTION_MESSAGE = "该邮箱不能用于匿名评论，请更换邮箱或登录后重试。";
    static final URI REJECTION_TYPE = URI.create("https://www.xhhao.com/probs/comment-email-rejected");

    private final ReactiveExtensionClient client;
    private final RoleService roleService;
    private final SettingConfigGetter settings;

    public Mono<Void> validate(String email, CommentNextEmailRestrictionConfig config) {
        var normalized = CommentNextEmailRestrictionConfig.normalize(email);
        if (!config.isEnabled() || normalized.isEmpty()) {
            // Required fields and email syntax remain the responsibility of Halo's endpoint.
            return Mono.empty();
        }
        if (config.blocks(normalized)) {
            return Mono.error(rejected());
        }
        if (!config.isProtectAdminEmails()) {
            return Mono.empty();
        }
        return isAdminEmail(normalized)
            .flatMap(protectedEmail -> protectedEmail ? Mono.error(rejected()) : Mono.empty());
    }

    private Mono<Boolean> isAdminEmail(String email) {
        var options = ListOptions.builder().andQuery(ExtensionUtil.notDeleting()).build();
        return client.listAll(User.class, options, Sort.by("metadata.name"))
            .filter(user -> user.getMetadata() != null && !ExtensionUtil.isDeleted(user)
                && user.getSpec() != null
                && email.equals(CommentNextEmailRestrictionConfig.normalize(user.getSpec().getEmail())))
            .concatMap(user -> isAdmin(user.getMetadata().getName()))
            .any(Boolean.TRUE::equals);
    }

    private Mono<Boolean> isAdmin(String username) {
        if (username == null || username.isBlank()) {
            return Mono.just(false);
        }
        return settings.getBadgeConfig().flatMap(config -> {
            var configured = config.getAdminIdentifiers().stream()
                .filter(item -> item != null && item.getUsername() != null)
                .anyMatch(item -> username.equalsIgnoreCase(item.getUsername().strip()));
            if (configured) {
                return Mono.just(true);
            }
            return roleService.getRolesByUsername(username).any(CommentNextRoles.SUPER_ADMIN::equals);
        });
    }

    private ResponseStatusException rejected() {
        var error = new ResponseStatusException(HttpStatus.BAD_REQUEST, REJECTION_MESSAGE);
        error.setType(REJECTION_TYPE);
        return error;
    }
}
