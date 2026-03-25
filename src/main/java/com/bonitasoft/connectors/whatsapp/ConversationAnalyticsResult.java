package com.bonitasoft.connectors.whatsapp;

public record ConversationAnalyticsResult(String conversations, int totalCount, boolean hasMore, String nextCursor) {}
