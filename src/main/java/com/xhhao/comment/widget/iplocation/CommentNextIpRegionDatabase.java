package com.xhhao.comment.widget.iplocation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.service.Config;
import org.lionsoul.ip2region.service.Ip2Region;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 内置 ip2region 离线库（IPv4 + IPv6），用于在线接口超时、限流或不可用时兜底。
 *
 * <p>xdb 数据文件随插件打包，首次查询时提取到临时目录，并以向量索引（VIndexCache）方式加载，
 * 避免把几十 MB 数据常驻内存。
 */
@Slf4j
@Component
class CommentNextIpRegionDatabase {

    private static final String XDB_DIRECTORY = "comment-next-ip2region";

    private static final String V4_RESOURCE = "/ip2region/ip2region_v4.xdb";

    private static final String V6_RESOURCE = "/ip2region/ip2region_v6.xdb";

    private static final int SEARCHER_POOL_SIZE = 4;

    private final AtomicBoolean initializationFailed = new AtomicBoolean();

    private volatile Ip2Region ip2Region;

    Mono<String> lookup(String ipAddress) {
        if (initializationFailed.get()) {
            return Mono.just("");
        }

        return Mono.fromCallable(() -> search(ipAddress))
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(error -> {
                log.debug("Failed to resolve location from ip2region. ip={}", ipAddress, error);
                return Mono.just("");
            });
    }

    private String search(String ipAddress) {
        var database = database();
        if (database == null) {
            return "";
        }

        try {
            return CommentNextIpLocationText.fromIp2Region(database.search(ipAddress));
        } catch (Exception e) {
            log.debug("ip2region lookup failed. ip={}", ipAddress, e);
            return "";
        }
    }

    private Ip2Region database() {
        var cached = ip2Region;
        if (cached != null) {
            return cached;
        }

        synchronized (this) {
            if (ip2Region != null || initializationFailed.get()) {
                return ip2Region;
            }

            try {
                var directory = Path.of(System.getProperty("java.io.tmpdir"), XDB_DIRECTORY);
                Files.createDirectories(directory);

                var v4Config = Config.custom()
                    .setXdbFile(extract(V4_RESOURCE, directory.resolve("ip2region_v4.xdb")))
                    .setCachePolicy(Config.VIndexCache)
                    .setSearchers(SEARCHER_POOL_SIZE)
                    .asV4();
                var v6Config = Config.custom()
                    .setXdbFile(extract(V6_RESOURCE, directory.resolve("ip2region_v6.xdb")))
                    .setCachePolicy(Config.VIndexCache)
                    .setSearchers(SEARCHER_POOL_SIZE)
                    .asV6();

                ip2Region = Ip2Region.create(v4Config, v6Config);
                log.info("Loaded built-in ip2region database from {}", directory);
            } catch (Exception e) {
                initializationFailed.set(true);
                log.warn("Failed to load built-in ip2region database, IP location will rely on "
                    + "the configured online API only.", e);
            }
            return ip2Region;
        }
    }

    private File extract(String resource, Path target) throws IOException {
        var existing = target.toFile();
        if (existing.isFile() && existing.length() > 0) {
            return existing;
        }

        try (InputStream inputStream = CommentNextIpRegionDatabase.class
            .getResourceAsStream(resource)) {
            if (inputStream == null) {
                throw new IOException("找不到内置 IP 库资源：" + resource);
            }

            var temporary = Files.createTempFile(
                target.getParent(),
                target.getFileName().toString(),
                ".part"
            );
            Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target.toFile();
    }
}
