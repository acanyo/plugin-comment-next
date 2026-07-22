package com.xhhao.comment.widget.ai.website;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class CommentNextWaybackClientTest {

    private final CommentNextWaybackClient client = new CommentNextWaybackClient(null);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsOnlyWaybackSnapshotUrls() throws Exception {
        var response = objectMapper.readTree("""
            {
              "archived_snapshots": {
                "closest": {
                  "available": true,
                  "status": "200",
                  "url": "http://web.archive.org/web/20260722000000/http://example.com/"
                }
              }
            }
            """);

        assertEquals(
            "https://web.archive.org/web/20260722000000/http://example.com/",
            client.snapshotUri(response).block().toASCIIString()
        );
    }

    @Test
    void rejectsUnexpectedSnapshotHosts() throws Exception {
        var response = objectMapper.readTree("""
            {
              "archived_snapshots": {
                "closest": {
                  "available": true,
                  "status": "200",
                  "url": "https://example.com/internal"
                }
              }
            }
            """);

        assertNull(client.snapshotUri(response).block());
    }
}
