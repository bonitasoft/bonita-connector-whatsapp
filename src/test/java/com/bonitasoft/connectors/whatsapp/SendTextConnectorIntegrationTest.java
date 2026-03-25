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
 * MockWebServer-based integration tests for SendTextConnector.
 */
class SendTextConnectorIntegrationTest {

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
        inputs.put("recipientPhone", "34612345678");
        inputs.put("messageBody", "Hello from integration test!");
        inputs.put("connectTimeout", 5000);
        inputs.put("readTimeout", 5000);
        return inputs;
    }

    @Test
    void should_send_text_successfully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"messages":[{"id":"wamid.TEXT001"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendTextConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("messageId")).isEqualTo("wamid.TEXT001");
        assertThat(outputs.get("recipientPhone")).isEqualTo("34612345678");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"type\":\"text\"");
        assertThat(body).contains("Hello from integration test!");
    }

    @Test
    void should_handle_rate_limit_429() throws Exception {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setBody("""
                            {"error":{"code":130429,"message":"Rate limit hit","type":"OAuthException"}}
                            """));
        }

        var connector = new SendTextConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("Rate limit");
    }

    @Test
    void should_handle_unauthorized_401() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("""
                        {"error":{"code":190,"message":"Invalid OAuth access token","type":"OAuthException"}}
                        """));

        var connector = new SendTextConnector();
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
    void should_handle_24h_window_expired() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("""
                        {"error":{"code":131047,"message":"Re-engagement message: use template to re-open 24h window","type":"OAuthException"}}
                        """));

        var connector = new SendTextConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("131047");
    }

    @Test
    void should_send_text_with_preview_url() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"messages":[{"id":"wamid.PREVIEW001"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendTextConnector();
        var inputs = validInputs();
        inputs.put("messageBody", "Check this out: https://www.bonitasoft.com");
        inputs.put("previewUrl", true);
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("messageId")).isEqualTo("wamid.PREVIEW001");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"preview_url\":true");
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

        var connector = new SendTextConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }

    @Test
    void should_retry_on_429_then_succeed() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(429)
                .setBody("""
                        {"error":{"code":130429,"message":"Rate limit hit","type":"OAuthException"}}
                        """));
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"messages":[{"id":"wamid.RETRY_TEXT"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendTextConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("messageId")).isEqualTo("wamid.RETRY_TEXT");
    }
}
