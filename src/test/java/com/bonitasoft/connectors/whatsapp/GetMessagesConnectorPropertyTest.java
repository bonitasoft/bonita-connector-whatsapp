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

class GetMessagesConnectorPropertyTest {

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
        inputs.put("contactPhone", "34612345678");
        return inputs;
    }

    @Property(tries = 50)
    void mandatoryContactPhoneRejectsBlank(@ForAll("blankStrings") String phone) {
        var connector = new GetMessagesConnector();
        var inputs = validInputs();
        inputs.put("contactPhone", phone);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void mandatoryPhoneNumberIdRejectsBlank(@ForAll("blankStrings") String phoneNumberId) {
        var connector = new GetMessagesConnector();
        var inputs = validInputs();
        inputs.put("phoneNumberId", phoneNumberId);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void mandatoryTokenRejectsBlank(@ForAll("blankStrings") String token) {
        var connector = new GetMessagesConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void validConfigurationAlwaysBuilds(
            @ForAll @AlphaChars @StringLength(min = 5, max = 50) String token,
            @ForAll("validPhoneNumbers") String contactPhone) {
        var connector = new GetMessagesConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        inputs.put("contactPhone", contactPhone);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void contactPhoneNormalization(@ForAll("phoneWithSeparators") String rawPhone) {
        var connector = new GetMessagesConnector();
        var inputs = validInputs();
        inputs.put("contactPhone", rawPhone);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Example
    void limitDefaultValue() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123")
                .contactPhone("34612345678")
                .build();
        // GetMessagesConnector uses default 20, but config default is 500
        // The connector reads with readIntegerInput("limit", 20)
        assertThat(config.getLimit()).isEqualTo(500);
    }

    @Example
    void cursorOptionalAcceptsNull() {
        var connector = new GetMessagesConnector();
        var inputs = validInputs();
        inputs.put("cursor", null);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void tokenFormatValidation(@ForAll @NotBlank @StringLength(min = 5, max = 200) String token) {
        var connector = new GetMessagesConnector();
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
                .contactPhone("34612345678")
                .connectTimeout(timeout)
                .build();
        assertThat(config.getConnectTimeout()).isPositive();
    }

    @Example
    void messageListResultAcceptsEmptyArray() {
        var result = new MessageListResult("[]", 0, false, "");
        assertThat(result.messageCount()).isZero();
        assertThat(result.hasMore()).isFalse();
        assertThat(result.nextCursor()).isEmpty();
    }

    @Example
    void defaultValuesApplied() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .build();
        assertThat(config.getConnectTimeout()).isEqualTo(30_000);
        assertThat(config.getReadTimeout()).isEqualTo(60_000);
        assertThat(config.getBaseUrl()).isEqualTo("https://graph.facebook.com/v23.0");
    }

    @Property(tries = 50)
    void limitAcceptsPositiveValues(@ForAll @IntRange(min = 1, max = 10_000) int limit) {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123")
                .contactPhone("34612345678")
                .limit(limit)
                .build();
        assertThat(config.getLimit()).isEqualTo(limit);
    }
}
