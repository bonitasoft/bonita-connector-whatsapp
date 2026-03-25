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
 * MockWebServer-based integration tests for GetStatusConnector.
 */
class GetStatusConnectorIntegrationTest {

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
        inputs.put("messageId", "wamid.HBgLMzQ2");
        inputs.put("connectTimeout", 5000);
        inputs.put("readTimeout", 5000);
        return inputs;
    }

    @Test
    void should_get_status_successfully() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"status":"delivered","timestamp":"2026-03-24T09:15:33Z","error_code":""}
                        """));

        var connector = new GetStatusConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("status")).isEqualTo("delivered");
        assertThat(outputs.get("timestamp")).isEqualTo("2026-03-24T09:15:33Z");
        assertThat(outputs.get("errorCode")).isEqualTo("");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).contains("wamid.HBgLMzQ2");
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

        var connector = new GetStatusConnector();
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

        var connector = new GetStatusConnector();
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
    void should_handle_message_not_found() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(400)
                .setBody("""
                        {"error":{"code":100,"message":"Unsupported get request","type":"GraphMethodException"}}
                        """));

        var connector = new GetStatusConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("Unsupported get request");
    }

    @Test
    void should_return_failed_status_with_error_code() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"status":"failed","timestamp":"2026-03-24T10:00:00Z","error_code":"131026"}
                        """));

        var connector = new GetStatusConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("status")).isEqualTo("failed");
        assertThat(outputs.get("errorCode")).isEqualTo("131026");
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

        var connector = new GetStatusConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }

    @Test
    void should_return_read_status() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("""
                        {"status":"read","timestamp":"2026-03-24T11:00:00Z","error_code":""}
                        """));

        var connector = new GetStatusConnector();
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        assertThat(TestHelper.getOutputs(connector).get("status")).isEqualTo("read");
    }
}
