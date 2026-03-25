package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Assume;
import net.jqwik.api.ForAll;
import net.jqwik.api.Example;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.bonitasoft.engine.connector.ConnectorValidationException;

class SendTemplateConnectorPropertyTest {

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "\t", "\n");
    }

    @Provide
    Arbitrary<String> validPhoneNumbers() {
        return Arbitraries.integers().between(7, 15).flatMap(length ->
                Arbitraries.strings().numeric().ofLength(length)
                        .filter(s -> !s.isEmpty() && s.charAt(0) != '0'));
    }

    @Provide
    Arbitrary<String> phoneWithSeparators() {
        return validPhoneNumbers().map(digits -> {
            StringBuilder sb = new StringBuilder("+");
            for (int i = 0; i < digits.length(); i++) {
                if (i > 0 && i % 3 == 0) {
                    sb.append(i % 2 == 0 ? " " : "-");
                }
                sb.append(digits.charAt(i));
            }
            return sb.toString();
        });
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

    @Property(tries = 50)
    void mandatoryTemplateNameRejectsBlank(@ForAll("blankStrings") String templateName) {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("templateName", templateName);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void mandatoryToRejectsBlank(@ForAll("blankStrings") String recipientPhone) {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("recipientPhone", recipientPhone);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void mandatoryTokenRejectsBlank(@ForAll("blankStrings") String token) {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void validConfigurationAlwaysBuilds(
            @ForAll @AlphaChars @StringLength(min = 5, max = 50) String token,
            @ForAll("validPhoneNumbers") String phone,
            @ForAll @AlphaChars @StringLength(min = 1, max = 50) String templateName) {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        inputs.put("recipientPhone", phone);
        inputs.put("templateName", templateName);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Example
    void languageCodeDefaultApplied() {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.remove("templateLanguage");
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Example
    void headerParametersOptionalAcceptsNull() {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("templateParameters", null);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Example
    void bodyParametersOptionalAcceptsNull() {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.remove("templateParameters");
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void phoneNumberFormatValidation(@ForAll("phoneWithSeparators") String rawPhone) {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("recipientPhone", rawPhone);
        connector.setInputParameters(inputs);
        // Phones with separators should normalize and validate successfully
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void tokenFormatValidation(@ForAll @NotBlank @StringLength(min = 10, max = 200) String token) {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void timeoutPositiveOnly(@ForAll @IntRange(min = 1, max = 300_000) int timeout) {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123")
                .recipientPhone("34612345678")
                .templateName("test")
                .connectTimeout(timeout)
                .build();
        assertThat(config.getConnectTimeout()).isPositive();
    }

    @Example
    void defaultBaseUrlApplied() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123")
                .recipientPhone("34612345678")
                .templateName("test")
                .build();
        assertThat(config.getBaseUrl()).isEqualTo("https://graph.facebook.com/v23.0");
        assertThat(config.getTemplateLanguage()).isEqualTo("es");
    }

    @Property(tries = 50)
    void mandatoryPhoneNumberIdRejectsBlank(@ForAll("blankStrings") String phoneNumberId) {
        var connector = new SendTemplateConnector();
        var inputs = validInputs();
        inputs.put("phoneNumberId", phoneNumberId);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }
}
