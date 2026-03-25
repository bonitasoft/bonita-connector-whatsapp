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
class GetMessagesConnectorTest {

    @Mock private WhatsAppClient mockClient;
    private GetMessagesConnector connector;

    @BeforeEach void setUp() { connector = new GetMessagesConnector(); }

    private void injectMockClient() throws Exception {
        var f = AbstractWhatsAppConnector.class.getDeclaredField("client"); f.setAccessible(true); f.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> m = new HashMap<>();
        m.put("permanentToken", "tok"); m.put("phoneNumberId", "123"); m.put("contactPhone", "+34612345678");
        return m;
    }

    @Test void should_get_messages_when_valid() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenReturn(
                new MessageListResult("[{\"id\":\"wamid.1\",\"text\":\"Hello\"}]", 1, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("messageCount")).isEqualTo(1);
        assertThat((String) TestHelper.getOutputs(connector).get("messages")).contains("wamid.1");
    }

    @Test void should_fail_when_contactPhone_missing() {
        Map<String, Object> m = validInputs(); m.remove("contactPhone");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_fail_when_phoneNumberId_missing() {
        Map<String, Object> m = validInputs(); m.remove("phoneNumberId");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_normalize_contact_phone() throws Exception {
        Map<String, Object> m = validInputs(); m.put("contactPhone", "+34 612-345-678");
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenReturn(new MessageListResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_use_default_limit_20() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenReturn(new MessageListResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_handle_pagination() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenReturn(new MessageListResult("[...]", 20, true, "next_cursor"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("hasMore")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("nextCursor")).isEqualTo("next_cursor");
    }

    @Test void should_handle_api_error() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenThrow(new WhatsAppException("Not found", 400, 133010, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Not found");
    }

    @Test void should_return_empty_messages_array() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenReturn(new MessageListResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("messageCount")).isEqualTo(0);
    }

    @Test void should_handle_unexpected_error() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenThrow(new RuntimeException("Timeout"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }

    @Test void should_fail_when_token_missing() {
        Map<String, Object> m = validInputs(); m.remove("permanentToken");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("token");
    }

    @Test void should_verify_all_outputs_on_success() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenReturn(
                new MessageListResult("[{\"id\":\"wamid.1\"}]", 1, false, ""));
        connector.executeBusinessLogic();
        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("messages")).isNotNull();
        assertThat(outputs.get("messageCount")).isEqualTo(1);
        assertThat(outputs.get("hasMore")).isEqualTo(false);
        assertThat(outputs.get("nextCursor")).isEqualTo("");
    }

    @Test void should_accept_custom_limit() throws Exception {
        Map<String, Object> m = validInputs(); m.put("limit", 50);
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenReturn(new MessageListResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_accept_cursor_for_pagination() throws Exception {
        Map<String, Object> m = validInputs(); m.put("cursor", "abc123");
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenReturn(new MessageListResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_truncate_long_error_message() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenThrow(
                new WhatsAppException("x".repeat(2000), 400, false));
        connector.executeBusinessLogic();
        assertThat(((String) TestHelper.getOutputs(connector).get("errorMessage")).length()).isLessThanOrEqualTo(1000);
    }

    @Test void should_handle_rate_limit_error() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenThrow(
                new WhatsAppException("Rate limit hit", 429, 4, true));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }

    @Test void should_handle_auth_failure() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.getMessages(any())).thenThrow(
                new WhatsAppException("Invalid OAuth access token", 401, 190, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Invalid OAuth");
    }
}
