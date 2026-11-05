package com.xhhao.comment.widget.security.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.user.service.RoleService;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class CommentNextEmailRestrictionServiceTest {

    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final RoleService roles = mock(RoleService.class);
    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);
    private final CommentNextEmailRestrictionConfig config = new CommentNextEmailRestrictionConfig();
    private final CommentNextEmailRestrictionService service =
        new CommentNextEmailRestrictionService(client, roles, settings);

    @BeforeEach
    void defaults() {
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.empty());
        when(settings.getBadgeConfig()).thenReturn(Mono.just(SettingConfigGetter.BadgeConfig.empty()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Test@Example.COM", "reader@sub.example.net", "reader@example.org",
        "reader@demo.test", "reader@localhost", "reader@not-a-mail.invalid", "reader@site.example"})
    void blocksReservedExampleDomains(String email) {
        assertRejected(email);
        verifyNoInteractions(roles);
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789@qq.com", "test@qq.com", "admin@outlook.com", "reader@notexample.com"})
    void doesNotGuessWhetherRealProviderAddressesAreFake(String email) {
        service.validate(email, config).block();
        verifyNoInteractions(roles);
    }

    @Test
    void blocksExplicitAddressesIgnoringCaseAndWhitespaceButNotOtherAddressesOnTheDomain() {
        config.setBlockedEmails(List.of(new CommentNextEmailRestrictionConfig.BlockedEmail(" FAKE@qq.com ")));
        assertRejected(" \tFake@QQ.COM\n");
        service.validate("another@qq.com", config).block();
        assertThatThrownBy(() -> service.validate("fake@qq.com", config).block())
            .hasMessageNotContaining("fake@qq.com");
    }

    @Test
    void protectsSuperAdministratorCurrentEmailAndReflectsChangesWithoutAStaleCache() {
        var admin = user("owner", " Owner@Mail.com ");
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(admin));
        when(roles.getRolesByUsername("owner")).thenReturn(Flux.just("super-role"));
        assertRejected("owner@mail.COM");

        admin.getSpec().setEmail("new-owner@mail.com");
        service.validate("owner@mail.com", config).block();
        assertRejected("new-owner@mail.com");
    }

    @Test
    void alsoProtectsConfiguredAdditionalAdministrators() {
        var badgeConfig = SettingConfigGetter.BadgeConfig.empty();
        var identifier = new SettingConfigGetter.BadgeIdentifier();
        identifier.setUsername(" EDITOR ");
        badgeConfig.setAdminIdentifiers(List.of(identifier));
        when(settings.getBadgeConfig()).thenReturn(Mono.just(badgeConfig));
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(user("editor", "editor@mail.com")));
        assertRejected("editor@mail.com");
        verifyNoInteractions(roles);
    }

    @Test
    void doesNotTreatEveryRegisteredMemberAsAnAdministrator() {
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(user("reader", "reader@mail.com")));
        when(roles.getRolesByUsername("reader")).thenReturn(Flux.just("authenticated"));
        service.validate("reader@mail.com", config).block();
    }

    @Test
    void doesNotProtectDeletedUserRecords() {
        var deleted = user("deleted", "deleted@mail.com");
        deleted.getMetadata().setDeletionTimestamp(Instant.now());
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(deleted));
        service.validate("deleted@mail.com", config).block();
        verifyNoInteractions(roles);
    }

    @Test
    void protectionCanBeDisabledWithoutDisablingTheExplicitDenyList() {
        config.setProtectAdminEmails(false);
        config.setBlockedEmails(List.of(new CommentNextEmailRestrictionConfig.BlockedEmail("bad@mail.com")));
        service.validate("owner@mail.com", config).block();
        assertRejected("bad@mail.com");
        verifyNoInteractions(client, roles);
    }

    @Test
    void exampleFilterCanBeDisabledIndependently() {
        config.setBlockExampleEmails(false);
        config.setProtectAdminEmails(false);
        service.validate("test@example.com", config).block();
        verifyNoInteractions(client, roles);
    }

    @Test
    void disabledPolicyAndEmptyEmailsDoNotQueryUserData() {
        config.setEnabled(false);
        service.validate("test@example.com", config).block();
        config.setEnabled(true);
        service.validate(null, config).block();
        service.validate(" ", config).block();
        verifyNoInteractions(client, roles);
    }

    @Test
    void roleLookupErrorsDoNotSilentlyAllowProtectedAddresses() {
        when(client.listAll(eq(User.class), any(ListOptions.class), any(Sort.class)))
            .thenReturn(Flux.just(user("owner", "owner@mail.com")));
        when(roles.getRolesByUsername("owner")).thenReturn(Flux.error(new IllegalStateException("roles unavailable")));
        assertThatThrownBy(() -> service.validate("owner@mail.com", config).block())
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void absentAndNullSettingsKeepTheSameDefaultsAsTheSettingsForm() throws Exception {
        var mapper = JsonUtils.createObjectMapper();
        var defaults = mapper.readValue("{}", CommentNextEmailRestrictionConfig.class);
        assertThat(defaults.isEnabled()).isTrue();
        assertThat(defaults.isBlockExampleEmails()).isTrue();
        assertThat(defaults.isProtectAdminEmails()).isTrue();
        assertThat(defaults.getBlockedEmails()).isEmpty();
        var withNull = mapper.readValue("{\"blockedEmails\":null}", CommentNextEmailRestrictionConfig.class);
        assertThat(withNull.getBlockedEmails()).isEmpty();
    }

    private void assertRejected(String email) {
        assertThatThrownBy(() -> service.validate(email, config).block())
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                assertThat(error.getBody().getType()).isEqualTo(CommentNextEmailRestrictionService.REJECTION_TYPE);
                assertThat(error.getReason()).isEqualTo(CommentNextEmailRestrictionService.REJECTION_MESSAGE);
            });
    }

    private User user(String name, String email) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(name);
        user.setMetadata(metadata);
        user.getSpec().setEmail(email);
        return user;
    }
}
