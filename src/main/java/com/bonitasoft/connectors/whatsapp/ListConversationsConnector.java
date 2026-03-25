package com.bonitasoft.connectors.whatsapp;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class ListConversationsConnector extends AbstractWhatsAppConnector {

    static final String WABA_ID = "wabaId";
    static final String START_DATE = "startDate";
    static final String END_DATE = "endDate";
    static final String GRANULARITY = "granularity";
    static final String CONVERSATION_TYPE = "conversationType";
    static final String DIRECTION = "direction";
    static final String LIMIT = "limit";
    static final String CURSOR = "cursor";

    @Override
    protected void doExecute() throws WhatsAppException {
        WhatsAppConfiguration cfg = buildConfiguration();
        ConversationAnalyticsResult result = client.listConversations(cfg);
        setOutputParameter("conversations", result.conversations());
        setOutputParameter("totalCount", result.totalCount());
        setOutputParameter("hasMore", result.hasMore());
        setOutputParameter("nextCursor", result.nextCursor());
    }

    @Override
    protected WhatsAppConfiguration buildConfiguration() {
        String token = resolveToken();
        String startDate = readMandatoryStringInput(START_DATE);
        String endDate = readMandatoryStringInput(END_DATE);
        validateDateRange(startDate, endDate);
        return WhatsAppConfiguration.builder()
                .permanentToken(token)
                .baseUrl(readStringInput("baseUrl") != null ? readStringInput("baseUrl") : "https://graph.facebook.com/v23.0")
                .connectTimeout(readIntegerInput("connectTimeout", 30_000))
                .readTimeout(readIntegerInput("readTimeout", 60_000))
                .wabaId(readMandatoryStringInput(WABA_ID))
                .startDate(startDate)
                .endDate(endDate)
                .granularity(readStringInput(GRANULARITY) != null ? readStringInput(GRANULARITY) : "DAILY")
                .conversationType(readStringInput(CONVERSATION_TYPE))
                .direction(readStringInput(DIRECTION))
                .limit(readIntegerInput(LIMIT, 500))
                .cursor(readStringInput(CURSOR))
                .build();
    }

    private void validateDateRange(String start, String end) {
        try {
            Long.parseLong(start);
            return; // Unix timestamps — skip validation
        } catch (NumberFormatException ignored) {}
        try {
            LocalDate startDate = LocalDate.parse(start);
            LocalDate endDate = LocalDate.parse(end);
            if (ChronoUnit.DAYS.between(startDate, endDate) > 90) {
                throw new IllegalArgumentException("Date range exceeds 90-day maximum. Split into multiple calls.");
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid date format. Use ISO 8601 (YYYY-MM-DD) or UNIX timestamp.");
        }
    }
}
