package com.xhhao.comment.widget.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.SettingConfigGetter;
import com.xhhao.comment.widget.security.CommentNextActionActor;
import com.xhhao.comment.widget.security.CommentNextActionGuard;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

class CommentNextAiTaggedSuggestionTest {
    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);
    private final CommentNextActionGuard guard = mock(CommentNextActionGuard.class);
    private final HaloAiFoundationAvailability availability = mock(HaloAiFoundationAvailability.class);
    private final CommentNextAiArticleContentResolver articles =
        mock(CommentNextAiArticleContentResolver.class);
    private final CommentNextAiAssistantProfileResolver profiles =
        mock(CommentNextAiAssistantProfileResolver.class);
    private final ExtensionGetter extensions = mock(ExtensionGetter.class);
    private final LanguageModel model = mock(LanguageModel.class);
    private final CommentNextAiService service =
        new CommentNextAiService(settings, guard, availability, articles, profiles, extensions);
    private final ServerRequest request = mock(ServerRequest.class);

    @BeforeEach
    void prepareServices() {
        var config = SettingConfigGetter.AiConfig.empty();
        var models = mock(AiModelService.class);
        when(settings.getAiConfig()).thenReturn(Mono.just(config));
        when(guard.verify(any(), any(), any()))
            .thenReturn(Mono.just(CommentNextActionActor.of("reader", "127.0.0.1")));
        when(availability.isEnabled()).thenReturn(Mono.just(true));
        when(profiles.resolve(config))
            .thenReturn(Mono.just(new CommentNextAiAssistantProfile(null, "评论助手")));
        when(extensions.getEnabledExtension(AiModelService.class)).thenReturn(Mono.just(models));
        when(models.languageModel(nullable(String.class))).thenReturn(Mono.just(model));
        when(model.generateText(any(GenerateTextRequest.class)))
            .thenReturn(Mono.just(GenerateTextResult.builder().text("生成的草稿").build()));
    }

    @Test
    void generatesBlankDraftUsingTagsAndRealArticleContext() {
        var body = requestBody("generate", "");
        body.setTags(List.of(" 内容实用 ", "引发思考"));
        body.setSubject("content.halo.run/v1alpha1/Post/article");
        when(articles.resolve(eq(body.getSubject()), anyInt()))
            .thenReturn(Mono.just(new CommentNextAiArticleContext("测试文章", "文章", "这里是实际正文")));

        assertThat(service.generateSuggestion(request, body).block().text()).isEqualTo("生成的草稿");
        assertThat(modelPrompt()).contains("内容实用、引发思考", "测试文章", "这里是实际正文");
    }

    @Test
    void legacyPolishRequestsStillUseOriginalTextWithoutTags() {
        var body = requestBody("polish", "这是我的原始观点");
        assertThat(service.generateSuggestion(request, body).block().text()).isEqualTo("生成的草稿");
        assertThat(modelPrompt()).contains("这是我的原始观点", "润色建议");
        verify(articles, never()).resolve(anyString(), anyInt());
    }

    @Test
    void rejectsBlankTagGenerationBeforeCallingTheModel() {
        var body = requestBody("generate", "");
        assertThatThrownBy(() -> service.generateSuggestion(request, body).block())
            .isInstanceOf(ResponseStatusException.class).hasMessageContaining("请至少选择 1 个评论标签");
        verify(model, never()).generateText(any(GenerateTextRequest.class));
    }

    private CommentNextAiSuggestionRequest requestBody(String mode, String content) {
        var body = new CommentNextAiSuggestionRequest();
        body.setMode(mode);
        body.setContent(content);
        body.setVariant("comment");
        return body;
    }

    private String modelPrompt() {
        var captor = ArgumentCaptor.forClass(GenerateTextRequest.class);
        verify(model).generateText(captor.capture());
        return captor.getValue().getMessages().toString();
    }
}
