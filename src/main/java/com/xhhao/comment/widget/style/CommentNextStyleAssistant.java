package com.xhhao.comment.widget.style;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.ai.ConditionalOnHaloAiFoundation;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerationTimeouts;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import run.halo.aifoundation.exception.DefaultModelNotConfiguredException;
import run.halo.aifoundation.exception.IncompatibleModelTypeException;
import run.halo.aifoundation.exception.ModelDisabledException;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.exception.ProviderDisabledException;
import run.halo.aifoundation.exception.StructuredOutputValidationException;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.schema.OutputType;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Service
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
class CommentNextStyleAssistant {

    private static final String SETUP_MESSAGE = "请在下方选择语言模型，或在 AI Foundation 中配置默认模型。";
    private static final Duration GENERATION_TIMEOUT = Duration.ofSeconds(50);
    private static final String SYSTEM = """
        你负责编辑 Halo 评论区的样式草稿。只能调整以下字段。
        lightAccent / darkAccent 是浅色和深色模式的主色，使用六位 HEX。
        radius 为 0 到 24 的整数像素。density 可用 compact（紧凑）或 spacious（宽松）。
        listStyle 可用 flat（分隔线列表）或 cards（卡片）。
        changes 只列出本轮改动的字段。值为 null 表示恢复该字段的主题默认值。
        让深浅两种模式的主色在背景上都容易辨认。summary 用用户的语言简短描述实际改动。
        """;

    private final ExtensionGetter extensionGetter;
    private final SettingConfigGetter settings;
    private final ObjectMapper mapper = JsonUtils.createObjectMapper();

    Mono<Availability> availability(String modelName) {
        var unconfigured = new Availability(false, true, modelSetupMessage(modelName));
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .flatMap(service -> Mono.defer(() -> model(service, modelName))
                .timeout(Duration.ofSeconds(5))
                .map(ignored -> new Availability(true, true, null))
                .defaultIfEmpty(unconfigured)
                .onErrorReturn(unconfigured))
            .defaultIfEmpty(unavailable())
            .onErrorReturn(unavailable());
    }

    Mono<Suggestion> suggest(CommentNextStyle draft, String instruction, String modelName) {
        var request = GenerateTextRequest.builder()
            .system(SYSTEM)
            .prompt("当前草稿\n%s\n\n本轮需求\n%s".formatted(mapper.valueToTree(draft), instruction))
            .output(OutputSpec.builder().type(OutputType.OBJECT).name("comment_next_style")
                .schema(Map.of("type", "object", "additionalProperties", false,
                    "required", List.of("summary", "changes"), "properties", Map.of(
                        "summary", Map.of("type", "string", "maxLength", 240),
                        "changes", CommentNextStyle.patchSchema())))
                .build())
            .temperature(0.3)
            .maxOutputTokens(1600)
            .timeouts(GenerationTimeouts.total(GENERATION_TIMEOUT))
            .build();

        return extensionGetter.getEnabledExtension(AiModelService.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                unavailable().reason())))
            .flatMap(service -> model(service, modelName))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                modelSetupMessage(modelName))))
            .flatMap(model -> model.generateText(request))
            .map(result -> apply(draft, mapper.valueToTree(result.getOutput())))
            .timeout(GENERATION_TIMEOUT)
            .onErrorMap(error -> !(error instanceof ResponseStatusException), error -> translate(error, modelName));
    }

    private Suggestion apply(CommentNextStyle draft, JsonNode answer) {
        var changed = draft.apply(answer.path("changes"));
        var summary = answer.path("summary").asText("").strip();
        if (summary.length() > 240) {
            summary = summary.substring(0, 240);
        }
        return new Suggestion(changed, summary);
    }

    private Mono<LanguageModel> model(AiModelService service, String modelName) {
        if (!modelName.isEmpty()) {
            return service.languageModel(modelName);
        }
        return settings.getAiConfig().flatMap(config -> {
            var name = config.getLanguageModelName();
            return name == null || name.isBlank() ? service.languageModel() : service.languageModel(name.strip());
        });
    }

    private ResponseStatusException translate(Throwable error, String modelName) {
        if (error instanceof DefaultModelNotConfiguredException || error instanceof ModelNotFoundException
            || error instanceof ModelDisabledException || error instanceof ProviderDisabledException
            || error instanceof IncompatibleModelTypeException) {
            return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, modelSetupMessage(modelName));
        }
        if (error instanceof TimeoutException || error instanceof AiGenerationTimeoutException) {
            return new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "AI 响应超时，请稍后重试。");
        }
        if (error instanceof IllegalArgumentException || error instanceof StructuredOutputValidationException) {
            return new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT,
                "AI 返回的样式无效，请换一种描述再试。");
        }
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI 生成失败，请检查模型服务后重试。");
    }

    private static String modelSetupMessage(String modelName) {
        return modelName.isEmpty() ? SETUP_MESSAGE : "所选模型不可用，请重新选择可用的语言模型。";
    }

    static Availability unavailable() {
        return new Availability(false, false, "启用 AI Foundation 后可使用 AI 修改样式。");
    }

    record Suggestion(CommentNextStyle draft, String summary) {
    }

    record Availability(boolean available, boolean canChooseModel, String reason) {
    }
}
