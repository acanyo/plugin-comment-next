package com.xhhao.comment.widget.ai;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.CommentNextAction;
import com.xhhao.comment.widget.security.CommentNextActionGuard;
import com.xhhao.comment.widget.security.CommentNextActionSecurityPolicy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.exception.AiFoundationException;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import run.halo.aifoundation.exception.DefaultModelNotConfiguredException;
import run.halo.aifoundation.exception.IncompatibleModelTypeException;
import run.halo.aifoundation.exception.ModelDisabledException;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.aifoundation.exception.ProviderApiException;
import run.halo.aifoundation.exception.ProviderDisabledException;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Service
@ConditionalOnHaloAiFoundation
@RequiredArgsConstructor
public class CommentNextAiService {
    private final SettingConfigGetter settingConfigGetter;

    private final CommentNextActionGuard actionGuard;

    private final HaloAiFoundationAvailability haloAiFoundationAvailability;

    private final ExtensionGetter extensionGetter;

    public Mono<CommentNextAiSuggestionResult> generateSuggestion(ServerRequest request,
                                                                  CommentNextAiSuggestionRequest body) {
        return settingConfigGetter.getAiConfig()
            .flatMap(config -> actionGuard.verify(request, CommentNextAction.AI_GENERATE, securityPolicy(config))
                .then(haloAiFoundationAvailability.isEnabled())
                .flatMap(enabled -> generateSuggestion(config, body, enabled)))
            .onErrorMap(this::mapAiError);
    }

    private Mono<CommentNextAiSuggestionResult> generateSuggestion(SettingConfigGetter.AiConfig config,
                                                                  CommentNextAiSuggestionRequest body,
                                                                  boolean aiFoundationEnabled) {
        if (!aiFoundationEnabled) {
            return Mono.error(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Halo AI Foundation 插件未安装或未启用"
            ));
        }

        var mode = CommentNextAiMode.from(body.getMode());
        var content = normalizeInput(body.getContent());
        validateInput(config, mode, content, body.getReplyToName());

        return generateText(config, mode, content, body)
            .map(CommentNextAiSuggestionResult::new);
    }

    private Mono<String> generateText(SettingConfigGetter.AiConfig config,
                                      CommentNextAiMode mode,
                                      String content,
                                      CommentNextAiSuggestionRequest body) {
        var request = GenerateTextRequest.builder()
            .system(config.getSystemPrompt())
            .messages(List.of(ModelMessage.user(buildPrompt(mode, content, body))))
            .temperature(config.normalizedTemperature())
            .maxOutputTokens(config.normalizedMaxOutputTokens())
            .maxRetries(1)
            .build();

        return aiModelService()
            .flatMap(service -> service.languageModel(blankToNull(config.getLanguageModelName())))
            .flatMap(model -> model.generateText(request))
            .map(GenerateTextResult::getText)
            .map(this::normalizeOutput)
            .filter(StringUtils::hasText)
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "AI 未返回可用内容"
            )));
    }

    private Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Halo AI Foundation 插件未安装或未启用"
            )));
    }

    private CommentNextActionSecurityPolicy securityPolicy(SettingConfigGetter.AiConfig config) {
        var security = config.getSecurity();
        return new CommentNextActionSecurityPolicy(
            config.isEnabled(),
            config.isAllowAnonymous(),
            security.getAnonymousRateLimit(),
            security.getAnonymousRateWindowSeconds(),
            security.getAuthenticatedRateLimit(),
            security.getAuthenticatedRateWindowSeconds()
        );
    }

    private void validateInput(SettingConfigGetter.AiConfig config,
                               CommentNextAiMode mode,
                               String content,
                               String replyToName) {
        if (content.length() > config.normalizedMaxInputLength()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "AI 输入内容过长，请删减后再试"
            );
        }

        if (mode.contentRequired() && !StringUtils.hasText(content)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "请先输入需要 AI 处理的内容"
            );
        }

        if (mode == CommentNextAiMode.REPLY
            && !StringUtils.hasText(content)
            && !StringUtils.hasText(replyToName)) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "请先输入回复内容或选择回复对象"
            );
        }
    }

    private String buildPrompt(CommentNextAiMode mode,
                               String content,
                               CommentNextAiSuggestionRequest body) {
        var variant = "reply".equalsIgnoreCase(body.getVariant()) ? "回复" : "评论";
        var replyTarget = StringUtils.hasText(body.getReplyToName())
            ? "@" + body.getReplyToName().trim()
            : "无";

        return """
            请根据下面的信息生成一段可直接提交的%s文本。

            写作模式：%s
            回复对象：%s
            用户原文：
            %s

            要求：
            - 只输出最终文本，不要解释过程。
            - 不要使用 Markdown 标题、列表、引用块或代码块。
            - 内容保持真实、自然、友好，避免攻击性表达。
            - 不要编造用户原文中没有的具体事实。
            """.formatted(variant, mode.label(), replyTarget, content);
    }

    private String normalizeInput(String content) {
        return content == null ? "" : content.strip();
    }

    private String normalizeOutput(String text) {
        if (text == null) {
            return "";
        }
        return text.strip()
            .replaceAll("(?m)^```(?:\\w+)?\\s*", "")
            .replaceAll("(?m)\\s*```$", "")
            .strip();
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Throwable mapAiError(Throwable error) {
        if (error instanceof ResponseStatusException) {
            return error;
        }

        if (error instanceof DefaultModelNotConfiguredException) {
            return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "请先在 AI Foundation 中配置默认语言模型",
                error
            );
        }

        if (error instanceof ModelNotFoundException) {
            return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "AI 模型不存在，请检查插件设置",
                error
            );
        }

        if (error instanceof ModelDisabledException) {
            return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "AI 模型未启用，请检查 AI Foundation 配置",
                error
            );
        }

        if (error instanceof IncompatibleModelTypeException) {
            return new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "请选择语言模型后再使用 AI 写作",
                error
            );
        }

        if (error instanceof ProviderDisabledException) {
            return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "AI 供应商未启用，请检查 AI Foundation 配置",
                error
            );
        }

        if (error instanceof AiGenerationTimeoutException) {
            return new ResponseStatusException(
                HttpStatus.GATEWAY_TIMEOUT,
                "AI 生成超时，请稍后再试",
                error
            );
        }

        if (error instanceof ProviderApiException
            || error instanceof AiGenerationCancelledException
            || error instanceof AiFoundationException) {
            return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "AI 服务调用失败，请检查 AI Foundation 配置",
                error
            );
        }

        return error;
    }
}
