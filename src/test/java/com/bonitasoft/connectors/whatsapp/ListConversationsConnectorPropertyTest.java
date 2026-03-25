package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;
import org.bonitasoft.engine.connector.ConnectorValidationException;

class ListConversationsConnectorPropertyTest {

    @Provide
    Arbitrary<String> blankStrings() {
        return Arbitraries.of("", " ", "\t", "\n");
    }

    @Provide
    Arbitrary<String> validGranularities() {
        return Arbitraries.of("HALF_HOUR", "DAILY", "MONTHLY");
    }

    @Provide
    Arbitrary<String> validConversationTypes() {
        return Arbitraries.of("REGULAR", "BUSINESS_INITIATED", "USER_INITIATED", "REFERRAL_CONVERSION");
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> inputs = new HashMap<>();
        inputs.put("permanentToken", "test-token-123");
        inputs.put("wabaId", "102290129340399");
        inputs.put("startDate", "2026-03-01");
        inputs.put("endDate", "2026-03-24");
        return inputs;
    }

    @Property
    void mandatoryWabaIdRejectsBlank(@ForAll("blankStrings") String wabaId) {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        inputs.put("wabaId", wabaId);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property
    void mandatoryStartDateRejectsBlank(@ForAll("blankStrings") String startDate) {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        inputs.put("startDate", startDate);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property
    void mandatoryEndDateRejectsBlank(@ForAll("blankStrings") String endDate) {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        inputs.put("endDate", endDate);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property
    void mandatoryTokenRejectsBlank(@ForAll("blankStrings") String token) {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class);
    }

    @Property
    void validConfigurationAlwaysBuilds(@ForAll @AlphaChars @StringLength(min = 5, max = 50) String token) {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        inputs.put("permanentToken", token);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property
    void dateRangeWithin90DaysAccepted(@ForAll @IntRange(min = 1, max = 89) int daysBetween) {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(daysBetween);
        inputs.put("startDate", start.toString());
        inputs.put("endDate", end.toString());
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property
    void dateRangeExceeding90DaysRejected(@ForAll @IntRange(min = 91, max = 365) int daysBetween) {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = start.plusDays(daysBetween);
        inputs.put("startDate", start.toString());
        inputs.put("endDate", end.toString());
        connector.setInputParameters(inputs);
        assertThatThrownBy(connector::validateInputParameters)
                .isInstanceOf(ConnectorValidationException.class)
                .hasMessageContaining("90-day");
    }

    @Property
    void granularityOptionalAcceptsNull() {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        inputs.remove("granularity");
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }

    @Property
    void limitDefaultValue() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .wabaId("123")
                .build();
        assertThat(config.getLimit()).isEqualTo(500);
    }

    @Property
    void limitAcceptsPositiveValues(@ForAll @IntRange(min = 1, max = 10_000) int limit) {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .wabaId("123")
                .limit(limit)
                .build();
        assertThat(config.getLimit()).isEqualTo(limit);
    }

    @Property
    void defaultGranularityApplied() {
        var config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .wabaId("123")
                .build();
        assertThat(config.getGranularity()).isEqualTo("DAILY");
    }

    @Property
    void cursorOptionalAcceptsAnyString(@ForAll @AlphaChars @StringLength(min = 1, max = 100) String cursor) {
        var connector = new ListConversationsConnector();
        var inputs = validInputs();
        inputs.put("cursor", cursor);
        connector.setInputParameters(inputs);
        assertThatCode(connector::validateInputParameters).doesNotThrowAnyException();
    }
}
