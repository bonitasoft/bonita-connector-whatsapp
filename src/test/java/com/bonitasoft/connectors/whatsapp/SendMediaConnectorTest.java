package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.bonitasoft.engine.connector.ConnectorValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SendMediaConnectorTest {

    @Mock private WhatsAppClient mockClient;
    private SendMediaConnector connector;

    @BeforeEach void setUp() { connector = new SendMediaConnector(); }

    private void injectMockClient() throws Exception {
        var f = AbstractWhatsAppConnector.class.getDeclaredField("client"); f.setAccessible(true); f.set(connector, mockClient);
    }

    private Map<String, Object> validInputs() {
        Map<String, Object> m = new HashMap<>();
        m.put("permanentToken", "tok"); m.put("phoneNumberId", "123"); m.put("recipientPhone", "34612345678");
        m.put("mediaType", "document"); m.put("mediaUrl", "https://example.com/doc.pdf");
        return m;
    }

    @Test void should_send_media_when_valid() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.sendMediaMessage(any())).thenReturn(new SendMessageResult("wamid.m1", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
        assertThat(TestHelper.getOutputs(connector).get("messageId")).isEqualTo("wamid.m1");
    }

    @Test void should_fail_when_mediaType_invalid() {
        Map<String, Object> m = validInputs(); m.put("mediaType", "gif");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("Invalid mediaType");
    }

    @Test void should_fail_when_no_mediaUrl_nor_mediaId() {
        Map<String, Object> m = validInputs(); m.remove("mediaUrl");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters())
                .isInstanceOf(ConnectorValidationException.class).hasMessageContaining("mediaUrl or mediaId");
    }

    @Test void should_accept_mediaId_instead_of_url() throws Exception {
        Map<String, Object> m = validInputs(); m.remove("mediaUrl"); m.put("mediaId", "media-id-123");
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.sendMediaMessage(any())).thenReturn(new SendMessageResult("wamid.m2", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_fail_when_recipientPhone_missing() {
        Map<String, Object> m = validInputs(); m.remove("recipientPhone");
        connector.setInputParameters(m);
        assertThatThrownBy(() -> connector.validateInputParameters()).isInstanceOf(ConnectorValidationException.class);
    }

    @Test void should_set_caption_for_image() throws Exception {
        Map<String, Object> m = validInputs(); m.put("mediaType", "image"); m.put("caption", "Test caption");
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.sendMediaMessage(any())).thenReturn(new SendMessageResult("wamid.m3", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_set_filename_for_document() throws Exception {
        Map<String, Object> m = validInputs(); m.put("filename", "invoice.pdf");
        connector.setInputParameters(m); connector.validateInputParameters(); injectMockClient();
        when(mockClient.sendMediaMessage(any())).thenReturn(new SendMessageResult("wamid.m4", "34612345678"));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(true);
    }

    @Test void should_handle_api_error() throws Exception {
        connector.setInputParameters(validInputs()); connector.validateInputParameters(); injectMockClient();
        when(mockClient.sendMediaMessage(any())).thenThrow(new WhatsAppException("File too large", 400, false));
        connector.executeBusinessLogic();
        assertThat(TestHelper.getOutputs(connector).get("success")).isEqualTo(false);
        assertThat((String) TestHelper.getOutputs(connector).get("errorMessage")).contains("File too large");
    }
}
