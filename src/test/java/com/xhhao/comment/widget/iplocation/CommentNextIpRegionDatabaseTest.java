package com.xhhao.comment.widget.iplocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 验证内置 ip2region 离线库（IPv4 + IPv6）能给出中文属地。
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommentNextIpRegionDatabaseTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private CommentNextIpRegionDatabase database;

    @BeforeAll
    void setUp() {
        database = new CommentNextIpRegionDatabase();
    }

    @Test
    void resolvesChineseProvinceForChineseAddresses() {
        assertEquals("广东", database.lookup("116.22.13.7").block(TIMEOUT));
        assertEquals("北京", database.lookup("240e:ec:6659:13c0:ed12:d679:85bb:44e7").block(TIMEOUT));
    }

    @Test
    void resolvesChineseCountryNameForOverseasAddress() {
        assertEquals("美国", database.lookup("8.8.8.8").block(TIMEOUT));
        assertEquals("美国", database.lookup("2001:4860:4860::8888").block(TIMEOUT));
        assertEquals("日本", database.lookup("133.242.0.1").block(TIMEOUT));
    }

    @Test
    void ignoresInvalidAddress() {
        assertEquals("", database.lookup("not-an-ip").block(TIMEOUT));
    }
}
