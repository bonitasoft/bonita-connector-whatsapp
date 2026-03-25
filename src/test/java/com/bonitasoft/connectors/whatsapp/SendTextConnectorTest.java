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
}
