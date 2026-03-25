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
 * MockWebServer-based integration tests for SendMediaConnector.
 */
class SendMediaConnectorIntegrationTest {

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
        inputs.put("mediaType", "document");
        inputs.put("mediaUrl", "https://example.com/invoice.pdf");
        inputs.put("connectTimeout", 5000);
        inputs.put("readTimeout", 5000);
        return inputs;
    }

    @Test
    void should_send_media_successfully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"messages":[{"id":"wamid.MEDIA001"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("caption", "Invoice Q1 2026");
        inputs.put("filename", "invoice-q1-2026.pdf");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("messageId")).isEqualTo("wamid.MEDIA001");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("POST");
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"type\":\"document\"");
        assertThat(body).contains("\"caption\":\"Invoice Q1 2026\"");
        assertThat(body).contains("\"filename\":\"invoice-q1-2026.pdf\"");
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

        var connector = new SendMediaConnector();
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

        var connector = new SendMediaConnector();
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
    void should_handle_file_too_large() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("""
                        {"error":{"code":131053,"message":"Media upload error: file size too large","type":"OAuthException"}}
                        """));

        var connector = new SendMediaConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("file size too large");
    }

    @Test
    void should_send_image_with_media_id() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"messages":[{"id":"wamid.IMG001"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("mediaType", "image");
        inputs.remove("mediaUrl");
        inputs.put("mediaId", "media-id-abc123");
        inputs.put("caption", "Product photo");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("messageId")).isEqualTo("wamid.IMG001");

        RecordedRequest request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("\"type\":\"image\"");
        assertThat(body).contains("\"id\":\"media-id-abc123\"");
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

        var connector = new SendMediaConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }

    @Test
    void should_send_video_successfully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"messages":[{"id":"wamid.VIDEO001"}],"contacts":[{"wa_id":"34612345678"}]}
                        """));

        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("mediaType", "video");
        inputs.put("mediaUrl", "https://example.com/video.mp4");
        connector.setInputParameters(inputs);
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("messageId")).isEqualTo("wamid.VIDEO001");
    }
}
