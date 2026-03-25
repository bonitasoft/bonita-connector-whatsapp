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
}
