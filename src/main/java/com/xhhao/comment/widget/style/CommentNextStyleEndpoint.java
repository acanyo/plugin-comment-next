package com.xhhao.comment.widget.style;

import com.fasterxml.jackson.databind.JsonNode;
import com.xhhao.comment.widget.style.CommentNextStyleAssistant.Availability;
import com.xhhao.comment.widget.style.CommentNextStyleStore.Operation;
import com.xhhao.comment.widget.style.CommentNextStyleStore.State;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class CommentNextStyleEndpoint implements CustomEndpoint {

    private final CommentNextStyleStore store;
    private final ObjectProvider<CommentNextStyleAssistant> assistant;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("comment-style/state", this::read)
            .PUT("comment-style/state", this::change)
            .POST("comment-style-suggestions", this::suggest)
            .build();
    }

    private Mono<ServerResponse> read(ServerRequest request) {
        var model = assistant.getIfAvailable();
        var availability = model == null
            ? Mono.just(CommentNextStyleAssistant.unavailable())
            : model.availability(request.queryParam("modelName").orElse("").strip());
        return Mono.zip(store.current(), availability, StyleWorkbench::new)
            .flatMap(this::respond);
    }

    private Mono<ServerResponse> change(ServerRequest request) {
        return body(request).flatMap(payload -> {
            var version = payload.path("revision");
            if (!version.isIntegralNumber() || !version.canConvertToLong()) {
                throw new ServerWebInputException("请重新读取当前样式版本。");
            }
            var operation = switch (payload.path("operation").asText()) {
                case "save" -> Operation.SAVE;
                case "publish" -> Operation.PUBLISH;
                case "rollback" -> Operation.ROLLBACK;
                default -> throw new ServerWebInputException("无效的样式操作。");
            };
            var draft = operation == Operation.ROLLBACK ? null : CommentNextStyle.read(payload.get("draft"));
            return store.change(version.longValue(), operation, draft);
        }).onErrorMap(IllegalArgumentException.class, error -> new ServerWebInputException(error.getMessage()))
            .flatMap(this::respond);
    }

    private Mono<ServerResponse> suggest(ServerRequest request) {
        var model = assistant.getIfAvailable();
        if (model == null) {
            return Mono.error(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                CommentNextStyleAssistant.unavailable().reason()));
        }
        return body(request).flatMap(payload -> {
            var text = payload.path("instruction").asText("").strip();
            if (text.isBlank() || text.length() > 1200) {
                throw new ServerWebInputException("请输入 1 到 1200 字的修改需求。");
            }
            var draft = CommentNextStyle.read(payload.get("draft"));
            var selected = payload.path("modelName");
            if (!selected.isMissingNode() && !selected.isNull() && !selected.isTextual()) {
                throw new ServerWebInputException("请选择有效的语言模型。");
            }
            return model.suggest(draft, text, selected.asText("").strip());
        }).onErrorMap(IllegalArgumentException.class, error -> new ServerWebInputException(error.getMessage()))
            .flatMap(this::respond);
    }

    private Mono<JsonNode> body(ServerRequest request) {
        return request.bodyToMono(JsonNode.class)
            .filter(JsonNode::isObject)
            .switchIfEmpty(Mono.error(new ServerWebInputException("请提交有效的工作台数据。")));
    }

    private Mono<ServerResponse> respond(Object result) {
        return ServerResponse.ok().cacheControl(CacheControl.noStore()).bodyValue(result);
    }

    record StyleWorkbench(State state, Availability assistant) {
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.commentnext.xhhao.com", "v1alpha1");
    }
}
