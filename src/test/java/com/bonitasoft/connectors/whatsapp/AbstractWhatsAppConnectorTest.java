package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for AbstractWhatsAppConnector protected utility methods.
 * Uses SendTextConnector as a concrete subclass to test inherited behavior.
 * Designed to kill pitest mutants on readBooleanInput, readIntegerInput,
 * normalizePhone, resolveToken, and truncate.
 */
class AbstractWhatsAppConnectorTest {

    private SendTextConnector connector;

    @BeforeEach
    void setUp() {
        connector = new SendTextConnector();
    }

    // ─── Helper to call protected methods via reflection ───

    private Object invokeReadIntegerInput(String name, int defaultValue) throws Exception {
        Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("readIntegerInput", String.class, int.class);
        m.setAccessible(true);
        return m.invoke(connector, name, defaultValue);
    }

    private Object invokeReadBooleanInput(String name, boolean defaultValue) throws Exception {
        Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("readBooleanInput", String.class, boolean.class);
        m.setAccessible(true);
        return m.invoke(connector, name, defaultValue);
    }

    private Object invokeNormalizePhone(String phone) throws Exception {
        Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("normalizePhone", String.class);
        m.setAccessible(true);
        return m.invoke(connector, phone);
    }

    private Object invokeResolveToken() throws Exception {
        Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("resolveToken");
        m.setAccessible(true);
        return m.invoke(connector);
    }

    private Object invokeTruncate(String msg) throws Exception {
        Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("truncate", String.class);
        m.setAccessible(true);
        return m.invoke(connector, msg);
    }

    private void injectMockClient(WhatsAppClient mockClient) throws Exception {
        var f = AbstractWhatsAppConnector.class.getDeclaredField("client");
        f.setAccessible(true);
        f.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> m = new HashMap<>();
        m.put("permanentToken", "tok");
        m.put("phoneNumberId", "123");
        m.put("recipientPhone", "34612345678");
        m.put("messageBody", "hi");
        return m;
    }

    // ─── readBooleanInput: verify actual return values ───

