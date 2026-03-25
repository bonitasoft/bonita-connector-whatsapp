package com.bonitasoft.connectors.whatsapp;

public record MessageListResult(String messages, int messageCount, boolean hasMore, String nextCursor) {}
