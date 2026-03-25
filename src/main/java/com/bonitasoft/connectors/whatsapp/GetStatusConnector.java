package com.bonitasoft.connectors.whatsapp;

public class GetStatusConnector extends AbstractWhatsAppConnector {

    static final String MESSAGE_ID = "messageId";

    @Override
    protected void doExecute() throws WhatsAppException {
        WhatsAppConfiguration cfg = buildConfiguration();
        MessageStatusResult result = client.getMessageStatus(cfg);
        setOutputParameter("status", result.status());
        setOutputParameter("timestamp", result.timestamp());
        setOutputParameter("errorCode", result.errorCode());
    }

    @Override
    protected WhatsAppConfiguration buildConfiguration() {
        String token = resolveToken();
        return WhatsAppConfiguration.builder()
                .permanentToken(token)
                .baseUrl(readStringInput("baseUrl") != null ? readStringInput("baseUrl") : "https://graph.facebook.com/v23.0")
                .connectTimeout(readIntegerInput("connectTimeout", 30_000))
                .readTimeout(readIntegerInput("readTimeout", 60_000))
                .messageId(readMandatoryStringInput(MESSAGE_ID))
                .build();
    }
}
