package com.xhhao.comment.widget.emote;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class CommentNextEmoteAssetFetcherTest {

    @Test
    void convertsUnicodeAndSpacePathsToValidAsciiUriWithoutDoubleEncoding() {
        var unicodeUri = CommentNextEmoteAssetFetcher.toSafeUri(
            "//raw.githubusercontent.com/SwaggyMacro/OwO_Stickers/refs/heads/main/stickers/alu/暗地观察@2x.png"
        );
        assertThat(unicodeUri.toASCIIString())
            .isEqualTo("https://raw.githubusercontent.com/SwaggyMacro/OwO_Stickers/refs/heads/main/stickers/alu/%E6%9A%97%E5%9C%B0%E8%A7%82%E5%AF%9F@2x.png");

        var alreadyEncoded = CommentNextEmoteAssetFetcher.toSafeUri(
            "https://example.com/stickers/%E6%9A%97%E5%9C%B0%201.png?v=1"
        );
        assertThat(alreadyEncoded.toASCIIString())
            .isEqualTo("https://example.com/stickers/%E6%9A%97%E5%9C%B0%201.png?v=1");
    }

    @Test
    void rejectsInvalidUrls() {
        assertThatThrownBy(() -> CommentNextEmoteAssetFetcher.toSafeUri("not-a-url"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsSupportedImageMagicBytesAndRejectsUnsafeTypes() {
        var png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00};
        var jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
        var gif = "GIF89a...".getBytes(StandardCharsets.US_ASCII);
        var webp = "RIFF\0\0\0\0WEBP".getBytes(StandardCharsets.US_ASCII);
        var svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(StandardCharsets.UTF_8);

        assertThat(CommentNextEmoteAssetFetcher.detectImageType(png, MediaType.APPLICATION_OCTET_STREAM))
            .isEqualTo(new CommentNextEmoteAssetFetcher.ImageType(MediaType.IMAGE_PNG, "png"));
        assertThat(CommentNextEmoteAssetFetcher.detectImageType(jpeg, null))
            .isEqualTo(new CommentNextEmoteAssetFetcher.ImageType(MediaType.IMAGE_JPEG, "jpg"));
        assertThat(CommentNextEmoteAssetFetcher.detectImageType(gif, null))
            .isEqualTo(new CommentNextEmoteAssetFetcher.ImageType(MediaType.IMAGE_GIF, "gif"));
        assertThat(CommentNextEmoteAssetFetcher.detectImageType(webp, null))
            .isEqualTo(new CommentNextEmoteAssetFetcher.ImageType(MediaType.parseMediaType("image/webp"), "webp"));
        assertThat(CommentNextEmoteAssetFetcher.detectImageType(svg, MediaType.parseMediaType("image/svg+xml")))
            .isNull();
    }

    @Test
    void allowsDomainFakeIpAddressesWhileBlockingLiteralAndPrivateTargets() throws Exception {
        var fakeIp = InetAddress.getByName("198.18.0.62");
        var fakeIpv6Ula = InetAddress.getByName("fc00::1234");
        var publicIp = InetAddress.getByName("8.8.8.8");
        var loopback = InetAddress.getByName("127.0.0.1");
        var lanIp = InetAddress.getByName("192.168.1.10");
        var metadataIp = InetAddress.getByName("169.254.169.254");
        var cgnatIp = InetAddress.getByName("100.100.100.200");

        assertThat(CommentNextEmoteAssetFetcher.isAllowedConnectAddress(fakeIp)).isTrue();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedConnectAddress(publicIp)).isTrue();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedConnectAddress(loopback)).isFalse();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedConnectAddress(lanIp)).isFalse();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedConnectAddress(metadataIp)).isFalse();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedConnectAddress(cgnatIp)).isFalse();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedConnectAddress(fakeIpv6Ula)).isFalse();

        assertThat(CommentNextEmoteAssetFetcher.isAllowedResolvedAddress("owo.twouu.com", fakeIp)).isTrue();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedResolvedAddress("198.18.0.62", fakeIp)).isFalse();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedResolvedAddress("owo.twouu.com", publicIp)).isTrue();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedResolvedAddress("owo.twouu.com", loopback)).isFalse();
        assertThat(CommentNextEmoteAssetFetcher.isAllowedResolvedAddress("owo.twouu.com", lanIp)).isFalse();

        assertThatCode(() -> CommentNextEmoteAssetFetcher.validateResolvedAddresses(
            "owo.twouu.com",
            new InetAddress[] {fakeIp, fakeIpv6Ula}
        )).doesNotThrowAnyException();

        assertThatThrownBy(() -> CommentNextEmoteAssetFetcher.validateResolvedAddresses(
            "198.18.0.62",
            new InetAddress[] {fakeIp}
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("网址解析到了非公网地址");

        assertThatThrownBy(() -> CommentNextEmoteAssetFetcher.validateResolvedAddresses(
            "evil.example.com",
            new InetAddress[] {fakeIp, loopback}
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("网址解析到了非公网地址");

        assertThatThrownBy(() -> CommentNextEmoteAssetFetcher.validateResolvedAddresses(
            "evil.example.com",
            new InetAddress[] {metadataIp}
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("网址解析到了非公网地址");

        assertThatThrownBy(() -> CommentNextEmoteAssetFetcher.validateResolvedAddresses(
            "evil.example.com",
            new InetAddress[] {cgnatIp}
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("网址解析到了非公网地址");

        assertThatThrownBy(() -> CommentNextEmoteAssetFetcher.validateResolvedAddresses(
            "evil.example.com",
            new InetAddress[] {fakeIpv6Ula}
        )).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("网址解析到了非公网地址");
    }
}
