package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WhatsAppClientTest {

    private MockWebServer server;
    private WhatsAppClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = server.url("/v23.0").toString();
        WhatsAppConfiguration config = WhatsAppConfiguration.builder()
                .permanentToken("test-token")
                .baseUrl(baseUrl)
                .connectTimeout(5000)
                .readTimeout(5000)
                .build();
        client = new WhatsAppClient(config, new RetryPolicy() {
            @Override void sleep(long millis) {}
        });
    }

    @AfterEach
    void tearDown() throws IOException { server.shutdown(); }

    @Test
    void should_send_template_message_successfully() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.test123"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .templateName("order_confirmation")
                .templateLanguage("es")
                .build();
        SendMessageResult result = client.sendTemplateMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.test123");
        assertThat(result.recipientPhone()).isEqualTo("34612345678");
    }

    @Test
    void should_send_text_message_successfully() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.text1"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("Hello!")
                .build();
        SendMessageResult result = client.sendTextMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.text1");
    }

    @Test
    void should_handle_meta_error_response() {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":131047,"message":"24h window expired","type":"OAuthException"}}
                """).setResponseCode(400));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> {
                    WhatsAppException we = (WhatsAppException) e;
                    assertThat(we.getErrorCode()).isEqualTo(131047);
                    assertThat(we.getStatusCode()).isEqualTo(400);
                });
    }

    @Test
    void should_handle_template_not_found() {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":132001,"message":"Template name does not exist","type":"OAuthException"}}
                """).setResponseCode(400));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .templateName("nonexistent")
                .templateLanguage("es")
                .build();
        assertThatThrownBy(() -> client.sendTemplateMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).getErrorCode()).isEqualTo(132001));
    }

    @Test
    void should_get_message_status() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"status":"delivered","timestamp":"2026-03-24T09:15:33Z","error_code":""}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .messageId("wamid.1")
                .build();
        MessageStatusResult result = client.getMessageStatus(cfg);
        assertThat(result.status()).isEqualTo("delivered");
    }

    @Test
    void should_list_conversations() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[{"count":12}]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .limit(500)
                .build();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.hasMore()).isFalse();
    }

    @Test
    void should_get_messages() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"data":[{"id":"msg1","text":"Hello"}],"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .contactPhone("34612345678")
                .limit(20)
                .build();
        MessageListResult result = client.getMessages(cfg);
        assertThat(result.messageCount()).isEqualTo(1);
    }

    @Test
    void should_convert_iso_date_to_unix_timestamp() {
        long ts = client.toUnixTimestamp("2026-03-01");
        assertThat(ts).isGreaterThan(0);
    }

    @Test
    void should_pass_through_unix_timestamp() {
        long ts = client.toUnixTimestamp("1740787200");
        assertThat(ts).isEqualTo(1740787200L);
    }

    @Test
    void should_retry_on_429_then_succeed() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":130429,"message":"Rate limit","type":"OAuthException"}}
                """).setResponseCode(429));
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.retry"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("retry test")
                .build();
        SendMessageResult result = client.sendTextMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.retry");
    }

    @Test
    void should_send_media_message() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.media1"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("document")
                .mediaUrl("https://example.com/doc.pdf")
                .caption("Invoice")
                .filename("invoice.pdf")
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.media1");
    }

    @Test
    void should_send_template_with_parameters() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.tpl"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .templateName("order_confirmation")
                .templateLanguage("es")
                .templateParameters("[\"ORD-001\",\"Madrid\",\"2026-04-15\"]")
                .build();
        SendMessageResult result = client.sendTemplateMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.tpl");
    }

    @Test
    void should_handle_500_server_error() {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":1,"message":"Internal server error","type":"OAuthException"}}
                """).setResponseCode(500));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        // Enqueue enough responses for all retries
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setBody("""
                    {"error":{"code":1,"message":"Internal server error","type":"OAuthException"}}
                    """).setResponseCode(500));
        }
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).getStatusCode()).isEqualTo(500));
    }

    @Test
    void should_handle_401_unauthorized() {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":190,"message":"Invalid OAuth access token","type":"OAuthException"}}
                """).setResponseCode(401));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("bad-token")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> {
                    WhatsAppException we = (WhatsAppException) e;
                    assertThat(we.getStatusCode()).isEqualTo(401);
                    assertThat(we.getErrorCode()).isEqualTo(190);
                });
    }

    @Test
    void should_send_media_with_id_instead_of_url() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.mediaid"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaId("media-123")
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.mediaid");
    }

    @Test
    void should_handle_malformed_error_response() {
        server.enqueue(new MockResponse().setBody("not json").setResponseCode(400));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .hasMessageContaining("HTTP 400");
    }

    @Test
    void should_include_authorization_header() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.auth"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        client.sendTextMessage(cfg);
        var request = server.takeRequest();
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer test-token");
    }

    @Test
    void should_get_messages_with_pagination() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"data":[{"id":"msg1"}],"paging":{"next":"http://next","cursors":{"after":"cursor1"}}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .contactPhone("34612345678")
                .limit(10)
                .build();
        MessageListResult result = client.getMessages(cfg);
        assertThat(result.messageCount()).isEqualTo(1);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("cursor1");
    }

    @Test
    void should_list_conversations_with_pagination() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[{"count":10}]},"paging":{"next":"http://next","cursors":{"after":"c2"}}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .limit(500)
                .build();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.hasMore()).isTrue();
        assertThat(result.nextCursor()).isEqualTo("c2");
    }

    @Test
    void should_throw_on_blank_date() {
        assertThatThrownBy(() -> client.toUnixTimestamp(""))
                .isInstanceOf(WhatsAppException.class);
    }

    @Test
    void should_throw_on_null_date() {
        assertThatThrownBy(() -> client.toUnixTimestamp(null))
                .isInstanceOf(WhatsAppException.class);
    }

    @Test
    void should_handle_empty_response_body() {
        server.enqueue(new MockResponse().setBody("").setResponseCode(400));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class);
    }

    @Test
    void should_send_audio_without_caption() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.audio1"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("audio")
                .mediaUrl("https://example.com/audio.ogg")
                .caption("Ignored caption for audio")
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.audio1");
        // Verify the request body does NOT contain caption for audio
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).doesNotContain("caption");
    }

    // --- isRetryableError boundary tests (via handleErrorResponse) ---

    @Test
    void should_mark_429_as_retryable() {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":0,"message":"Rate limit","type":"OAuthException"}}
                """).setResponseCode(429));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        // Enqueue retries
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse().setBody("""
                    {"error":{"code":0,"message":"Rate limit","type":"OAuthException"}}
                    """).setResponseCode(429));
        }
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> {
                    WhatsAppException we = (WhatsAppException) e;
                    assertThat(we.getStatusCode()).isEqualTo(429);
                    assertThat(we.isRetryable()).isTrue();
                });
    }

    @Test
    void should_mark_500_as_retryable() {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setBody("""
                    {"error":{"code":0,"message":"Server error","type":"OAuthException"}}
                    """).setResponseCode(500));
        }
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).isRetryable()).isTrue());
    }

    @Test
    void should_mark_501_as_retryable() {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setBody("""
                    {"error":{"code":0,"message":"Not implemented","type":"OAuthException"}}
                    """).setResponseCode(501));
        }
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).isRetryable()).isTrue());
    }

    @Test
    void should_mark_499_as_not_retryable() {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":0,"message":"Client error","type":"OAuthException"}}
                """).setResponseCode(499));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).isRetryable()).isFalse());
    }

    @Test
    void should_mark_400_as_not_retryable() {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":0,"message":"Bad request","type":"OAuthException"}}
                """).setResponseCode(400));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).isRetryable()).isFalse());
    }

    @Test
    void should_mark_403_as_not_retryable() {
        server.enqueue(new MockResponse().setBody("""
                {"error":{"code":0,"message":"Forbidden","type":"OAuthException"}}
                """).setResponseCode(403));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).isRetryable()).isFalse());
    }

    @Test
    void should_mark_meta_code_130429_as_retryable() {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setBody("""
                    {"error":{"code":130429,"message":"Rate limit","type":"OAuthException"}}
                    """).setResponseCode(400));
        }
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).isRetryable()).isTrue());
    }

    @Test
    void should_mark_meta_code_131056_as_retryable() {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setBody("""
                    {"error":{"code":131056,"message":"Pair rate limit","type":"OAuthException"}}
                    """).setResponseCode(400));
        }
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).isRetryable()).isTrue());
    }

    @Test
    void should_mark_meta_code_4_as_retryable() {
        for (int i = 0; i < 4; i++) {
            server.enqueue(new MockResponse().setBody("""
                    {"error":{"code":4,"message":"App level rate","type":"OAuthException"}}
                    """).setResponseCode(400));
        }
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .messageBody("test")
                .build();
        assertThatThrownBy(() -> client.sendTextMessage(cfg))
                .isInstanceOf(WhatsAppException.class)
                .satisfies(e -> assertThat(((WhatsAppException) e).isRetryable()).isTrue());
    }

    // --- sendMediaMessage edge cases ---

    @Test
    void should_send_media_without_caption_when_null() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.nocap"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaUrl("https://example.com/img.jpg")
                .caption(null)
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.nocap");
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).doesNotContain("caption");
    }

    @Test
    void should_send_media_without_caption_when_blank() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.blankcap"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaUrl("https://example.com/img.jpg")
                .caption("  ")
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.blankcap");
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).doesNotContain("caption");
    }

    @Test
    void should_send_image_with_caption() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.imgcap"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaUrl("https://example.com/img.jpg")
                .caption("My caption")
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.imgcap");
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("caption");
        assertThat(body).contains("My caption");
    }

    @Test
    void should_send_document_with_filename() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.docfn"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("document")
                .mediaUrl("https://example.com/doc.pdf")
                .filename("report.pdf")
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.docfn");
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("filename");
        assertThat(body).contains("report.pdf");
    }

    @Test
    void should_not_include_filename_for_image() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.imgfn"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaUrl("https://example.com/img.jpg")
                .filename("ignored.jpg")
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.imgfn");
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).doesNotContain("filename");
    }

    @Test
    void should_send_document_without_filename_when_null() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.docnofn"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("document")
                .mediaUrl("https://example.com/doc.pdf")
                .filename(null)
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.docnofn");
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).doesNotContain("filename");
    }

    // --- listConversations with optional fields ---

    @Test
    void should_list_conversations_with_conversation_type() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .conversationType("REGULAR")
                .limit(500)
                .build();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        assertThat(result.totalCount()).isEqualTo(0);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).contains("conversation_type=REGULAR");
    }

    @Test
    void should_list_conversations_without_conversation_type() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .conversationType(null)
                .limit(500)
                .build();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).doesNotContain("conversation_type");
    }

    @Test
    void should_list_conversations_with_direction() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .direction("BUSINESS_INITIATED")
                .limit(500)
                .build();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).contains("conversation_direction=BUSINESS_INITIATED");
    }

    @Test
    void should_list_conversations_without_direction() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .direction(null)
                .limit(500)
                .build();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).doesNotContain("conversation_direction");
    }

    @Test
    void should_list_conversations_with_cursor() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .cursor("abc123")
                .limit(500)
                .build();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).contains("after=abc123");
    }

    @Test
    void should_list_conversations_without_cursor() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .cursor(null)
                .limit(500)
                .build();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).doesNotContain("after=");
    }

    // --- getMessages with cursor ---

    @Test
    void should_get_messages_with_cursor() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"data":[],"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .contactPhone("34612345678")
                .limit(20)
                .cursor("cursor_xyz")
                .build();
        MessageListResult result = client.getMessages(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).contains("after=cursor_xyz");
    }

    @Test
    void should_get_messages_without_cursor() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"data":[],"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .contactPhone("34612345678")
                .limit(20)
                .cursor(null)
                .build();
        MessageListResult result = client.getMessages(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).doesNotContain("after=");
    }

    @Test
    void should_send_media_with_media_url_not_media_id() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.urlonly"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaUrl("https://example.com/img.jpg")
                .mediaId(null)
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.urlonly");
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).contains("link");
        assertThat(body).doesNotContain("\"id\"");
    }

    @Test
    void should_send_media_with_blank_url_uses_media_id() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"messages":[{"id":"wamid.idonly"}],"contacts":[{"wa_id":"34612345678"}]}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .recipientPhone("34612345678")
                .mediaType("image")
                .mediaUrl("  ")
                .mediaId("media-456")
                .build();
        SendMessageResult result = client.sendMediaMessage(cfg);
        assertThat(result.messageId()).isEqualTo("wamid.idonly");
        var request = server.takeRequest();
        String body = request.getBody().readUtf8();
        assertThat(body).doesNotContain("link");
    }

    // --- listConversations with blank direction ---

    @Test
    void should_list_conversations_with_blank_direction_skips_param() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .direction("  ")
                .limit(500)
                .build();
        client.listConversations(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).doesNotContain("conversation_direction");
    }

    @Test
    void should_list_conversations_with_blank_conversation_type_skips_param() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .conversationType("  ")
                .limit(500)
                .build();
        client.listConversations(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).doesNotContain("conversation_type");
    }

    @Test
    void should_get_messages_with_blank_cursor_skips_param() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"data":[],"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .phoneNumberId("12345")
                .contactPhone("34612345678")
                .limit(20)
                .cursor("  ")
                .build();
        client.getMessages(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).doesNotContain("after=");
    }

    @Test
    void should_list_conversations_with_blank_cursor_skips_param() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                {"conversation_analytics":{"data":[]},"paging":{}}
                """).setResponseCode(200));
        WhatsAppConfiguration cfg = WhatsAppConfiguration.builder()
                .baseUrl(server.url("/v23.0").toString())
                .permanentToken("tok")
                .wabaId("waba1")
                .startDate("1740787200")
                .endDate("1743379200")
                .granularity("DAILY")
                .cursor("  ")
                .limit(500)
                .build();
        client.listConversations(cfg);
        var request = server.takeRequest();
        String url = request.getRequestUrl().toString();
        assertThat(url).doesNotContain("after=");
    }
}
