package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * MockWebServer-based integration tests for ListConversationsConnector.
 */
class ListConversationsConnectorIntegrationTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("permanentToken", "test-token-integration");
        inputs.put("baseUrl", server.url("/v23.0").toString());
        inputs.put("wabaId", "102290129340399");
        inputs.put("startDate", "2026-03-01");
        inputs.put("endDate", "2026-03-24");
        inputs.put("connectTimeout", 5000);
        inputs.put("readTimeout", 5000);
        return inputs;
    }

    @Test
    void should_list_conversations_successfully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {
                            "conversation_analytics": {
                                "data": [
                                    {"count": 42, "type": "REGULAR"},
                                    {"count": 15, "type": "BUSINESS_INITIATED"}
                                ]
                            },
                            "paging": {}
                        }
                        """));

        var connector = new ListConversationsConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("totalCount")).isEqualTo(2);
        assertThat(outputs.get("hasMore")).isEqualTo(false);
        assertThat((String) outputs.get("conversations")).contains("REGULAR");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).contains("conversation_analytics");
        assertThat(request.getPath()).contains("granularity=DAILY");
    }

    @Test
    void should_handle_rate_limit_429() throws Exception {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setBody("""
                            {"error":{"code":4,"message":"Application request limit reached","type":"OAuthException"}}
                            """));
        }

        var connector = new ListConversationsConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("request limit");
    }

    @Test
    void should_handle_unauthorized_401() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("""
                        {"error":{"code":190,"message":"Invalid OAuth access token","type":"OAuthException"}}
                        """));

        var connector = new ListConversationsConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("Invalid OAuth");
    }

    @Test
    void should_handle_invalid_waba_id() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("""
                        {"error":{"code":100,"message":"Invalid parameter","type":"OAuthException"}}
                        """));

        var connector = new ListConversationsConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("Invalid parameter");
    }

    @Test
    void should_handle_pagination_response() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {
                            "conversation_analytics": {
                                "data": [{"count": 500}]
                            },
                            "paging": {
                                "next": "https://graph.facebook.com/v23.0/next-page",
                                "cursors": {"after": "cursor_abc123"}
                            }
                        }
                        """));

        var connector = new ListConversationsConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("hasMore")).isEqualTo(true);
        assertThat(outputs.get("nextCursor")).isEqualTo("cursor_abc123");
    }

    @Test
    void should_handle_server_error_500() throws Exception {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(500)
                    .setBody("""
                            {"error":{"code":1,"message":"Internal server error","type":"OAuthException"}}
                            """));
        }

        var connector = new ListConversationsConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }
}
