package com.bonitasoft.connectors.whatsapp;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WhatsAppConfiguration {

    private String permanentToken;
    @Builder.Default
    private String baseUrl = "https://graph.facebook.com/v23.0";
    @Builder.Default
    private int connectTimeout = 30_000;
    @Builder.Default
    private int readTimeout = 60_000;

    private String phoneNumberId;
    private String wabaId;
    private String recipientPhone;
    private String templateName;
    @Builder.Default
    private String templateLanguage = "es";
    private String templateParameters;

    private String messageBody;
    @Builder.Default
    private boolean previewUrl = false;

    private String mediaType;
    private String mediaUrl;
    private String mediaId;
    private String caption;
    private String filename;

    private String messageId;

    private String contactPhone;
    private String startDate;
    private String endDate;
    @Builder.Default
    private String granularity = "DAILY";
    private String conversationType;
    private String direction;
    @Builder.Default
    private int limit = 500;
    private String cursor;
}
