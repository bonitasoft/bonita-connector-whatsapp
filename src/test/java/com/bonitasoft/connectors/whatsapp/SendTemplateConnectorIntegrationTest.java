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
 * MockWebServer-based integration tests for SendTemplateConnector.
 * Tests the full connector lifecycle (validate -> connect -> execute -> disconnect)
 * against a local HTTP server.
 */
class SendTemplateConnectorIntegrationTest {

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
        inputs.put("templateName", "order_confirmation_es");
        inputs.put("templateLanguage", "es");
        inputs.put("connectTimeout", 5000);
        inputs.put("readTimeout", 5000);
        return inputs;
    }

    @Test
    void should_send_template_successfully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"messages":[{"id":"wamid.HBgLMzQ2"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendTemplateConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("messageId")).isEqualTo("wamid.HBgLMzQ2");
        assertThat(outputs.get("recipientPhone")).isEqualTo("34612345678");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).contains("/messages");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token-integration");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"type\":\"template\"");
        assertThat(body).contains("\"name\":\"order_confirmation_es\"");
    }

    @Test
    void should_handle_rate_limit_429() throws Exception {
        // Enqueue 4 429s (initial + 3 retries)
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse()
                    .setResponseCode(429)
                    .setBody("""
                            {"error":{"code":130429,"message":"Rate limit hit","type":"OAuthException"}}
                            """));
        }

        var connector = new SendTemplateConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("130429");
    }

    @Test
    void should_handle_unauthorized_401() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(401)
                .setBody("""
                        {"error":{"code":190,"message":"Invalid OAuth access token","type":"OAuthException"}}
                        """));

        var connector = new SendTemplateConnector();
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
    void should_handle_invalid_template() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("""
                        {"error":{"code":132001,"message":"Template name does not exist in the specified language","type":"OAuthException"}}
                        """));

        var connector = new SendTemplateConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("132001");
        assertThat((String) outputs.get("errorMessage")).contains("Template name does not exist");
    }

    @Test
    void should_return_message_id() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"messages":[{"id":"wamid.UNIQUE_MSG_ID_12345"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("templateParameters", "[\"ORD-2026-001\",\"Madrid\"]");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("messageId")).isEqualTo("wamid.UNIQUE_MSG_ID_12345");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("components");
        assertThat(body).contains("ORD-2026-001");
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

        var connector = new SendTemplateConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("Internal server error");
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
                        {"messages":[{"id":"wamid.RETRY_OK"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendTemplateConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("messageId")).isEqualTo("wamid.RETRY_OK");
    }
}
