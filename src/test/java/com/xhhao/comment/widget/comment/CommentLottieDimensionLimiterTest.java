package com.xhhao.comment.widget.comment;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

class CommentLottieDimensionLimiterTest {

    @Test
    void scalesBothDimensionsToFitConfiguredBounds() {
        var result = CommentLottieDimensionLimiter.limit(
            "<p>Hi</p><img src=\"https://cdn.example.com/a.lottie\" width=\"1024\" height=\"512\">",
            512,
            256
        );

        var image = Jsoup.parseBodyFragment(result).body().selectFirst("img");
        assertThat(image).isNotNull();
        assertThat(image.attr("width")).isEqualTo("512");
        assertThat(image.attr("height")).isEqualTo("256");
    }

    @Test
    void preservesAspectRatioWhenOnlyOneDimensionExceedsLimit() {
        var result = CommentLottieDimensionLimiter.limit(
            "<img src=\"https://cdn.example.com/a.lottie\" width=\"1024\" height=\"100\">",
            512,
            512
        );

        var image = Jsoup.parseBodyFragment(result).body().selectFirst("img");
        assertThat(image).isNotNull();
        assertThat(image.attr("width")).isEqualTo("512");
        assertThat(image.attr("height")).isEqualTo("50");
    }

    @Test
    void leavesNonLottieImagesAndWithinBoundsLottieUnchanged() {
        var html = "<img src=\"https://cdn.example.com/a.png\" width=\"1024\" height=\"1024\">"
            + "<img src=\"https://cdn.example.com/a.lottie\" width=\"512\" height=\"512\">";

        assertThat(CommentLottieDimensionLimiter.limit(html, 512, 512)).isEqualTo(html);
    }
}
