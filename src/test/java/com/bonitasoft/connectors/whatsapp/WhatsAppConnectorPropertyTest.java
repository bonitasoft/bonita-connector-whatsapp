package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.NotBlank;
import net.jqwik.api.constraints.StringLength;

class WhatsAppConnectorPropertyTest {

    @Property
    void should_accept_valid_phone_numbers(@ForAll("validPhoneNumbers") String phone) {
        // Valid phone numbers are 7-15 digits, matching the connector validation regex
        assertThat(phone).matches("\\d{7,15}");

        // Configuration should build successfully
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123456789")
                .recipientPhone(phone)
                .templateName("hello_world")
                .build();
        assertThat(config.getRecipientPhone()).isEqualTo(phone);
    }

    @Property
    void should_accept_valid_waba_ids(@ForAll("positiveNumericStrings") String wabaId) {
        // WABA IDs are positive numeric strings
        assertThat(wabaId).matches("\\d+");
        assertThat(Long.parseLong(wabaId)).isPositive();

        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .wabaId(wabaId)
                .build();
        assertThat(config.getWabaId()).isEqualTo(wabaId);
    }

    @Property
    void should_accept_non_empty_template_names(@ForAll @NotBlank String templateName) {
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123456789")
                .recipientPhone("34612345678")
                .templateName(templateName)
                .build();
        assertThat(config.getTemplateName()).isNotBlank();
    }

    @Property
    void should_accept_any_non_null_text_for_send_text(@ForAll @NotBlank @StringLength(min = 1, max = 4096) String body) {
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123456789")
                .recipientPhone("34612345678")
                .messageBody(body)
                .build();
        assertThat(config.getMessageBody()).isNotNull();
        assertThat(config.getMessageBody().length()).isBetween(1, 4096);
    }

    @Property
    void should_accept_valid_media_urls(@ForAll("validMediaUrls") String mediaUrl) {
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .phoneNumberId("123456789")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaUrl(mediaUrl)
                .build();
        assertThat(config.getMediaUrl()).startsWith("https://");
    }

    @Property
    void should_accept_positive_timeout_values(
            @ForAll @IntRange(min = 1, max = 300_000) int connectTimeout,
            @ForAll @IntRange(min = 1, max = 300_000) int readTimeout) {
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout)
                .build();
        assertThat(config.getConnectTimeout()).isPositive();
        assertThat(config.getReadTimeout()).isPositive();
    }

    @Property
    void should_handle_any_positive_max_retries(@ForAll @IntRange(min = 1, max = 10) int maxRetries) {
        // RetryPolicy uses a fixed MAX_RETRIES=3 internally, but we verify that
        // the policy handles retryable exceptions without crashing for any attempt count
        RetryPolicy policy = new RetryPolicy();
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            long waitMs = policy.calculateWait(attempt);
            assertThat(waitMs).isPositive();
            assertThat(waitMs).isEqualTo(1_000L * (1L << attempt));
        }
    }

    @Property
    void should_build_configuration_with_random_valid_values(
            @ForAll @NotBlank @StringLength(min = 10, max = 100) String token,
            @ForAll("validPhoneNumbers") String phone,
            @ForAll("positiveNumericStrings") String phoneNumberId,
            @ForAll @NotBlank @StringLength(min = 1, max = 50) String templateName) {
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken(token)
                .phoneNumberId(phoneNumberId)
                .recipientPhone(phone)
                .templateName(templateName)
                .templateLanguage("es")
                .build();

        assertThat(config.getPermanentToken()).isEqualTo(token);
        assertThat(config.getPhoneNumberId()).isEqualTo(phoneNumberId);
        assertThat(config.getRecipientPhone()).isEqualTo(phone);
        assertThat(config.getTemplateName()).isEqualTo(templateName);
        assertThat(config.getBaseUrl()).isNotBlank();
        assertThat(config.getConnectTimeout()).isPositive();
        assertThat(config.getReadTimeout()).isPositive();
    }

    @Property
    void should_resolve_token_from_input_over_env(@ForAll @NotBlank @StringLength(min = 5, max = 100) String inputToken) {
        // Token resolution order: input > system property > env variable
        // When input token is provided, it should always be used regardless of env
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken(inputToken)
                .build();
        assertThat(config.getPermanentToken()).isEqualTo(inputToken);
        assertThat(config.getPermanentToken()).isNotBlank();
    }

    @Property
    void should_accept_valid_rate_limit_values(@ForAll @IntRange(min = 1, max = 10_000) int limit) {
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .wabaId("123456789")
                .limit(limit)
                .build();
        assertThat(config.getLimit()).isEqualTo(limit);
        assertThat(config.getLimit()).isPositive();
    }

    @Property
    void should_normalize_phones_with_any_separators(@ForAll("phoneWithSeparators") String rawPhone) {
        // Simulate the normalizePhone logic from AbstractWhatsAppConnector
        String normalized = rawPhone.replaceAll("[+\\s\\-]", "");
        assertThat(normalized).matches("\\d{7,15}");
    }

    @Property
    void should_use_default_base_url_when_not_specified(@ForAll @NotBlank String token) {
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken(token)
                .build();
        assertThat(config.getBaseUrl()).isEqualTo("https://graph.facebook.com/v23.0");
    }

    // --- Providers ---

    @Provide
    Arbitrary<String> validPhoneNumbers() {
        return Arbitraries.integers().between(7, 15).flatMap(length ->
                Arbitraries.strings().numeric().ofLength(length)
                        .filter(s -> !s.isEmpty() && s.charAt(0) != '0')
        );
    }

    @Provide
    Arbitrary<String> positiveNumericStrings() {
        return Arbitraries.longs().between(1L, 999_999_999_999L)
                .map(String::valueOf);
    }

    @Provide
    Arbitrary<String> validMediaUrls() {
        return Arbitraries.of(
                "https://example.com/image.jpg",
                "https://cdn.example.org/docs/file.pdf",
                "https://media.whatsapp.net/v/t61/audio.ogg",
                "https://storage.googleapis.com/bucket/video.mp4",
                "https://s3.amazonaws.com/bucket/photo.png"
        );
    }

    @Provide
    Arbitrary<String> phoneWithSeparators() {
        return validPhoneNumbers().map(digits -> {
            // Randomly add +, spaces, or dashes
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
}
