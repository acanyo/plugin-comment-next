package com.xhhao.comment.widget.iplocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CommentNextIpLocationClientTest {

    private static final Charset GBK = Charset.forName("GBK");

    @Test
    void decodesGbkBodyLikePconline() {
        var body = """
            {"ip":"116.22.13.7","pro":"广东省","city":"广州市","region":"天河区"}
            """.getBytes(GBK);

        assertEquals(
            "广东",
            CommentNextIpLocationClient.locationFromBody(body, GBK)
        );
    }

    @Test
    void decodesUtf8BodyWithoutDeclaredCharset() {
        var body = """
            {"code":200,"data":{"country":"中国","province":"广东省","city":"广州市"}}
            """.getBytes(StandardCharsets.UTF_8);

        assertEquals("广东", CommentNextIpLocationClient.locationFromBody(body, null));
    }

    @Test
    void skipsBomAndLeadingNewlines() {
        var body = ("\n\n\uFEFF{\"pro\":\"广东省\"}").getBytes(StandardCharsets.UTF_8);

        assertEquals("广东", CommentNextIpLocationClient.locationFromBody(body, null));
    }

    @Test
    void ignoresNonJsonBody() {
        assertEquals(
            "",
            CommentNextIpLocationClient.locationFromBody(
                "<html>error</html>".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            )
        );
        assertEquals("", CommentNextIpLocationClient.locationFromBody(new byte[0], null));
        assertEquals("", CommentNextIpLocationClient.locationFromBody(null, null));
        assertEquals(
            "",
            CommentNextIpLocationClient.locationFromBody(
                "{ not json }".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8
            )
        );
    }

    @Test
    void replacesOnlyTheConfiguredIpPlaceholder() {
        assertEquals(
            "https://whois.pconline.com.cn/ipJson.jsp?ip=116.22.13.7&json=true",
            CommentNextIpLocationClient.locationUri(
                "116.22.13.7",
                "https://whois.pconline.com.cn/ipJson.jsp?ip={ip}&json=true"
            ).toASCIIString()
        );
    }

    @Test
    void requiresIpPlaceholder() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CommentNextIpLocationClient.locationUri(
                "116.22.13.7",
                "https://whois.pconline.com.cn/ipJson.jsp"
            )
        );
    }

    @Test
    void rejectsBlankTemplate() {
        assertThrows(
            IllegalArgumentException.class,
            () -> CommentNextIpLocationClient.locationUri("116.22.13.7", "  ")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> CommentNextIpLocationClient.locationUri("116.22.13.7", null)
        );
    }
}
