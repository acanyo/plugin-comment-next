package com.xhhao.comment.widget.iplocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class CommentNextIpLocationClientTest {

    private static final Charset GBK = Charset.forName("GBK");

    private static final String REQUESTED_IP = "116.22.13.7";

    @Test
    void decodesGbkBodyLikePconline() {
        var body = """
            {"ip":"116.22.13.7","pro":"广东省","city":"广州市","region":"天河区"}
            """.getBytes(GBK);

        assertEquals(
            "广东",
            CommentNextIpLocationClient.locationFromBody(body, GBK, REQUESTED_IP)
        );
    }

    @Test
    void decodesUtf8BodyWithoutDeclaredCharset() {
        var body = """
            {"code":200,"data":{"country":"中国","province":"广东省","city":"广州市"}}
            """.getBytes(StandardCharsets.UTF_8);

        assertEquals("广东", CommentNextIpLocationClient.locationFromBody(body, null, REQUESTED_IP));
    }

    @Test
    void skipsBomAndLeadingNewlines() {
        var body = ("\n\n\uFEFF{\"pro\":\"广东省\"}").getBytes(StandardCharsets.UTF_8);

        assertEquals("广东", CommentNextIpLocationClient.locationFromBody(body, null, REQUESTED_IP));
    }

    @Test
    void ignoresNonJsonBody() {
        assertEquals(
            "",
            CommentNextIpLocationClient.locationFromBody(
                "<html>error</html>".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8, REQUESTED_IP
            )
        );
        assertEquals("", CommentNextIpLocationClient.locationFromBody(new byte[0], null, REQUESTED_IP));
        assertEquals("", CommentNextIpLocationClient.locationFromBody(null, null, REQUESTED_IP));
        assertEquals(
            "",
            CommentNextIpLocationClient.locationFromBody(
                "{ not json }".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8, REQUESTED_IP
            )
        );
    }

    @Test
    void discardsAResultForADifferentAddress() {
        var body = """
            {"ip":"192.0.2.1","pro":"北京市"}
            """.getBytes(GBK);

        assertEquals("", CommentNextIpLocationClient.locationFromBody(body, GBK, "2001:4860::8888"));
    }

    @Test
    void acceptsEquivalentIpv6NotationsInWrappedResponses() {
        var body = """
            {"code":200,"data":{"ip":"2001:4860:0:0:0:0:0:8888","country":"美国"}}
            """.getBytes(StandardCharsets.UTF_8);

        assertEquals("美国", CommentNextIpLocationClient.locationFromBody(body, null, "2001:4860::8888"));
        assertEquals("", CommentNextIpLocationClient.locationFromBody(body, null, "2001:4860::8844"));
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

    @Test
    void requiresHttpsToSendTheCommenterIp() {
        assertThrows(IllegalArgumentException.class, () -> CommentNextIpLocationClient.locationUri(
            "192.0.2.1", "http://example.test/?ip={ip}"));
    }
}
