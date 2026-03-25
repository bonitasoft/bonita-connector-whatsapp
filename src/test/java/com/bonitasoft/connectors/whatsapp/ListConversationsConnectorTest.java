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
class ListConversationsConnectorTest {

    @Mock private WhatsAppClient mockClient;
    private ListConversationsConnector connector;

    @BeforeEach void setUp() { connector = new ListConversationsConnector(); }

    private void injectMockClient() throws Exception {
        var f = AbstractWhatsAppConnector.class.getDeclaredField("client"); f.setAccessible(true); f.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> m = new HashMap<>();
        m.put("permanentToken", "tok"); m.put("wabaId", "102290129340399");
        m.put("startDate", "2026-03-01"); m.put("endDate", "2026-03-24");
        return m;
    }

    @Test void should_list_conversations_when_valid() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenReturn(
                new ConversationAnalyticsResult("[{\"count\":12}]", 1, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("totalCount")).isEqualTo(1);
        assertThat(TestHelper.getOutputs(connector).get("hasMore")).isEqualTo(false);
    }

    @Test void should_fail_when_wabaId_missing() {
        Map<String, Object> m = validInputs(); m.remove("wabaId");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_fail_when_startDate_missing() {
        Map<String, Object> m = validInputs(); m.remove("startDate");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_fail_when_date_range_exceeds_90_days() {
        Map<String, Object> m = validInputs(); m.put("startDate", "2026-01-01"); m.put("endDate", "2026-06-01");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("90-day");
    }

    @Test void should_accept_unix_timestamps() throws Exception {
        Map<String, Object> m = validInputs(); m.put("startDate", "1740787200"); m.put("endDate", "1743379200");
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenReturn(
                new ConversationAnalyticsResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_handle_pagination() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenReturn(
                new ConversationAnalyticsResult("[{\"count\":500}]", 500, true, "cursor123"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("hasMore")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("nextCursor")).isEqualTo("cursor123");
    }

    @Test void should_use_default_granularity() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenReturn(
                new ConversationAnalyticsResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_handle_api_error() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenThrow(new WhatsAppException("Rate limit", 429, 4, true));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
    }

    @Test void should_fail_when_token_missing() {
        Map<String, Object> m = validInputs(); m.remove("permanentToken");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("token");
    }

    @Test void should_fail_when_endDate_missing() {
        Map<String, Object> m = validInputs(); m.remove("endDate");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_handle_unexpected_error() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenThrow(new RuntimeException("Timeout"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("Unexpected error");
    }

    @Test void should_verify_all_outputs_on_success() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenReturn(
                new ConversationAnalyticsResult("[{\"count\":42}]", 1, false, ""));
        connector.executeBusinessLogic();
        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("conversations")).isNotNull();
        assertThat(outputs.get("totalCount")).isEqualTo(1);
        assertThat(outputs.get("hasMore")).isEqualTo(false);
        assertThat(outputs.get("nextCursor")).isEqualTo("");
    }

    @Test void should_accept_optional_conversation_type_and_direction() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("conversationType", "REGULAR");
        m.put("direction", "BUSINESS_INITIATED");
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenReturn(
                new ConversationAnalyticsResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_accept_custom_limit() throws Exception {
        Map<String, Object> m = validInputs(); m.put("limit", 100);
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenReturn(
                new ConversationAnalyticsResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_fail_when_invalid_date_format() {
        Map<String, Object> m = validInputs(); m.put("startDate", "not-a-date");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("Invalid date format");
    }

    @Test void should_truncate_long_error_message() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenThrow(
                new WhatsAppException("x".repeat(2000), 400, false));
        connector.executeBusinessLogic();
        assertThat(((String) TestHelper.getOutputs(connector).get("errorMessage")).length()).isLessThanOrEqualTo(1000);
    }

    @Test void should_accept_cursor_for_pagination() throws Exception {
        Map<String, Object> m = validInputs(); m.put("cursor", "cursor_abc");
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.listConversations(any())).thenReturn(
                new ConversationAnalyticsResult("[]", 0, false, ""));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }
}