    @Test
    void should_readBooleanInput_return_default_true_when_value_null() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("previewUrl", null);
        connector.setInputParameters(m);
        // default = true, val is null => should return true
        Object result = invokeReadBooleanInput("previewUrl", true);
        assertThat(result).isEqualTo(true);
    }

    @Test
    void should_readBooleanInput_return_default_false_when_value_null() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("previewUrl", null);
        connector.setInputParameters(m);
        // default = false, val is null => should return false
        Object result = invokeReadBooleanInput("previewUrl", false);
        assertThat(result).isEqualTo(false);
    }

    @Test
    void should_readBooleanInput_return_true_when_input_is_boolean_true() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("previewUrl", Boolean.TRUE);
        connector.setInputParameters(m);
        Object result = invokeReadBooleanInput("previewUrl", false);
        assertThat(result).isEqualTo(true);
    }

    @Test
    void should_readBooleanInput_return_false_when_input_is_boolean_false() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("previewUrl", Boolean.FALSE);
        connector.setInputParameters(m);
        Object result = invokeReadBooleanInput("previewUrl", true);
        assertThat(result).isEqualTo(false);
    }

    @Test
    void should_readBooleanInput_parse_string_true() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("previewUrl", "true");
        connector.setInputParameters(m);
        Object result = invokeReadBooleanInput("previewUrl", false);
        assertThat(result).isEqualTo(true);
    }

    @Test
    void should_readBooleanInput_parse_string_false() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("previewUrl", "false");
        connector.setInputParameters(m);
        Object result = invokeReadBooleanInput("previewUrl", true);
        assertThat(result).isEqualTo(false);
    }

    @Test
    void should_readBooleanInput_parse_non_boolean_string_as_false() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("previewUrl", "yes");
        connector.setInputParameters(m);
        // "yes" is not "true" so Boolean.parseBoolean returns false
        Object result = invokeReadBooleanInput("previewUrl", true);
        assertThat(result).isEqualTo(false);
    }

    // ─── readIntegerInput: verify actual return values ───

    @Test
    void should_readIntegerInput_return_default_when_null() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("connectTimeout", null);
        connector.setInputParameters(m);
        Object result = invokeReadIntegerInput("connectTimeout", 42);
        assertThat(result).isEqualTo(42);
    }

    @Test
    void should_readIntegerInput_return_integer_instance() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("connectTimeout", 5000);
        connector.setInputParameters(m);
        Object result = invokeReadIntegerInput("connectTimeout", 0);
        assertThat(result).isEqualTo(5000);
    }

    @Test
    void should_readIntegerInput_parse_string() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("connectTimeout", "7500");
        connector.setInputParameters(m);
        Object result = invokeReadIntegerInput("connectTimeout", 0);
        assertThat(result).isEqualTo(7500);
    }

    @Test
    void should_readIntegerInput_return_default_for_invalid_string() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("connectTimeout", "not-a-number");
        connector.setInputParameters(m);
        Object result = invokeReadIntegerInput("connectTimeout", 99);
        assertThat(result).isEqualTo(99);
    }

    @Test
    void should_readIntegerInput_return_default_for_missing_key() throws Exception {
        connector.setInputParameters(validInputs());
        // "someKey" not in inputs => null => default
        Object result = invokeReadIntegerInput("someKey", 123);
        assertThat(result).isEqualTo(123);
    }

    // ─── resolveToken: 3 branches + exception ───

    @Test
    void should_resolveToken_from_input() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("permanentToken", "my-token");
        connector.setInputParameters(m);
        Object result = invokeResolveToken();
        assertThat(result).isEqualTo("my-token");
    }

    @Test
    void should_resolveToken_fallback_to_system_property() throws Exception {
        Map<String, Object> m = validInputs();
        m.remove("permanentToken");
        connector.setInputParameters(m);
        String prev = System.getProperty("whatsapp.token");
        try {
            System.setProperty("whatsapp.token", "sys-prop-token");
            Object result = invokeResolveToken();
            assertThat(result).isEqualTo("sys-prop-token");
        } finally {
            if (prev != null) System.setProperty("whatsapp.token", prev);
            else System.clearProperty("whatsapp.token");
        }
    }

    @Test
    void should_resolveToken_skip_blank_input_and_use_system_property() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("permanentToken", "   ");
        connector.setInputParameters(m);
        String prev = System.getProperty("whatsapp.token");
        try {
            System.setProperty("whatsapp.token", "sys-token-2");
            Object result = invokeResolveToken();
            assertThat(result).isEqualTo("sys-token-2");
        } finally {
            if (prev != null) System.setProperty("whatsapp.token", prev);
            else System.clearProperty("whatsapp.token");
        }
    }

    @Test
    void should_resolveToken_prefer_input_over_system_property() throws Exception {
        Map<String, Object> m = validInputs();
        m.put("permanentToken", "input-tok");
        connector.setInputParameters(m);
        String prev = System.getProperty("whatsapp.token");
        try {
            System.setProperty("whatsapp.token", "sys-tok");
            Object result = invokeResolveToken();
            assertThat(result).isEqualTo("input-tok");
        } finally {
            if (prev != null) System.setProperty("whatsapp.token", prev);
            else System.clearProperty("whatsapp.token");
        }
    }

    @Test
    void should_resolveToken_throw_when_all_sources_empty() {
        Map<String, Object> m = validInputs();
        m.remove("permanentToken");
        connector.setInputParameters(m);
        String prev = System.getProperty("whatsapp.token");
        try {
            System.clearProperty("whatsapp.token");
            assertThatThrownBy(() -> invokeResolveToken())
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        } finally {
            if (prev != null) System.setProperty("whatsapp.token", prev);
        }
    }

    @Test
    void should_resolveToken_skip_blank_system_property_to_throw() {
        Map<String, Object> m = validInputs();
        m.remove("permanentToken");
        connector.setInputParameters(m);
        String prev = System.getProperty("whatsapp.token");
        try {
            System.setProperty("whatsapp.token", "  ");
            // env var WHATSAPP_TOKEN is presumably not set in test env
            // This should throw because input=null, sysprop=blank, env=null/blank
            assertThatThrownBy(() -> invokeResolveToken())
                    .hasCauseInstanceOf(IllegalArgumentException.class);
        } finally {
            if (prev != null) System.setProperty("whatsapp.token", prev);
            else System.clearProperty("whatsapp.token");
        }
    }

    // ─── normalizePhone: null, clean, with special chars ───

    @Test
    void should_normalizePhone_return_null_for_null() throws Exception {
        connector.setInputParameters(validInputs());
        Object result = invokeNormalizePhone(null);
        assertThat(result).isNull();
    }

    @Test
    void should_normalizePhone_strip_plus_spaces_dashes() throws Exception {
        connector.setInputParameters(validInputs());
        Object result = invokeNormalizePhone("+34 612-345 678");
        assertThat(result).isEqualTo("34612345678");
    }

    @Test
    void should_normalizePhone_return_same_when_clean() throws Exception {
        connector.setInputParameters(validInputs());
        Object result = invokeNormalizePhone("34612345678");
        assertThat(result).isEqualTo("34612345678");
    }

    @Test
    void should_normalizePhone_return_empty_string_for_empty() throws Exception {
        connector.setInputParameters(validInputs());
        Object result = invokeNormalizePhone("");
        assertThat(result).isEqualTo("");
    }

    // ─── truncate: boundary at exactly 1000 ───

    @Test
    void should_truncate_return_null_for_null() throws Exception {
        connector.setInputParameters(validInputs());
        Object result = invokeTruncate(null);
        assertThat(result).isNull();
    }

    @Test
    void should_truncate_not_truncate_short_message() throws Exception {
        connector.setInputParameters(validInputs());
        Object result = invokeTruncate("short msg");
        assertThat(result).isEqualTo("short msg");
    }

    @Test
    void should_truncate_not_truncate_exactly_1000_chars() throws Exception {
        connector.setInputParameters(validInputs());
        String exact = "A".repeat(1000);
        Object result = invokeTruncate(exact);
        assertThat(result).isEqualTo(exact);
        assertThat(((String) result).length()).isEqualTo(1000);
    }

    @Test
    void should_truncate_at_1001_chars() throws Exception {
        connector.setInputParameters(validInputs());
        String longMsg = "B".repeat(1001);
        Object result = invokeTruncate(longMsg);
        assertThat(((String) result).length()).isEqualTo(1000);
    }

    @Test
    void should_truncate_at_2000_chars() throws Exception {
        connector.setInputParameters(validInputs());
        String veryLong = "C".repeat(2000);
        Object result = invokeTruncate(veryLong);
        assertThat(((String) result).length()).isEqualTo(1000);
    }

    // ─── executeBusinessLogic: success path ───

    @Test
    void should_set_success_true_on_happy_path() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        var mockClient = mock(WhatsAppClient.class);
        when(mockClient.sendTextMessage(any()))
                .thenReturn(new SendMessageResult("wamid.1", "34612345678"));
        injectMockClient(mockClient);
        connector.executeBusinessLogic();
        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(true);
        assertThat(outputs.get("errorMessage")).isEqualTo("");
        assertThat(outputs.get("messageId")).isEqualTo("wamid.1");
        assertThat(outputs.get("recipientPhone")).isEqualTo("34612345678");
    }

    // ─── executeBusinessLogic: WhatsAppException path ───

    @Test
    void should_set_success_false_on_whatsapp_error() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        var mockClient = mock(WhatsAppClient.class);
        when(mockClient.sendTextMessage(any()))
                .thenThrow(new WhatsAppException("API Error 131047", 400, 131047, false));
        injectMockClient(mockClient);
        connector.executeBusinessLogic();
        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).contains("API Error 131047");
    }

    // ─── executeBusinessLogic: unexpected Exception path ───

    @Test
    void should_set_success_false_on_unexpected_error() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        var mockClient = mock(WhatsAppClient.class);
        when(mockClient.sendTextMessage(any()))
                .thenThrow(new RuntimeException("Network failure"));
        injectMockClient(mockClient);
        connector.executeBusinessLogic();
        Map<String, Object> outputs = TestHelper.getOutputs(connector);
        assertThat(outputs.get("success")).isEqualTo(false);
        assertThat((String) outputs.get("errorMessage")).startsWith("Unexpected error:");
        assertThat((String) outputs.get("errorMessage")).contains("Network failure");
    }

    // ─── executeBusinessLogic: truncate in error paths ───

    @Test
    void should_truncate_long_whatsapp_error_message() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        var mockClient = mock(WhatsAppClient.class);
        String longMsg = "E".repeat(1500);
        when(mockClient.sendTextMessage(any()))
                .thenThrow(new WhatsAppException(longMsg, 400, false));
        injectMockClient(mockClient);
        connector.executeBusinessLogic();
        String errorMsg = (String) TestHelper.getOutputs(connector).get("errorMessage");
        assertThat(errorMsg.length()).isEqualTo(1000);
    }

    @Test
    void should_truncate_long_unexpected_error_message() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        var mockClient = mock(WhatsAppClient.class);
        String longMsg = "F".repeat(2000);
        when(mockClient.sendTextMessage(any()))
                .thenThrow(new RuntimeException(longMsg));
        injectMockClient(mockClient);
        connector.executeBusinessLogic();
        String errorMsg = (String) TestHelper.getOutputs(connector).get("errorMessage");
        assertThat(errorMsg.length()).isEqualTo(1000);
    }

    @Test
    void should_not_truncate_whatsapp_error_message_at_exactly_1000() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        var mockClient = mock(WhatsAppClient.class);
        String exactMsg = "G".repeat(1000);
        when(mockClient.sendTextMessage(any()))
                .thenThrow(new WhatsAppException(exactMsg, 400, false));
        injectMockClient(mockClient);
        connector.executeBusinessLogic();
        String errorMsg = (String) TestHelper.getOutputs(connector).get("errorMessage");
        assertThat(errorMsg).isEqualTo(exactMsg);
        assertThat(errorMsg.length()).isEqualTo(1000);
    }

    @Test
    void should_not_truncate_whatsapp_error_at_999() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
        var mockClient = mock(WhatsAppClient.class);
        String shortMsg = "H".repeat(999);
        when(mockClient.sendTextMessage(any()))
                .thenThrow(new WhatsAppException(shortMsg, 400, false));
        injectMockClient(mockClient);
        connector.executeBusinessLogic();
        String errorMsg = (String) TestHelper.getOutputs(connector).get("errorMessage");
        assertThat(errorMsg).isEqualTo(shortMsg);
    }

    // ─── readStringInput ───

    @Test
    void should_readStringInput_return_null_when_not_set() throws Exception {
        connector.setInputParameters(validInputs());
        Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("readStringInput", String.class);
        m.setAccessible(true);
        Object result = m.invoke(connector, "nonexistentKey");
        assertThat(result).isNull();
    }

    @Test
    void should_readStringInput_return_value_as_string() throws Exception {
        Map<String, Object> inputs = validInputs();
        inputs.put("myKey", 12345);
        connector.setInputParameters(inputs);
        Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("readStringInput", String.class);
        m.setAccessible(true);
        Object result = m.invoke(connector, "myKey");
        assertThat(result).isEqualTo("12345");
    }

    // ─── readMandatoryStringInput ───

    @Test
    void should_readMandatoryStringInput_throw_on_null() {
        Map<String, Object> inputs = validInputs();
        inputs.put("myField", null);
        connector.setInputParameters(inputs);
        assertThatThrownBy(() -> {
            Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("readMandatoryStringInput", String.class);
            m.setAccessible(true);
            m.invoke(connector, "myField");
        }).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void should_readMandatoryStringInput_throw_on_blank() {
        Map<String, Object> inputs = validInputs();
        inputs.put("myField", "   ");
        connector.setInputParameters(inputs);
        assertThatThrownBy(() -> {
            Method m = AbstractWhatsAppConnector.class.getDeclaredMethod("readMandatoryStringInput", String.class);
            m.setAccessible(true);
            m.invoke(connector, "myField");
        }).hasCauseInstanceOf(IllegalArgumentException.class);
    }

    // ─── connect and disconnect ───

    @Test
    void should_connect_create_client() throws Exception {
        connector.setInputParameters(validInputs());
        connector.connect();
        var f = AbstractWhatsAppConnector.class.getDeclaredField("client");
        f.setAccessible(true);
        assertThat(f.get(connector)).isNotNull();
    }

    @Test
    void should_disconnect_without_error() throws Exception {
        connector.disconnect();
    }

    // ─── validateInputParameters wraps IllegalArgumentException ───

    @Test
    void should_validateInputParameters_throw_ConnectorValidationException_on_missing_mandatory() {
        Map<String, Object> m = validInputs();
        m.remove("phoneNumberId");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("mandatory");
    }

    @Test
    void should_validateInputParameters_pass_with_valid_inputs() throws Exception {
        connector.setInputParameters(validInputs());
        connector.validateInputParameters();
    }
}
