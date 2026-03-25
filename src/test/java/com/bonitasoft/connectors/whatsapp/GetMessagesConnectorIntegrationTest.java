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
 * MockWebServer-based integration tests for GetMessagesConnector.
 */
class GetMessagesConnectorIntegrationTest {

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
        inputs.put("phoneNumberId", "102290129340398");
        inputs.put("contactPhone", "34612345678");
        inputs.put("connectTimeout", 5000);
        inputs.put("readTimeout", 5000);
        return inputs;
    }

    @Test
    void should_get_messages_successfully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {
                            "data": [
                                {"id": "wamid.MSG001", "type": "text", "text": {"body": "Hello"}},
                                {"id": "wamid.MSG002", "type": "text", "text": {"body": "World"}}
                            ],
                            "paging": {}
                        }
                        """));

        var connector = new GetMessagesConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("messageCount")).isEqualTo(2);
        assertThat(outputs.get("hasMore")).isEqualTo(false);
        assertThat((String) outputs.get("messages")).contains("wamid.MSG001");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).contains("contact_phone=34612345678");
        assertThat(request.getPath()).contains("limit=20");
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

        var connector = new GetMessagesConnector();
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

        var connector = new GetMessagesConnector();
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
    void should_handle_contact_not_found() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("""
                        {"error":{"code":133010,"message":"Contact phone number not found","type":"OAuthException"}}
                        """));

        var connector = new GetMessagesConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("not found");
    }

    @Test
    void should_handle_pagination_response() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {
                            "data": [
                                {"id": "wamid.MSG100", "type": "text", "text": {"body": "Test"}}
                            ],
                            "paging": {
                                "next": "https://graph.facebook.com/v23.0/next",
                                "cursors": {"after": "cursor_xyz789"}
                            }
                        }
                        """));

        var connector = new GetMessagesConnector();
        var inputs = validInputs();
        inputs.put("limit", 1);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("messageCount")).isEqualTo(1);
        assertThat(outputs.get("hasMore")).isEqualTo(true);
        assertThat(outputs.get("nextCursor")).isEqualTo("cursor_xyz789");
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

        var connector = new GetMessagesConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }

    @Test
    void should_return_empty_messages() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"data":[],"paging":{}}
                        """));

        var connector = new GetMessagesConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("messageCount")).isEqualTo(0);
        assertThat(outputs.get("hasMore")).isEqualTo(false);
    }
}
