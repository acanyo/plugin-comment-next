package com.xhhao.comment.widget.iplocation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.xhhao.comment.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class CommentNextIpLocationResponseParserTest {

    @Test
    void readsProvinceForChineseIpFromIpApiResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "status": "success",
              "country": "中国",
              "countryCode": "CN",
              "region": "GD",
              "regionName": "广东",
              "city": "深圳市"
            }
            """);

        assertEquals("广东", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void readsCountryForOverseasIpFromIpApiResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "status": "success",
              "country": "日本",
              "countryCode": "JP",
              "regionName": "东京都",
              "city": "东京"
            }
            """);

        assertEquals("日本", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void fallsBackToCityWhenProvinceMissing() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "status": "success",
              "country": "中国",
              "countryCode": "CN",
              "city": "北京市"
            }
            """);

        assertEquals("北京", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void readsChineseLocationFromPconlineResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "ip": "116.22.13.7",
              "pro": "广东省",
              "proCode": "440000",
              "city": "广州市",
              "cityCode": "440100",
              "region": "天河区",
              "regionCode": "440106",
              "addr": "广东省广州市天河区 电信",
              "regionNames": "",
              "err": ""
            }
            """);

        assertEquals("广东", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void readsCountryFromPconlineOverseasResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "ip": "8.8.8.8",
              "pro": "",
              "proCode": "999999",
              "city": "",
              "region": "",
              "addr": " 美国",
              "err": "noprovince"
            }
            """);

        assertEquals("美国", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void readsLocationFromMir6ResponseWrappedInData() throws Exception {
        var objectMapper = JsonUtils.createObjectMapper();

        var chinese = objectMapper.readTree("""
            {
              "code": 200,
              "msg": "success",
              "data": {
                "ip": "116.22.13.7",
                "country": "中国",
                "countryCode": "CN",
                "province": "广东省",
                "city": "广州市",
                "location": "中国[CN] 广东省 广州市 南沙区"
              }
            }
            """);
        assertEquals("广东", CommentNextIpLocationResponseParser.location(chinese));

        var overseas = objectMapper.readTree("""
            {
              "code": 200,
              "msg": "success",
              "data": {
                "country": "美国",
                "countryCode": "US",
                "province": "堪萨斯州",
                "city": "里诺县"
              }
            }
            """);
        assertEquals("美国", CommentNextIpLocationResponseParser.location(overseas));
    }

    @Test
    void readsLocationFromZxincResponseWrappedInData() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "code": 0,
              "data": {
                "location": "中国–广东–广州–天河区 电信",
                "country": "中国–广东–广州–天河区",
                "local": "电信"
              }
            }
            """);

        assertEquals("广东", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void readsLocationFromChineseAddressText() throws Exception {
        var objectMapper = JsonUtils.createObjectMapper();

        assertEquals(
            "广东",
            CommentNextIpLocationResponseParser.location(
                objectMapper.readTree("{\"addr\":\"广东省广州市天河区 电信\"}")
            )
        );
        assertEquals(
            "美国",
            CommentNextIpLocationResponseParser.location(
                objectMapper.readTree("{\"addr\":\" 美国\"}")
            )
        );
    }

    @Test
    void rejectsNonSuccessCodes() throws Exception {
        var objectMapper = JsonUtils.createObjectMapper();

        assertEquals(
            "",
            CommentNextIpLocationResponseParser.location(objectMapper.readTree("""
                {"code":404,"msg":"not found"}
                """))
        );
    }

    @Test
    void readsLocationFromIpWhoIsResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "success": true,
              "country": "中国",
              "country_code": "CN",
              "region": "广东",
              "city": "广州"
            }
            """);

        assertEquals("广东", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void readsLocationFromIpApiCoResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "country_name": "China",
              "country_code": "CN",
              "region": "Guangdong",
              "city": "Shenzhen"
            }
            """);

        assertEquals("Guangdong", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void rejectsFailureResponses() throws Exception {
        var objectMapper = JsonUtils.createObjectMapper();

        assertEquals(
            "",
            CommentNextIpLocationResponseParser.location(objectMapper.readTree("""
                {"status":"fail","message":"reserved range","query":"127.0.0.1"}
                """))
        );
        assertEquals(
            "",
            CommentNextIpLocationResponseParser.location(objectMapper.readTree("""
                {"success":false,"message":"invalid IP address"}
                """))
        );
        assertEquals(
            "",
            CommentNextIpLocationResponseParser.location(objectMapper.readTree("""
                {"error":true,"reason":"RateLimited"}
                """))
        );
    }

    @Test
    void readsLocationFromIpInfoResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "city": "Guangzhou",
              "region": "Guangdong",
              "country": "CN",
              "org": "AS4134 CHINANET BACKBONE"
            }
            """);

        assertEquals("Guangdong", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void keepsOverseasProvinceWhenOnlyCountryCodeIsGiven() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {"region": "California", "country": "US"}
            """);

        assertEquals("California", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void fallsBackToCountryCodeWhenNoRegionIsGiven() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {"country": "JP"}
            """);

        assertEquals("JP", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void keepsChineseCountryNameAsDisplayValue() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {"country": "中国"}
            """);

        assertEquals("中国", CommentNextIpLocationResponseParser.location(response));
    }

    @Test
    void rejectsEmptyAndMalformedResponses() throws Exception {
        var objectMapper = JsonUtils.createObjectMapper();

        assertEquals("", CommentNextIpLocationResponseParser.location(null));
        assertEquals(
            "",
            CommentNextIpLocationResponseParser.location(objectMapper.readTree("{}"))
        );
        assertEquals(
            "",
            CommentNextIpLocationResponseParser.location(objectMapper.readTree("[]"))
        );
        assertEquals(
            "",
            CommentNextIpLocationResponseParser.location(objectMapper.readTree("\"中国\""))
        );
    }

    @Test
    void trimsAndTruncatesOverlongLocation() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {"country":"中国","countryCode":"CN","regionName":"  广东   深圳  "}
            """);

        assertEquals("广东 深圳", CommentNextIpLocationResponseParser.location(response));

        var longRegion = JsonUtils.createObjectMapper().readTree("""
            {"country":"中国","countryCode":"CN","regionName":"%s"}
            """.formatted("深".repeat(80)));

        assertEquals(
            48,
            CommentNextIpLocationResponseParser.location(longRegion).length()
        );
    }
}
