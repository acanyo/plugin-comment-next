package com.xhhao.comment.widget.emote;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class CommentNextEmoteHtmlUrlCodecTest {

    @Test
    void extractsOriginBeforeSrcAndNormalizesProtocolRelativeUrls() {
        var html = "<img src=\"//cdn.example.com/thumb.png\" origin=\"//raw.githubusercontent.com/user/repo/main/暗地观察@2x.png\" alt=\"暗地观察\" class=\"OwO-img\">";

        assertThat(CommentNextEmoteHtmlUrlCodec.extractPreferredUrl(html))
            .isEqualTo("https://raw.githubusercontent.com/user/repo/main/暗地观察@2x.png");
    }

    @Test
    void fallsBackToSrcWhenOriginIsAbsent() {
        var html = "<img src=\"//raw.githubusercontent.com/user/repo/main/smile.png\" alt=\"smile\">";

        assertThat(CommentNextEmoteHtmlUrlCodec.extractPreferredUrl(html))
            .isEqualTo("https://raw.githubusercontent.com/user/repo/main/smile.png");
    }

    @Test
    void detectsExternalVsLocalAndSameOriginUrls() {
        var site = URI.create("https://blog.example.com/");

        assertThat(CommentNextEmoteHtmlUrlCodec.isExternalUrl("/upload/emote-1.png", site)).isFalse();
        assertThat(CommentNextEmoteHtmlUrlCodec.isExternalUrl("https://blog.example.com/upload/emote-1.png", site)).isFalse();
        assertThat(CommentNextEmoteHtmlUrlCodec.isExternalUrl("data:image/png;base64,AAA", site)).isFalse();
        assertThat(CommentNextEmoteHtmlUrlCodec.isExternalUrl("https://raw.githubusercontent.com/a/b.png", site)).isTrue();
        assertThat(CommentNextEmoteHtmlUrlCodec.isExternalUrl("//cdn.other.com/a/暗地观察.png", site)).isTrue();
    }

    @Test
    void replacesSrcRemovesOriginAndPreservesOtherAttributes() {
        var html = "<img src=\"//thumb.example.com/1.png\" origin=\"//origin.example.com/1.png\" alt=\"开心\" class=\"OwO-img\">";

        var updated = CommentNextEmoteHtmlUrlCodec.replaceImageUrl(html, "https://blog.example.com/upload/emote-1.png");

        assertThat(updated).contains("src=\"https://blog.example.com/upload/emote-1.png\"");
        assertThat(updated).doesNotContain("origin=");
        assertThat(updated).contains("alt=\"开心\"");
        assertThat(updated).contains("class=\"OwO-img\"");
    }
}
