package com.xhhao.comment.widget;

import java.util.Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.PropertyPlaceholderHelper;
import org.thymeleaf.context.ITemplateContext;
import org.thymeleaf.model.IModel;
import org.thymeleaf.model.IModelFactory;
import org.thymeleaf.processor.element.IElementModelStructureHandler;
import reactor.core.publisher.Mono;
import run.halo.app.plugin.PluginContext;
import run.halo.app.theme.dialect.TemplateHeadProcessor;

@Component
@RequiredArgsConstructor
public class CommentWidgetHeadProcessor implements TemplateHeadProcessor {

    static final PropertyPlaceholderHelper PROPERTY_PLACEHOLDER_HELPER = new PropertyPlaceholderHelper("${", "}");

    private final PluginContext pluginContext;

    @Override
    public Mono<Void> process(ITemplateContext context, IModel model,
                              IElementModelStructureHandler structureHandler) {
        final IModelFactory modelFactory = context.getModelFactory();
        model.add(modelFactory.createText(commentWidgetScript()));
        return Mono.empty();
    }

    private String commentWidgetScript() {

        final Properties properties = new Properties();
        properties.setProperty("version", pluginContext.getVersion());

        return PROPERTY_PLACEHOLDER_HELPER.replacePlaceholders("""
            <!-- plugin-comment-next start -->
            <link rel="stylesheet" href="/plugins/PluginCommentNext/assets/static/comment-next.css?version=${version}" />
            <script defer src="/plugins/PluginCommentNext/assets/static/comment-next.iife.js?version=${version}"></script>
            <!-- plugin-comment-next end -->
            """, properties);
    }
}
