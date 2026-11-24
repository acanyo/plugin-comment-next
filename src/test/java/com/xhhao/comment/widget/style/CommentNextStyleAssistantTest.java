package com.xhhao.comment.widget.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xhhao.comment.widget.SettingConfigGetter;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.LanguageModel;
import run.halo.aifoundation.exception.DefaultModelNotConfiguredException;
import run.halo.aifoundation.exception.ModelNotFoundException;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

class CommentNextStyleAssistantTest {

    private final ExtensionGetter extensions = mock(ExtensionGetter.class);
    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);
    private final AiModelService models = mock(AiModelService.class);
    private final CommentNextStyleAssistant assistant = new CommentNextStyleAssistant(extensions, settings);

    @BeforeEach
    void provideModelService() {
        when(extensions.getEnabledExtension(AiModelService.class)).thenReturn(Mono.just(models));
    }

    @Test
    void generatesWithTheExplicitlySelectedModel() {
        var selected = mock(LanguageModel.class);
        when(models.languageModel("style-alternate")).thenReturn(Mono.just(selected));
        when(selected.generateText(any(GenerateTextRequest.class)))
            .thenReturn(Mono.just(GenerateTextResult.builder()
                .output(Map.of("summary", "缩小圆角", "changes", Map.of("radius", 6)))
                .build()));

        assertThat(assistant.availability("style-alternate").block().available()).isTrue();
        assertThat(assistant.suggest(CommentNextStyle.DEFAULT, "缩小圆角", "style-alternate").block().draft().radius())
            .isEqualTo(6);
        verifyNoInteractions(settings);
        verify(models, never()).languageModel();
    }

    @Test
    void keepsSelectionAvailableWhenTheDefaultModelIsMissing() {
        when(settings.getAiConfig()).thenReturn(Mono.just(SettingConfigGetter.AiConfig.empty()));
        when(models.languageModel()).thenReturn(Mono.error(new DefaultModelNotConfiguredException("language")));

        var availability = assistant.availability("").block();

        assertThat(availability.available()).isFalse();
        assertThat(availability.canChooseModel()).isTrue();
    }

    @Test
    void usesTheConfiguredCommentModelWhenSelectionIsCleared() {
        var config = SettingConfigGetter.AiConfig.empty().setLanguageModelName("comment-default");
        when(settings.getAiConfig()).thenReturn(Mono.just(config));
        when(models.languageModel("comment-default")).thenReturn(Mono.just(mock(LanguageModel.class)));

        assertThat(assistant.availability("").block().available()).isTrue();
        verify(models, never()).languageModel();
    }

    @Test
    void rejectsAnUnavailableSelectionWithoutFallingBack() {
        when(models.languageModel("removed-model"))
            .thenReturn(Mono.error(new ModelNotFoundException("removed-model")));

        var availability = assistant.availability("removed-model").block();
        assertThat(availability.available()).isFalse();
        assertThat(availability.canChooseModel()).isTrue();
        assertThatThrownBy(() -> assistant.suggest(CommentNextStyle.DEFAULT, "缩小圆角", "removed-model").block())
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(error.getReason()).contains("所选模型不可用");
            });
        verifyNoInteractions(settings);
        verify(models, never()).languageModel();
    }
}
