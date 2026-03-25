package com.bonitasoft.connectors.whatsapp;

public class GetMessagesConnector extends AbstractWhatsAppConnector {

    static final String PHONE_NUMBER_ID = "phoneNumberId";
    static final String CONTACT_PHONE = "contactPhone";
    static final String LIMIT = "limit";
    static final String CURSOR = "cursor";

    @Override
    protected void doExecute() throws WhatsAppException {
        WhatsAppConfiguration cfg = buildConfiguration();
        MessageListResult result = client.getMessages(cfg);
        setOutputParameter("messages", result.messages());
        setOutputParameter("messageCount", result.messageCount());
        setOutputParameter("hasMore", result.hasMore());
        setOutputParameter("nextCursor", result.nextCursor());
    }

    @Override
    protected WhatsAppConfiguration buildConfiguration() {
        String token = resolveToken();
        String contactPhone = normalizePhone(readMandatoryStringInput(CONTACT_PHONE));
        return WhatsAppConfiguration.builder()
                .permanentToken(token)
                .baseUrl(readStringInput("baseUrl") != null ? readStringInput("baseUrl") : "https://graph.facebook.com/v23.0")
                .connectTimeout(readIntegerInput("connectTimeout", 30_000))
                .readTimeout(readIntegerInput("readTimeout", 60_000))
                .phoneNumberId(readMandatoryStringInput(PHONE_NUMBER_ID))
                .contactPhone(contactPhone)
                .limit(readIntegerInput(LIMIT, 20))
                .cursor(readStringInput(CURSOR))
                .build();
    }
}
