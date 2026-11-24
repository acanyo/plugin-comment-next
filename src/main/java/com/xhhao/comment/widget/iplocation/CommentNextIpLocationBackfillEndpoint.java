package com.xhhao.comment.widget.iplocation;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;

@Component
@RequiredArgsConstructor
public class CommentNextIpLocationBackfillEndpoint implements CustomEndpoint {

    private final CommentNextIpLocationBackfill backfill;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return RouterFunctions.route()
            .GET("ip-location-backfill", request -> backfill.status().flatMap(status ->
                ServerResponse.ok().cacheControl(CacheControl.noStore()).bodyValue(status)))
            .POST("ip-location-backfill", request -> backfill.start().flatMap(status ->
                ServerResponse.accepted().cacheControl(CacheControl.noStore()).bodyValue(status)))
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return new GroupVersion("console.api.commentnext.xhhao.com", "v1alpha1");
    }
}
