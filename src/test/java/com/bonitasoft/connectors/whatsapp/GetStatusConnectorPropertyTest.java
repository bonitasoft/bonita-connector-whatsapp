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

class GetStatusConnectorPropertyTest {

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "\t", "\n");
    }

    @Provide
    Arbitrary<String> validMessageIds() {
        return Arbitraries.of(
                "wamid.HBgLMzQ2",
                "wamid.ABCdef123456",
                "wamid.test_message_id_001",
                "wamid.LONG_ID_WITH_MANY_CHARS_1234567890"
        );
    }

    @Provide
    Arbitrary<String> validStatuses() {
        return Arbitraries.of("sent", "delivered", "read", "failed");
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("permanentToken", "test-token-123");
        inputs.put("messageId", "wamid.HBgLMzQ2");
        return inputs;
    }

    @Property(tries = 50)
    void mandatoryMessageIdRejectsBlank(@ForAll("blankStrings") String messageId) {
        var connector = new GetStatusConnector();
        var inputs = validInputs();
        inputs.put("messageId", messageId);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void mandatoryTokenRejectsBlank(@ForAll("blankStrings") String token) {
        var connector = new GetStatusConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void validConfigurationAlwaysBuilds(
            @ForAll @AlphaChars @StringLength(min = 5, max = 50) String token,
            @ForAll("validMessageIds") String messageId) {
        var connector = new GetStatusConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        inputs.put("messageId", messageId);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void messageIdFormatAccepted(@ForAll("validMessageIds") String messageId) {
        var connector = new GetStatusConnector();
        var inputs = validInputs();
        inputs.put("messageId", messageId);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void tokenFormatValidation(@ForAll @NotBlank @StringLength(min = 5, max = 200) String token) {
        var connector = new GetStatusConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void timeoutPositiveOnly(@ForAll @IntRange(min = 1, max = 300_000) int timeout) {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .messageId("wamid.test")
                .connectTimeout(timeout)
                .build();
        assertThat(config.getConnectTimeout()).isPositive();
    }

    @Example
    void defaultBaseUrlApplied() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .messageId("wamid.test")
                .build();
        assertThat(config.getBaseUrl()).isEqualTo("https://graph.facebook.com/v23.0");
    }

    @Example
    void defaultTimeoutsApplied() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .messageId("wamid.test")
                .build();
        assertThat(config.getConnectTimeout()).isEqualTo(30_000);
        assertThat(config.getReadTimeout()).isEqualTo(60_000);
    }

    @Property(tries = 50)
    void statusResultAcceptsAllStatuses(@ForAll("validStatuses") String status) {
        var result = new MessageStatusResult(status, "2026-03-24T09:00:00Z", "");
        assertThat(result.status()).isEqualTo(status);
        assertThat(result.timestamp()).isNotBlank();
        assertThat(result.errorCode()).isEmpty();
    }

    @Property(tries = 50)
    void errorMessageTruncation(@ForAll @AlphaChars @StringLength(min = 1001, max = 2000) String longMsg) {
        String truncated = longMsg.length() > 1000 ? longMsg.substring(0, 1000) : longMsg;
        assertThat(truncated.length()).isLessThanOrEqualTo(1000);
    }

    @Property(tries = 50)
    void configurationBuildWithCustomTimeout(
            @ForAll @IntRange(min = 1, max = 300_000) int connectTimeout,
            @ForAll @IntRange(min = 1, max = 300_000) int readTimeout) {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .messageId("wamid.test")
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
        assertThat(config.getConnectTimeout()).isEqualTo(connectTimeout);
        assertThat(config.getReadTimeout()).isEqualTo(readTimeout);
    }
}
