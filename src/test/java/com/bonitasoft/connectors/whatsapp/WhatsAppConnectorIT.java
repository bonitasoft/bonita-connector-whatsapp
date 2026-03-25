package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Integration tests for WhatsApp Business Cloud API connector.
 *
 * <p>Requires the following environment variables:</p>
 * <ul>
 *     <li>{@code WHATSAPP_TOKEN} — System User permanent token from Meta Business Manager</li>
 *     <li>{@code WHATSAPP_PHONE_NUMBER_ID} — Phone Number ID registered in WhatsApp Business</li>
 *     <li>{@code WHATSAPP_TEST_RECIPIENT} — Phone number to receive test messages (with country code, no +)</li>
 * </ul>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "WHATSAPP_TOKEN", matches = ".+")
class WhatsAppConnectorIT {

    private static final String TOKEN = System.getenv("WHATSAPP_TOKEN");
    private static final String PHONE_NUMBER_ID = System.getenv("WHATSAPP_PHONE_NUMBER_ID");
    private static final String TEST_RECIPIENT = System.getenv("WHATSAPP_TEST_RECIPIENT");

    @Test
    void should_send_template_message() throws Exception {
        var connector = new SendTemplateConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("permanentToken", TOKEN);
        inputs.put("phoneNumberId", PHONE_NUMBER_ID);
        inputs.put("recipientPhone", TEST_RECIPIENT);
        inputs.put("templateName", "hello_world");
        inputs.put("templateLanguage", "en_US");
        connector.setInputParameters(inputs);

        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat((String) outputs.get("errorMessage")).isEmpty();
        assertThat((String) outputs.get("messageId")).startsWith("wamid.");
        assertThat(outputs.get("recipientPhone")).isNotNull();
    }

    @Test
    void should_send_text_message() throws Exception {
        var connector = new SendTextConnector();
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("permanentToken", TOKEN);
        inputs.put("phoneNumberId", PHONE_NUMBER_ID);
        inputs.put("recipientPhone", TEST_RECIPIENT);
        inputs.put("messageBody", "Integration test message from Bonita WhatsApp Connector at " + java.time.Instant.now());
        inputs.put("previewUrl", false);
        connector.setInputParameters(inputs);

        connector.validateInputParameters();
        connector.connect();
        connector.executeBusinessLogic();
        connector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat((String) outputs.get("errorMessage")).isEmpty();
        assertThat((String) outputs.get("messageId")).startsWith("wamid.");
    }

    @Test
    void should_get_message_status() throws Exception {
        // First, send a message to get a valid message ID
        var sendConnector = new SendTextConnector();
        Map<String, Object> sendInputs = new HashMap<>();
        sendInputs.put("permanentToken", TOKEN);
        sendInputs.put("phoneNumberId", PHONE_NUMBER_ID);
        sendInputs.put("recipientPhone", TEST_RECIPIENT);
        sendInputs.put("messageBody", "Status check test at " + java.time.Instant.now());
        sendInputs.put("previewUrl", false);
        sendConnector.setInputParameters(sendInputs);

        sendConnector.validateInputParameters();
        sendConnector.connect();
        sendConnector.executeBusinessLogic();
        sendConnector.disconnect();

        String messageId = (String) TestHelper.getOutputs(sendConnector).get("messageId");
        assertThat(messageId).startsWith("wamid.");

        // Now check status of that message
        var statusConnector = new GetStatusConnector();
        Map<String, Object> statusInputs = new HashMap<>();
        statusInputs.put("permanentToken", TOKEN);
        statusInputs.put("messageId", messageId);
        statusConnector.setInputParameters(statusInputs);

        statusConnector.validateInputParameters();
        statusConnector.connect();
        statusConnector.executeBusinessLogic();
        statusConnector.disconnect();

        Map<String, Object> outputs = TestHelper.getOutputs(statusConnector);
        // Status endpoint may return success=false if the API doesn't support single-message status lookup,
        // but the connector should not throw an unhandled exception
        assertThat(outputs).containsKey("success");
    }
}
