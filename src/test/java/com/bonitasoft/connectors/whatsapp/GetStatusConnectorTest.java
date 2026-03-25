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
class GetStatusConnectorTest {

    @Mock private WhatsAppClient mockClient;
    private GetStatusConnector connector;

    @BeforeEach void setUp() { connector = new GetStatusConnector(); }

    private void injectMockClient() throws Exception {
        var f = AbstractWhatsAppConnector.class.getDeclaredField("client"); f.setAccessible(true); f.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> m = new HashMap<>();
        m.put("permanentToken", "tok"); m.put("messageId", "wamid.HBgLMzQ2");
        return m;
    }

    @Test void should_get_status_delivered() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessageStatus(any())).thenReturn(new MessageStatusResult("delivered", "2026-03-24T09:15:33Z", ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("status")).isEqualTo("delivered");
        assertThat(TestHelper.getOutputs(connector).get("timestamp")).isEqualTo("2026-03-24T09:15:33Z");
    }

    @Test void should_get_status_failed_with_errorCode() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessageStatus(any())).thenReturn(new MessageStatusResult("failed", "2026-03-24T10:00:00Z", "131026"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("status")).isEqualTo("failed");
        assertThat(TestHelper.getOutputs(connector).get("errorCode")).isEqualTo("131026");
    }

    @Test void should_fail_when_messageId_missing() {
        Map<String, Object> m = validInputs(); m.remove("messageId");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_fail_when_token_missing() {
        Map<String, Object> m = validInputs(); m.remove("permanentToken");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("token");
    }

    @Test void should_handle_api_error() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessageStatus(any())).thenThrow(new WhatsAppException("Token revoked", 400, 190, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Token revoked");
    }

    @Test void should_return_read_status() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessageStatus(any())).thenReturn(new MessageStatusResult("read", "2026-03-24T11:00:00Z", ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("status")).isEqualTo("read");
    }

    @Test void should_return_sent_status() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessageStatus(any())).thenReturn(new MessageStatusResult("sent", "2026-03-24T09:00:00Z", ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("status")).isEqualTo("sent");
    }

    @Test void should_handle_unexpected_error() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessageStatus(any())).thenThrow(new RuntimeException("Network down"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }
}
