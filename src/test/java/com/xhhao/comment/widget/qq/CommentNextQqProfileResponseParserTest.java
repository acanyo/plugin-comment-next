package com.xhhao.comment.widget.qq;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.xhhao.comment.utils.JsonUtils;
import org.junit.jupiter.api.Test;

class CommentNextQqProfileResponseParserTest {

    @Test
    void readsNicknameFromDocumentedResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {"nickname":"  QQ 用户  "}
            """);

        assertEquals("QQ 用户", CommentNextQqProfileResponseParser.nickname(response));
    }

    @Test
    void readsNameFromCommonQqApiResponse() throws Exception {
        var response = JsonUtils.createObjectMapper().readTree("""
            {
              "code": 200,
              "name": "冬天的夏目。",
              "avatar": "https://example.com/qq-avatar.png"
            }
            """);

        assertEquals("冬天的夏目。", CommentNextQqProfileResponseParser.nickname(response));
    }

    @Test
    void rejectsMissingOrOversizedNickname() throws Exception {
        var objectMapper = JsonUtils.createObjectMapper();

        assertEquals(
            "",
            CommentNextQqProfileResponseParser.nickname(objectMapper.readTree("{}"))
        );
        assertEquals(
            "",
            CommentNextQqProfileResponseParser.nickname(
                objectMapper.readTree("{\"nickname\":\"" + "x".repeat(65) + "\"}")
            )
        );
    }
}
