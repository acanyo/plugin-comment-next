package com.xhhao.comment.widget.iplocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.xhhao.comment.utils.JsonUtils;
import com.xhhao.comment.widget.SettingConfigGetter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class CommentNextIpLocationServiceTest {

    private final CommentNextIpLocationClient client = mock(CommentNextIpLocationClient.class);

    private final SettingConfigGetter settings = mock(SettingConfigGetter.class);

    private final SettingConfigGetter.IpLocationConfig config = new SettingConfigGetter.IpLocationConfig()
        .setOnlineLookupEnabled(true);

    private final CommentNextIpLocationService service = new CommentNextIpLocationService(
        client, new CommentNextIpLocationCache(), settings);

    @BeforeEach
    void setUp() {
        when(settings.getIpLocationConfig()).thenReturn(Mono.just(config));
        when(client.fetchLocation(anyString(), anyString(), any())).thenReturn(Mono.empty());
    }

    @Test
    void upgradingFromOfflineSettingsKeepsOnlineQueriesOff() throws Exception {
        var previousSettings = JsonUtils.createObjectMapper().readValue(
            """
            {"enabled":true,"offlineFallback":false}
            """,
            SettingConfigGetter.IpLocationConfig.class);
        when(settings.getIpLocationConfig()).thenReturn(Mono.just(previousSettings));

        assertEquals("", service.resolve("8.8.8.8").block(Duration.ofMillis(500)));
        verifyNoInteractions(client);
    }

    @Test
    void defaultsToNoOnlineQueries() {
        when(settings.getIpLocationConfig()).thenReturn(Mono.just(new SettingConfigGetter.IpLocationConfig()));

        assertEquals("", service.resolve("8.8.8.8").block(Duration.ofMillis(500)));
        verifyNoInteractions(client);
    }

    @Test
    void defaultProviderSkipsIpv6Lookups() {
        assertEquals("", service.resolve("2001:4860::8888").block());
        verifyNoInteractions(client);
    }

    @Test
    void cachesTheOnlineResultForEquivalentIpv6Addresses() {
        config.setApiUrlTemplate("https://dual-stack.example.test/?ip={ip}");
        when(client.fetchLocation(anyString(), anyString(), any())).thenReturn(Mono.just("日本"));

        assertEquals("日本", service.resolve("2001:4860:0:0:0:0:0:8888").block());
        assertEquals("日本", service.resolve("2001:4860::8888").block());
        verify(client).fetchLocation(eq("2001:4860::8888"), anyString(), eq(Duration.ofMillis(3000)));
    }

    @Test
    void separatesCachedResultsByProviderTemplate() {
        var firstProvider = "https://first.example.test/?ip={ip}";
        var secondProvider = "https://second.example.test/?ip={ip}";
        when(client.fetchLocation(anyString(), eq(firstProvider), any())).thenReturn(Mono.just("美国"));
        when(client.fetchLocation(anyString(), eq(secondProvider), any())).thenReturn(Mono.just("日本"));

        config.setApiUrlTemplate(firstProvider);
        assertEquals("美国", service.resolve("8.8.8.8").block());
        config.setApiUrlTemplate(secondProvider);
        assertEquals("日本", service.resolve("8.8.8.8").block());
        config.setApiUrlTemplate(firstProvider);
        assertEquals("美国", service.resolve("8.8.8.8").block());
        verify(client).fetchLocation(anyString(), eq(firstProvider), any());
        verify(client).fetchLocation(anyString(), eq(secondProvider), any());
    }

    @Test
    void disabledFeatureAndPrivateAddressesDoNotPerformLookups() {
        config.setEnabled(false);
        assertEquals("", service.resolve("8.8.8.8").block());
        config.setEnabled(true);
        for (var ip : new String[] {"127.0.0.1", "192.168.1.1", "fd12::1", "not-an-ip", ""}) {
            assertEquals("", service.resolve(ip).block());
        }
        verifyNoInteractions(client);
    }

    @Test
    void failedOnlineRequestReturnsEmptyAndIsNegativeCached() {
        when(client.fetchLocation(anyString(), anyString(), any()))
            .thenReturn(Mono.error(new IllegalStateException("unavailable")));

        assertEquals("", service.resolve("8.8.8.8").block());
        assertEquals("", service.resolve("8.8.8.8").block());
        verify(client, times(1)).fetchLocation(anyString(), anyString(), any());
    }

    @Test
    void concurrentSubscribersShareOneOnlineRequest() {
        var response = Sinks.<String>one();
        when(client.fetchLocation(anyString(), anyString(), any())).thenReturn(response.asMono());

        var first = service.resolve("8.8.8.8").toFuture();
        var second = service.resolve("8.8.8.8").toFuture();
        response.tryEmitValue("美国");

        assertEquals("美国", first.join());
        assertEquals("美国", second.join());
        verify(client, times(1)).fetchLocation(anyString(), anyString(), any());
    }

    @Test
    void saturatedOnlineRequestsDoNotCacheSkippedAddresses() {
        var response = Sinks.<String>one();
        when(client.fetchLocation(anyString(), anyString(), any())).thenReturn(response.asMono());
        var pending = new ArrayList<CompletableFuture<String>>();
        for (int i = 1; i <= 8; i++) {
            pending.add(service.resolve("8.8.8." + i).toFuture());
        }
        assertEquals("", service.resolve("8.8.8.9").block(Duration.ofMillis(500)));
        verify(client, never()).fetchLocation(eq("8.8.8.9"), anyString(), any());

        response.tryEmitValue("美国");
        pending.forEach(future -> assertEquals("美国", future.join()));
        assertEquals("美国", service.resolve("8.8.8.9").block(Duration.ofMillis(500)));
        verify(client, times(9)).fetchLocation(anyString(), anyString(), any());
    }
}
