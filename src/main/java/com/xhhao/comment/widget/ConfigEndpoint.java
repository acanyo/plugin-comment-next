package com.xhhao.comment.widget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.ai.HaloAiFoundationAvailability;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.User;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.ExtensionUtil;
import run.halo.app.extension.GroupVersion;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.PluginContext;

import static run.halo.app.extension.index.query.Queries.in;

@Component
@RequiredArgsConstructor
public class ConfigEndpoint implements CustomEndpoint {

    private static final String BADGE_GROUP = "badge";

    private static final String ADMIN_IDENTIFIERS = "adminIdentifiers";

    private static final String USERNAME = "username";

    private static final String UPLOAD_GROUP = "upload";

    private static final String AI_GROUP = "ai";

    private static final String IMG_BB = "imgBb";

    private static final String API_KEY = "apiKey";

    private static final String ENABLED = "enabled";

    private static final String ALLOW_ANONYMOUS = "allowAnonymous";

    private static final String FOUNDATION_AVAILABLE = "foundationAvailable";

    private final ReactiveExtensionClient client;

    private final PluginContext context;

    private final HaloAiFoundationAvailability haloAiFoundationAvailability;

    private final ObjectMapper objectMapper = JsonUtils.createObjectMapper();

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
                .GET("config", this::getConfig)
                .build();
    }


    private Mono<ServerResponse> getConfig(ServerRequest request) {
        return client.fetch(ConfigMap.class, context.getConfigMapName())
            .map(this::toConfigNode)
            .defaultIfEmpty(objectMapper.createObjectNode())
            .map(this::removeSensitiveFields)
            .map(this::applyAiDefaults)
            .flatMap(this::appendAiFoundationAvailability)
            .flatMap(this::appendSystemAdminIdentifiers)
            .flatMap(rootNode -> ServerResponse.ok().bodyValue(rootNode));
    }

    private ObjectNode toConfigNode(ConfigMap configMap) {
        Map<String, String> data = configMap.getData();
        ObjectNode rootNode = objectMapper.createObjectNode();

        if (data == null) {
            return rootNode;
        }

        data.forEach((key, value) -> {
            try {
                JsonNode jsonNode = objectMapper.readTree(value);
                rootNode.set(key, jsonNode);
            } catch (Exception e) {
                rootNode.put(key, value);
            }
        });

        return rootNode;
    }

    private Mono<ObjectNode> appendAiFoundationAvailability(ObjectNode rootNode) {
        return haloAiFoundationAvailability.isEnabled()
            .map(available -> {
                aiNode(rootNode).put(FOUNDATION_AVAILABLE, available);
                return rootNode;
            })
            .onErrorReturn(rootNode);
    }

    private ObjectNode removeSensitiveFields(ObjectNode rootNode) {
        var uploadValue = rootNode.get(UPLOAD_GROUP);
        if (uploadValue instanceof ObjectNode uploadNode) {
            var imgBbValue = uploadNode.get(IMG_BB);
            if (imgBbValue instanceof ObjectNode imgBbNode) {
                imgBbNode.remove(API_KEY);
            }
        }

        var aiValue = rootNode.get(AI_GROUP);
        if (aiValue instanceof ObjectNode aiNode) {
            aiNode.remove(List.of(
                "languageModelName",
                "maxOutputTokens",
                "temperature",
                "systemPrompt",
                "security"
            ));
        }
        return rootNode;
    }

    private ObjectNode applyAiDefaults(ObjectNode rootNode) {
        var aiNode = aiNode(rootNode);
        if (!aiNode.has(ENABLED)) {
            aiNode.put(ENABLED, false);
        }
        if (!aiNode.has(ALLOW_ANONYMOUS)) {
            aiNode.put(ALLOW_ANONYMOUS, false);
        }
        return rootNode;
    }

    private ObjectNode aiNode(ObjectNode rootNode) {
        var aiValue = rootNode.get(AI_GROUP);
        if (aiValue instanceof ObjectNode existingAiNode) {
            return existingAiNode;
        }

        var aiNode = objectMapper.createObjectNode();
        rootNode.set(AI_GROUP, aiNode);
        return aiNode;
    }

    private Mono<ObjectNode> appendSystemAdminIdentifiers(ObjectNode rootNode) {
        var adminIdentifiers = adminIdentifiers(rootNode);
        var usernames = configuredAdminUsernames(adminIdentifiers);

        return client.listAll(User.class, superAdminUserOptions(), ExtensionUtil.defaultSort())
            .map(user -> user.getMetadata().getName())
            .filter(StringUtils::hasText)
            .distinct()
            .collectList()
            .map(superAdminUsernames -> {
                usernames.addAll(superAdminUsernames);
                usernames.forEach(username -> upsertUsernameIdentifier(adminIdentifiers, username));
                return rootNode;
            })
            .onErrorReturn(rootNode);
    }

    private ListOptions superAdminUserOptions() {
        return ListOptions.builder()
            .andQuery(ExtensionUtil.notDeleting())
            .andQuery(in(User.USER_RELATED_ROLES_INDEX, CommentNextRoles.SUPER_ADMIN))
            .build();
    }

    private ArrayNode adminIdentifiers(ObjectNode rootNode) {
        var badgeNode = badgeNode(rootNode);
        var adminIdentifiersValue = badgeNode.get(ADMIN_IDENTIFIERS);
        if (adminIdentifiersValue instanceof ArrayNode existingAdminIdentifiers) {
            return existingAdminIdentifiers;
        }

        var adminIdentifiers = objectMapper.createArrayNode();
        badgeNode.set(ADMIN_IDENTIFIERS, adminIdentifiers);
        return adminIdentifiers;
    }

    private ObjectNode badgeNode(ObjectNode rootNode) {
        var badgeValue = rootNode.get(BADGE_GROUP);

        if (badgeValue instanceof ObjectNode existingBadgeNode) {
            return existingBadgeNode;
        }

        var badgeNode = objectMapper.createObjectNode();
        rootNode.set(BADGE_GROUP, badgeNode);
        return badgeNode;
    }

    private Set<String> configuredAdminUsernames(ArrayNode identifiers) {
        var usernames = new LinkedHashSet<String>();
        for (JsonNode identifier : identifiers) {
            var username = identifier.path(USERNAME).asText();
            if (StringUtils.hasText(username)) {
                usernames.add(username);
            }
        }
        return usernames;
    }

    private void upsertUsernameIdentifier(ArrayNode identifiers, String username) {
        ObjectNode identifier = findUsernameIdentifier(identifiers, username);
        if (identifier == null) {
            identifier = usernameIdentifier(username);
            identifiers.add(identifier);
        }
    }

    private ObjectNode findUsernameIdentifier(ArrayNode identifiers, String username) {
        for (JsonNode identifier : identifiers) {
            if (identifier instanceof ObjectNode objectNode
                && username.equalsIgnoreCase(identifier.path(USERNAME).asText())) {
                return objectNode;
            }
        }
        return null;
    }

    private ObjectNode usernameIdentifier(String username) {
        var identifier = objectMapper.createObjectNode();
        identifier.put(USERNAME, username);
        return identifier;
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion("api.commentnext.xhhao.com/v1alpha1");
    }
}
