package com.xhhao.comment.widget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.ai.CommentNextAiAssistantProfileResolver;
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

    private static final String SECURITY_GROUP = "security";

    private static final String CAPTCHA = "captcha";

    private static final String GEE_TEST = "geeTest";

    private static final String ALTCHA = "altcha";

    private static final String CAP = "cap";

    private static final String TYPE = "type";

    private static final String CAPTCHA_KEY = "captchaKey";

    private static final String SECRET = "secret";

    private static final String SECRET_KEY = "secretKey";

    private static final String AI_GROUP = "ai";

    private static final String AI_REVIEW_GROUP = "aiReview";

    private static final String AI_PROMPTS_GROUP = "aiPrompts";

    private static final String AI_ASSISTANT_GROUP = "assistant";

    private static final String AI_MENTION_GROUP = "mention";

    private static final String AI_MODEL_GROUP = "model";

    private static final String AI_PROMPT_GROUP = "prompt";

    private static final String IMG_BB = "imgBb";

    private static final String API_KEY = "apiKey";

    private static final String ENABLED = "enabled";

    private static final String ALLOW_ANONYMOUS = "allowAnonymous";

    private static final String MENTION_AUTO_REPLY_ENABLED = "mentionAutoReplyEnabled";

    private static final String FOUNDATION_AVAILABLE = "foundationAvailable";

    private static final String ASSISTANT_NAME = "assistantName";

    private static final String ASSISTANT_USER_NAME = "assistantUserName";

    private static final String ASSISTANT_DISPLAY_NAME = "assistantDisplayName";

    private static final String ASSISTANT_MENTION_NAME = "assistantMentionName";

    private static final String BUTTON_LABEL = "buttonLabel";

    private static final String LANGUAGE_MODEL_NAME = "languageModelName";

    private static final String MAX_INPUT_LENGTH = "maxInputLength";

    private static final String MAX_OUTPUT_TOKENS = "maxOutputTokens";

    private static final String TEMPERATURE = "temperature";

    private static final String SYSTEM_PROMPT = "systemPrompt";

    private final ReactiveExtensionClient client;

    private final PluginContext context;

    private final HaloAiFoundationAvailability haloAiFoundationAvailability;

    private final CommentNextAiAssistantProfileResolver aiAssistantProfileResolver;

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
            .map(this::flattenAiGroupedFields)
            .map(this::removeSensitiveFields)
            .map(this::applyAiDefaults)
            .flatMap(this::appendAiAssistantProfile)
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
        rootNode.remove(List.of(AI_REVIEW_GROUP, AI_PROMPTS_GROUP));

        var uploadValue = rootNode.get(UPLOAD_GROUP);
        if (uploadValue instanceof ObjectNode uploadNode) {
            var imgBbValue = uploadNode.get(IMG_BB);
            if (imgBbValue instanceof ObjectNode imgBbNode) {
                imgBbNode.remove(API_KEY);
            }
        }

        var securityValue = rootNode.get(SECURITY_GROUP);
        if (securityValue instanceof ObjectNode securityNode) {
            removeCaptchaSensitiveFields(securityNode);
        }

        var aiValue = rootNode.get(AI_GROUP);
        if (aiValue instanceof ObjectNode aiNode) {
            aiNode.remove(List.of(
                AI_ASSISTANT_GROUP,
                AI_MENTION_GROUP,
                AI_MODEL_GROUP,
                AI_PROMPT_GROUP,
                LANGUAGE_MODEL_NAME,
                MAX_OUTPUT_TOKENS,
                TEMPERATURE,
                "reasoningEffort",
                SYSTEM_PROMPT,
                "security"
            ));
        }
        return rootNode;
    }

    private void removeCaptchaSensitiveFields(ObjectNode securityNode) {
        var captchaValue = securityNode.get(CAPTCHA);
        if (!(captchaValue instanceof ObjectNode captchaNode)) {
            return;
        }

        captchaNode.remove("turnstile");
        var captchaType = captchaNode.path(TYPE).asText();
        if (!Set.of("ALPHANUMERIC", "ARITHMETIC", "GEETEST", "ALTCHA", "CAP").contains(captchaType)) {
            captchaNode.put(TYPE, "ALPHANUMERIC");
        }

        var geeTestValue = captchaNode.get(GEE_TEST);
        if (geeTestValue instanceof ObjectNode geeTestNode) {
            geeTestNode.remove(CAPTCHA_KEY);
        }

        var altchaValue = captchaNode.get(ALTCHA);
        if (altchaValue instanceof ObjectNode altchaNode) {
            altchaNode.remove(SECRET);
        }

        var capValue = captchaNode.get(CAP);
        if (capValue instanceof ObjectNode capNode) {
            capNode.remove(SECRET_KEY);
        }
    }

    private ObjectNode flattenAiGroupedFields(ObjectNode rootNode) {
        var aiNode = aiNode(rootNode);
        var assistantNode = childObject(aiNode, AI_ASSISTANT_GROUP);
        var mentionNode = childObject(aiNode, AI_MENTION_GROUP);
        var modelNode = childObject(aiNode, AI_MODEL_GROUP);
        var promptNode = childObject(aiNode, AI_PROMPT_GROUP);

        copyIfPresent(assistantNode, aiNode, ASSISTANT_NAME);
        copyIfPresent(assistantNode, aiNode, ASSISTANT_USER_NAME);
        copyIfPresent(assistantNode, aiNode, BUTTON_LABEL);
        copyIfPresent(mentionNode, aiNode, MENTION_AUTO_REPLY_ENABLED);
        copyIfPresent(modelNode, aiNode, ALLOW_ANONYMOUS);
        copyIfPresent(modelNode, aiNode, LANGUAGE_MODEL_NAME);
        copyIfPresent(modelNode, aiNode, MAX_INPUT_LENGTH);
        copyIfPresent(modelNode, aiNode, MAX_OUTPUT_TOKENS);
        copyIfPresent(modelNode, aiNode, TEMPERATURE);
        copyIfPresent(promptNode, aiNode, SYSTEM_PROMPT);

        return rootNode;
    }

    private ObjectNode childObject(ObjectNode parent, String fieldName) {
        var value = parent.get(fieldName);
        return value instanceof ObjectNode objectNode ? objectNode : objectMapper.createObjectNode();
    }

    private void copyIfPresent(ObjectNode source, ObjectNode target, String fieldName) {
        var value = source.get(fieldName);
        if (value != null && !value.isNull()) {
            target.set(fieldName, value.deepCopy());
        }
    }

    private Mono<ObjectNode> appendAiAssistantProfile(ObjectNode rootNode) {
        var aiNode = aiNode(rootNode);
        var assistantUserName = aiNode.path(ASSISTANT_USER_NAME).asText();
        var fallbackDisplayName = aiNode.path(ASSISTANT_NAME).asText();

        return aiAssistantProfileResolver.resolve(assistantUserName, fallbackDisplayName)
            .map(profile -> {
                if (profile.hasUser()) {
                    aiNode.put(ASSISTANT_USER_NAME, profile.username());
                }
                aiNode.put(ASSISTANT_NAME, profile.displayName());
                aiNode.put(ASSISTANT_DISPLAY_NAME, profile.displayName());
                aiNode.put(ASSISTANT_MENTION_NAME, profile.mentionName());
                return rootNode;
            })
            .onErrorReturn(rootNode);
    }

    private ObjectNode applyAiDefaults(ObjectNode rootNode) {
        var aiNode = aiNode(rootNode);
        if (!aiNode.has(ENABLED)) {
            aiNode.put(ENABLED, false);
        }
        if (!aiNode.has(ALLOW_ANONYMOUS)) {
            aiNode.put(ALLOW_ANONYMOUS, false);
        }
        if (!aiNode.has(MENTION_AUTO_REPLY_ENABLED)) {
            aiNode.put(MENTION_AUTO_REPLY_ENABLED, false);
        }
        putTextIfBlank(aiNode, ASSISTANT_NAME, SettingConfigGetter.AiConfig.DEFAULT_ASSISTANT_NAME);
        putTextIfBlank(aiNode, BUTTON_LABEL, SettingConfigGetter.AiConfig.DEFAULT_BUTTON_LABEL);
        return rootNode;
    }

    private void putTextIfBlank(ObjectNode node, String fieldName, String value) {
        if (!StringUtils.hasText(node.path(fieldName).asText())) {
            node.put(fieldName, value);
        }
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
