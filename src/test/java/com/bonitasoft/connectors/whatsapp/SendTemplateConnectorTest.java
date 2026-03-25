package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendTemplateConnectorTest {

    @Mock
    private WhatsAppClient mockClient;

    private SendTemplateConnector connector;

    @BeforeEach
    void setUp() {
        connector = new SendTemplateConnector();
    }

    private void setInputs(Map<String, Object> inputs) {
        connector.setInputParameters(inputs);
    }

    private void injectMockClient() throws Exception {
        var field = AbstractWhatsAppConnector.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("permanentToken", "test-token-123");
        inputs.put("phoneNumberId", "102290129340398");
        inputs.put("recipientPhone", "34612345678");
        inputs.put("templateName", "order_confirmation_es");
        inputs.put("templateLanguage", "es");
        return inputs;
    }

    @Test
    void should_send_template_when_all_inputs_valid() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenReturn(
                new SendMessageResult("wamid.HBgLMzQ2", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("messageId")).isEqualTo("wamid.HBgLMzQ2");
        assertThat(TestHelper.getOutputs(connector).get("recipientPhone")).isEqualTo("34612345678");
        assertThat(TestHelper.getOutputs(connector).get("errorMessage")).isEqualTo("");
    }

    @Test
    void should_fail_validation_when_token_missing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove("permanentToken");
        setInputs(inputs);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("token");
    }

    @Test
    void should_fail_validation_when_recipientPhone_missing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove("recipientPhone");
        setInputs(inputs);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void should_fail_validation_when_templateName_missing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove("templateName");
        setInputs(inputs);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void should_fail_validation_when_phoneNumberId_missing() {
        Map<String, Object> inputs = validInputs();
        inputs.remove("phoneNumberId");
        setInputs(inputs);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Test
    void should_fail_validation_when_phone_format_invalid() {
        Map<String, Object> inputs = validInputs();
        inputs.put("recipientPhone", "123");
        setInputs(inputs);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("7-15 digits");
    }

    @Test
    void should_normalize_phone_with_plus_and_spaces() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put("recipientPhone", "+34 612 345 678");
        setInputs(inputs);
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenReturn(
                new SendMessageResult("wamid.test", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test
    void should_set_error_outputs_when_api_fails() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenThrow(
                new WhatsAppException("Meta API error 400 [132001]: Template not found", 400, 132001, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("132001");
    }

    @Test
    void should_handle_template_with_parameters() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put("templateParameters", "[\"ORD-2026-00123\",\"Calle Mayor 1\",\"15/04/2026\"]");
        setInputs(inputs);
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenReturn(
                new SendMessageResult("wamid.params", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("messageId")).isEqualTo("wamid.params");
    }

    @Test
    void should_use_default_language_when_not_provided() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.remove("templateLanguage");
        setInputs(inputs);
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenReturn(
                new SendMessageResult("wamid.default", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test
    void should_handle_unexpected_exception() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenThrow(new RuntimeException("Network failure"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Unexpected error");
    }

    @Test
    void should_handle_rate_limit_error() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenThrow(
                new WhatsAppException("Rate limit hit", 429, 130429, true));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Rate limit");
    }

    @Test
    void should_fail_validation_when_phone_too_short() {
        Map<String, Object> inputs = validInputs();
        inputs.put("recipientPhone", "12345");
        setInputs(inputs);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("7-15 digits");
    }

    @Test
    void should_fail_validation_when_phone_too_long() {
        Map<String, Object> inputs = validInputs();
        inputs.put("recipientPhone", "1234567890123456");
        setInputs(inputs);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("7-15 digits");
    }

    @Test
    void should_accept_phone_with_dashes() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put("recipientPhone", "34-612-345-678");
        setInputs(inputs);
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenReturn(
                new SendMessageResult("wamid.dash", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test
    void should_handle_auth_failure() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTemplateMessage(any())).thenThrow(
                new WhatsAppException("Invalid OAuth access token", 401, 190, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Invalid OAuth");
    }

    @Test
    void should_truncate_long_error_message() throws Exception {
        setInputs(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        String longMsg = "x".repeat(2000);
        when(mockClient.sendTemplateMessage(any())).thenThrow(
                new WhatsAppException(longMsg, 400, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat(((String) TestHelper.getOutputs(connector).get("errorMessage")).length()).isLessThanOrEqualTo(1000);
    }
}
