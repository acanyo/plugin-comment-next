package com.xhhao.comment.widget.iplocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CommentNextIpLocationTextTest {

    @Test
    void trimsAdministrativeSuffixLikeDifferentApisWouldReturn() {
        assertEquals("广东", CommentNextIpLocationText.trimAdministrativeSuffix("广东省"));
        assertEquals("北京", CommentNextIpLocationText.trimAdministrativeSuffix("北京市"));
        assertEquals("内蒙古", CommentNextIpLocationText.trimAdministrativeSuffix("内蒙古自治区"));
        assertEquals("广西", CommentNextIpLocationText.trimAdministrativeSuffix("广西壮族自治区"));
        assertEquals("新疆", CommentNextIpLocationText.trimAdministrativeSuffix("新疆维吾尔自治区"));
        assertEquals("香港", CommentNextIpLocationText.trimAdministrativeSuffix("香港特别行政区"));
        assertEquals("美国", CommentNextIpLocationText.trimAdministrativeSuffix("美国"));
        assertEquals("加利福尼亚州", CommentNextIpLocationText.trimAdministrativeSuffix("加利福尼亚州"));
    }

    @Test
    void extractsProvinceFromChineseAddressText() {
        assertEquals("广东", CommentNextIpLocationText.regionFromText("广东省广州市天河区 电信"));
        assertEquals("广东", CommentNextIpLocationText.regionFromText("中国–广东–广州–天河区 电信"));
        assertEquals("美国", CommentNextIpLocationText.regionFromText(" 美国"));
        assertEquals("北京", CommentNextIpLocationText.regionFromText("北京市"));
        assertEquals("", CommentNextIpLocationText.regionFromText(""));
    }

    @Test
    void keepsCountryFromDashedOverseasText() {
        assertEquals(
            "美国",
            CommentNextIpLocationText.regionFromText(
                "美国–加利福尼亚州–圣克拉拉–山景城 谷歌公司DNS服务器"
            )
        );
    }

    @Test
    void recognizesChinaFromNameOrCountryCode() {
        assertTrue(CommentNextIpLocationText.isChina("中国", ""));
        assertTrue(CommentNextIpLocationText.isChina("China", ""));
        assertTrue(CommentNextIpLocationText.isChina("", "CN"));
        assertFalse(CommentNextIpLocationText.isChina("美国", "US"));
        assertFalse(CommentNextIpLocationText.isChina("", ""));
    }

    @Test
    void detectsCountryCodeOnlyForAsciiLetters() {
        assertTrue(CommentNextIpLocationText.isCountryCode("CN"));
        assertTrue(CommentNextIpLocationText.isCountryCode("chn"));
        assertFalse(CommentNextIpLocationText.isCountryCode("中国"));
        assertFalse(CommentNextIpLocationText.isCountryCode("United States"));
        assertFalse(CommentNextIpLocationText.isCountryCode("C"));
    }

    @Test
    void detectsChineseRegionSuffix() {
        assertTrue(CommentNextIpLocationText.looksChineseRegion("广东省"));
        assertTrue(CommentNextIpLocationText.looksChineseRegion("上海市"));
        assertFalse(CommentNextIpLocationText.looksChineseRegion("堪萨斯州"));
        assertFalse(CommentNextIpLocationText.looksChineseRegion(""));
    }

    @Test
    void normalizesAndTruncatesText() {
        assertEquals("广东 深圳", CommentNextIpLocationText.normalize("  广东   深圳  "));
        assertEquals("", CommentNextIpLocationText.normalize("0"));
        assertEquals("", CommentNextIpLocationText.normalize("   "));
        assertEquals(48, CommentNextIpLocationText.normalize("深".repeat(80)).length());
    }

    @Test
    void rejectsIpAddressesAndAcceptsRegionNamesAsLabels() {
        assertEquals("Québec", CommentNextIpLocationText.displayLabel(" Québec "));
        assertEquals("Côte d’Ivoire", CommentNextIpLocationText.displayLabel("Côte d’Ivoire"));
        assertEquals("", CommentNextIpLocationText.displayLabel("Japan 192.0.2.1"));
        assertEquals("", CommentNextIpLocationText.displayLabel("2001:db8::1"));
    }
}
