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

class SendMediaConnectorPropertyTest {

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "\t", "\n");
    }

    @Provide
    Arbitrary<String> validMediaTypes() {
        return Arbitraries.of("image", "document", "audio", "video");
    }

    @Provide
    Arbitrary<String> invalidMediaTypes() {
        return Arbitraries.of("gif", "pdf", "text", "binary", "attachment");
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
        inputs.put("mediaType", "document");
        inputs.put("mediaUrl", "https://example.com/doc.pdf");
        return inputs;
    }

    @Property(tries = 50)
    void mandatoryMediaTypeRejectsBlank(@ForAll("blankStrings") String mediaType) {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("mediaType", mediaType);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void mandatoryRecipientPhoneRejectsBlank(@ForAll("blankStrings") String phone) {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("recipientPhone", phone);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void mandatoryTokenRejectsBlank(@ForAll("blankStrings") String token) {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property(tries = 50)
    void validConfigurationAlwaysBuilds(
            @ForAll("validMediaTypes") String mediaType,
            @ForAll("validPhoneNumbers") String phone) {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("mediaType", mediaType);
        inputs.put("recipientPhone", phone);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void invalidMediaTypeRejected(@ForAll("invalidMediaTypes") String mediaType) {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("mediaType", mediaType);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("Invalid mediaType");
    }

    @Example
    void mediaUrlOrMediaIdRequired() {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.remove("mediaUrl");
        // No mediaId either
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("mediaUrl or mediaId");
    }

    @Property(tries = 50)
    void captionOptionalAcceptsNull(@ForAll("validMediaTypes") String mediaType) {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("mediaType", mediaType);
        inputs.put("caption", null);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Example
    void filenameOptionalAcceptsNull() {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("filename", null);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void mediaIdAcceptedAlternativeToUrl(@ForAll @AlphaChars @StringLength(min = 5, max = 50) String mediaId) {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.remove("mediaUrl");
        inputs.put("mediaId", mediaId);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property(tries = 50)
    void timeoutPositiveOnly(@ForAll @IntRange(min = 1, max = 300_000) int timeout) {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaUrl("https://example.com/img.jpg")
                .connectTimeout(timeout)
                .build();
        assertThat(config.getConnectTimeout()).isPositive();
    }

    @Property(tries = 50)
    void mandatoryPhoneNumberIdRejectsBlank(@ForAll("blankStrings") String phoneNumberId) {
        var connector = new SendMediaConnector();
        var inputs = validInputs();
        inputs.put("phoneNumberId", phoneNumberId);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
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
}
