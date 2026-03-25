package com.bonitasoft.connectors.whatsapp;

public class SendTextConnector extends AbstractWhatsAppConnector {

    static final String PHONE_NUMBER_ID = "phoneNumberId";
    static final String RECIPIENT_PHONE = "recipientPhone";
    static final String MESSAGE_BODY = "messageBody";
    static final String PREVIEW_URL = "previewUrl";

    @Override
    protected void doExecute() throws WhatsAppException {
        WhatsAppConfiguration cfg = buildConfiguration();
        SendMessageResult result = client.sendTextMessage(cfg);
        setOutputParameter("messageId", result.messageId());
        setOutputParameter("recipientPhone", result.recipientPhone());
    }

    @Override
    protected WhatsAppConfiguration buildConfiguration() {
        String token = resolveToken();
        String phone = normalizePhone(readMandatoryStringInput(RECIPIENT_PHONE));
        String body = readMandatoryStringInput(MESSAGE_BODY);
        if (body.length() > 4096) {
            throw new IllegalArgumentException("Message body exceeds 4096 characters limit. Length: " + body.length());
        }
        return WhatsAppConfiguration.builder()
                .permanentToken(token)
                .baseUrl(readStringInput("baseUrl") != null ? readStringInput("baseUrl") : "https://graph.facebook.com/v23.0")
                .connectTimeout(readIntegerInput("connectTimeout", 30_000))
                .readTimeout(readIntegerInput("readTimeout", 60_000))
                .phoneNumberId(readMandatoryStringInput(PHONE_NUMBER_ID))
                .recipientPhone(phone)
                .messageBody(body)
                .previewUrl(readBooleanInput(PREVIEW_URL, false))
                .build();
    }
}
