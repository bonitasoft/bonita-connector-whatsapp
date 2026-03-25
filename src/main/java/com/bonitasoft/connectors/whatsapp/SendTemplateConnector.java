package com.bonitasoft.connectors.whatsapp;

public class SendTemplateConnector extends AbstractWhatsAppConnector {

    static final String PHONE_NUMBER_ID = "phoneNumberId";
    static final String RECIPIENT_PHONE = "recipientPhone";
    static final String TEMPLATE_NAME = "templateName";
    static final String TEMPLATE_LANGUAGE = "templateLanguage";
    static final String TEMPLATE_PARAMETERS = "templateParameters";
    static final String OUT_MESSAGE_ID = "messageId";
    static final String OUT_RECIPIENT_PHONE = "recipientPhone";

    @Override
    protected void doExecute() throws WhatsAppException {
        WhatsAppConfiguration cfg = buildConfiguration();
        SendMessageResult result = client.sendTemplateMessage(cfg);
        setOutputParameter(OUT_MESSAGE_ID, result.messageId());
        setOutputParameter(OUT_RECIPIENT_PHONE, result.recipientPhone());
    }

    @Override
    protected WhatsAppConfiguration buildConfiguration() {
        String token = resolveToken();
        String phone = normalizePhone(readMandatoryStringInput(RECIPIENT_PHONE));
        validatePhone(phone);
        return WhatsAppConfiguration.builder()
                .permanentToken(token)
                .baseUrl(readStringInput("baseUrl") != null ? readStringInput("baseUrl") : "https://graph.facebook.com/v23.0")
                .connectTimeout(readIntegerInput("connectTimeout", 30_000))
                .readTimeout(readIntegerInput("readTimeout", 60_000))
                .phoneNumberId(readMandatoryStringInput(PHONE_NUMBER_ID))
                .recipientPhone(phone)
                .templateName(readMandatoryStringInput(TEMPLATE_NAME))
                .templateLanguage(readStringInput(TEMPLATE_LANGUAGE) != null ? readStringInput(TEMPLATE_LANGUAGE) : "es")
                .templateParameters(readStringInput(TEMPLATE_PARAMETERS))
                .build();
    }

    private void validatePhone(String phone) {
        if (!phone.matches("\\d{7,15}")) {
            throw new IllegalArgumentException("Recipient phone must be 7-15 digits after normalization. Got: " + phone);
        }
    }
}
