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
class SendTextConnectorTest {

    @Mock private WhatsAppClient mockClient;
    private SendTextConnector connector;

    @BeforeEach
    void setUp() { connector = new SendTextConnector(); }

    private void injectMockClient() throws Exception {
        var field = AbstractWhatsAppConnector.class.getDeclaredField("client");
        field.setAccessible(true);
        field.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> m = new HashMap<>();
        m.put("permanentToken", "test-token");
        m.put("phoneNumberId", "102290129340398");
        m.put("recipientPhone", "34612345678");
        m.put("messageBody", "Hello from Bonita BPM!");
        return m;
    }

    @Test void should_send_text_when_valid() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenReturn(new SendMessageResult("wamid.1", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("messageId")).isEqualTo("wamid.1");
    }

    @Test void should_fail_when_messageBody_missing() {
        Map<String, Object> m = validInputs(); m.remove("messageBody");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_fail_when_messageBody_exceeds_4096() {
        Map<String, Object> m = validInputs(); m.put("messageBody", "x".repeat(4097));
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("4096");
    }

    @Test void should_fail_when_recipientPhone_missing() {
        Map<String, Object> m = validInputs(); m.remove("recipientPhone");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_fail_when_phoneNumberId_missing() {
        Map<String, Object> m = validInputs(); m.remove("phoneNumberId");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_set_error_on_24h_window_expired() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenThrow(
                new WhatsAppException("24h session expired. Use whatsapp-send-template.", 400, 131047, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("24h session expired");
    }

    @Test void should_use_previewUrl_default_false() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenReturn(new SendMessageResult("wamid.2", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_handle_api_error() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenThrow(new WhatsAppException("Server error", 500, true));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }

    @Test void should_handle_unexpected_exception() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenThrow(new RuntimeException("Network failure"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Unexpected error");
    }

    @Test void should_fail_when_token_missing() {
        Map<String, Object> m = validInputs(); m.remove("permanentToken");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("token");
    }

    @Test void should_verify_all_outputs_on_success() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenReturn(new SendMessageResult("wamid.full", "34612345678"));
        connector.executeBusinessLogic();
        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("messageId")).isEqualTo("wamid.full");
        assertThat(outputs.get("recipientPhone")).isEqualTo("34612345678");
    }

    @Test void should_accept_null_optional_previewUrl() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("previewUrl", null);
        connector.setInputParameters(m);
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenReturn(new SendMessageResult("wamid.opt", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_truncate_long_error_message() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenThrow(
                new WhatsAppException("x".repeat(2000), 400, false));
        connector.executeBusinessLogic();
        assertThat(((String) TestHelper.getOutputs(connector).get("errorMessage")).length()).isLessThanOrEqualTo(1000);
    }

    @Test void should_accept_message_body_exactly_4096_chars() throws Exception {
        Map<String, Object> m = validInputs(); m.put("messageBody", "a".repeat(4096));
        connector.setInputParameters(m);
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenReturn(new SendMessageResult("wamid.max", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_handle_auth_failure() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenThrow(
                new WhatsAppException("Invalid OAuth access token", 401, 190, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Invalid OAuth");
    }

    @Test void should_normalize_phone_with_spaces() throws Exception {
        Map<String, Object> m = validInputs(); m.put("recipientPhone", "+34 612 345 678");
        connector.setInputParameters(m);
        connector.validateInputParameters();
        injectMockClient();
        when(mockClient.sendTextMessage(any())).thenReturn(new SendMessageResult("wamid.norm", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }
}
