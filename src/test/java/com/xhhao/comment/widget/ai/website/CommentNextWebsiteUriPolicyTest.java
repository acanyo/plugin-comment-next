package com.xhhao.comment.widget.ai.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import org.junit.jupiter.api.Test;

class CommentNextWebsiteUriPolicyTest {

    private final CommentNextWebsiteUriPolicy policy = new CommentNextWebsiteUriPolicy();

    @Test
    void normalizesWebsiteToOriginRoot() {
        assertEquals(
            "https://example.com/",
            policy.normalizeWebsiteRoot("example.com/promotions?source=comment").toASCIIString()
        );
        assertEquals(
            "http://example.com/",
            policy.normalizeWebsiteRoot("HTTP://Example.COM:80/path").toASCIIString()
        );
    }

    @Test
    void rejectsCredentialsAndNonStandardPorts() {
        assertThrows(
            IllegalArgumentException.class,
            () -> policy.normalizeWebsiteRoot("https://user:password@example.com/")
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> policy.normalizeWebsiteRoot("https://example.com:8443/")
        );
    }

    @Test
    void rejectsPrivateAndReservedAddresses() throws Exception {
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("10.0.0.1")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("100.64.0.1")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("169.254.169.254")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("192.168.1.1")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("198.51.100.1")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("203.0.113.1")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("::1")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("fc00::1")));
        assertFalse(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("2001:db8::1")));
        assertTrue(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(CommentNextWebsiteUriPolicy.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void blocksPrivateTargetsBeforeHttpFetch() {
        assertThrows(
            IllegalArgumentException.class,
            () -> policy.validateOutbound(URI.create("http://127.0.0.1/")).block()
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> policy.validateOutbound(URI.create("http://169.254.169.254/")).block()
        );
    }
}
