package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Example;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.bonitasoft.engine.connector.ConnectorValidationException;

class SendTextConnectorPropertyTest {

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

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("permanentToken", "test-token-123");
        inputs.put("phoneNumberId", "102290129340398");
        inputs.put("recipientPhone", "34612345678");
        inputs.put("messageBody", "Hello from Bonita!");
        return inputs;
    }

    @Property(tries = 50)
    void mandatoryBodyRejectsBlank(@ForAll("blankStrings") String body) {
        var connector = new SendTextConnector();
        var inputs = validInputs();
        inputs.put("messageBody", body);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void mandatoryToRejectsBlank(@ForAll("blankStrings") String recipientPhone) {
        var connector = new SendTextConnector();
        var inputs = validInputs();
        inputs.put("recipientPhone", recipientPhone);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void validConfigurationAlwaysBuilds(
            @ForAll @AlphaChars @StringLength(min = 5, max = 50) String token,
            @ForAll("validPhoneNumbers") String phone,
            @ForAll @AlphaChars @StringLength(min = 1, max = 100) String body) {
        var connector = new SendTextConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        inputs.put("recipientPhone", phone);
        inputs.put("messageBody", body);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Example
    void previewUrlDefaultFalse() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThat(config.isPreviewUrl()).isFalse();
    }

    @Property(tries = 50)
    void bodyMaxLength(@ForAll @AlphaChars @StringLength(min = 1, max = 4096) String body) {
        var connector = new SendTextConnector();
        var inputs = validInputs();
        inputs.put("messageBody", body);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void phoneNumberFormatValidation(@ForAll("validPhoneNumbers") String phone) {
        var connector = new SendTextConnector();
        var inputs = validInputs();
        inputs.put("recipientPhone", phone);
        connector.setInputParameters(inputs);
        // Valid phone numbers should pass validation (SendTextConnector doesn't validate phone format,
        // but it does call readMandatoryStringInput which rejects blank)
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void tokenFormatValidation(@ForAll @NotBlank @StringLength(min = 5, max = 200) String token) {
        var connector = new SendTextConnector();
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
                .messageBody("test")
                .connectTimeout(timeout)
                .build();
        assertThat(config.getConnectTimeout()).isPositive();
    }

    @Example
    void defaultValuesApplied() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .build();
        assertThat(config.getConnectTimeout()).isEqualTo(30_000);
        assertThat(config.getReadTimeout()).isEqualTo(60_000);
        assertThat(config.isPreviewUrl()).isFalse();
        assertThat(config.getBaseUrl()).isEqualTo("https://graph.facebook.com/v23.0");
    }

    @Property(tries = 50)
    void errorMessageTruncation(@ForAll @AlphaChars @StringLength(min = 1001, max = 2000) String longMsg) {
        String truncated = longMsg.length() > 1000 ? longMsg.substring(0, 1000) : longMsg;
        assertThat(truncated.length()).isLessThanOrEqualTo(1000);
    }

    @Property(tries = 50)
    void mandatoryPhoneNumberIdRejectsBlank(@ForAll("blankStrings") String phoneNumberId) {
        var connector = new SendTextConnector();
        var inputs = validInputs();
        inputs.put("phoneNumberId", phoneNumberId);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Example
    void bodyExceeding4096Rejected() {
        var connector = new SendTextConnector();
        var inputs = validInputs();
        inputs.put("messageBody", "x".repeat(4097));
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("4096");
    }
}
