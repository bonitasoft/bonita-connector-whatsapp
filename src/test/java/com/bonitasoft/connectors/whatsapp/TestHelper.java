package com.bonitasoft.connectors.whatsapp;

import java.util.Map;

import org.bonitasoft.engine.connector.AbstractConnector;

final class TestHelper {

    private TestHelper() {}

    @SuppressWarnings("unchecked")
    static Map<String, Object> getOutputs(AbstractConnector connector) {
        try {
            var field = AbstractConnector.class.getDeclaredField("outputParameters");
            field.setAccessible(true);
            return (Map<String, Object>) field.get(connector);
        } catch (Exception e) {
            throw new RuntimeException("Cannot access outputParameters", e);
        }
    }
}
