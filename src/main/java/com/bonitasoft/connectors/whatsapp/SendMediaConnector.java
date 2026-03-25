package com.bonitasoft.connectors.whatsapp;

import java.util.Set;

public class SendMediaConnector extends AbstractWhatsAppConnector {

    static final String PHONE_NUMBER_ID = "phoneNumberId";
    static final String RECIPIENT_PHONE = "recipientPhone";
    static final String MEDIA_TYPE = "mediaType";
    static final String MEDIA_URL = "mediaUrl";
    static final String MEDIA_ID = "mediaId";
    static final String CAPTION = "caption";
    static final String FILENAME = "filename";
    private static final Set<String> VALID_MEDIA_TYPES = Set.of("image", "document", "audio", "video");

    @Override
    protected void doExecute() throws WhatsAppException {
        WhatsAppConfiguration cfg = buildConfiguration();
        SendMessageResult result = client.sendMediaMessage(cfg);
        setOutputParameter("messageId", result.messageId());
        setOutputParameter("recipientPhone", result.recipientPhone());
    }

    @Override
    protected WhatsAppConfiguration buildConfiguration() {
        String token = resolveToken();
        String phone = normalizePhone(readMandatoryStringInput(RECIPIENT_PHONE));
        String mediaType = readMandatoryStringInput(MEDIA_TYPE);
        if (!VALID_MEDIA_TYPES.contains(mediaType)) {
            throw new IllegalArgumentException("Invalid mediaType: " + mediaType + ". Must be one of: " + VALID_MEDIA_TYPES);
        }
        String mediaUrl = readStringInput(MEDIA_URL);
        String mediaId = readStringInput(MEDIA_ID);
        if ((mediaUrl == null || mediaUrl.isBlank()) && (mediaId == null || mediaId.isBlank())) {
            throw new IllegalArgumentException("Either mediaUrl or mediaId must be provided.");
        }
        return WhatsAppConfiguration.builder()
                .permanentToken(token)
                .baseUrl(readStringInput("baseUrl") != null ? readStringInput("baseUrl") : "https://graph.facebook.com/v23.0")
                .connectTimeout(readIntegerInput("connectTimeout", 30_000))
                .readTimeout(readIntegerInput("readTimeout", 60_000))
                .phoneNumberId(readMandatoryStringInput(PHONE_NUMBER_ID))
                .recipientPhone(phone)
                .mediaType(mediaType)
                .mediaUrl(mediaUrl)
                .mediaId(mediaId)
                .caption(readStringInput(CAPTION))
                .filename(readStringInput(FILENAME))
                .build();
    }
}
