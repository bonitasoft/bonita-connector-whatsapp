package com.bonitasoft.connectors.whatsapp;

import lombok.extern.slf4j.Slf4j;
import org.bonitasoft.engine.connector.AbstractConnector;
import org.bonitasoft.engine.connector.ConnectorException;
import org.bonitasoft.engine.connector.ConnectorValidationException;

@Slf4j
public abstract class AbstractWhatsAppConnector extends AbstractConnector {

    protected WhatsAppClient client;

    @Override
    public void validateInputParameters() throws ConnectorValidationException {
        try {
            buildConfiguration();
        } catch (IllegalArgumentException e) {
            throw new ConnectorValidationException(e.getMessage());
        }
    }

    @Override
    public void connect() throws ConnectorException {
        WhatsAppConfiguration config = buildConfiguration();
        client = new WhatsAppClient(config, new RetryPolicy());
    }

    @Override
    public final void executeBusinessLogic() throws ConnectorException {
        try {
            doExecute();
            setOutputParameter("success", true);
            setOutputParameter("errorMessage", "");
        } catch (WhatsAppException e) {
            log.error("WhatsApp API error: {}", e.getMessage());
            setOutputParameter("success", false);
            setOutputParameter("errorMessage", truncate(e.getMessage()));
        } catch (Exception e) {
            log.error("Unexpected error: {}", e.getMessage(), e);
            setOutputParameter("success", false);
            setOutputParameter("errorMessage", truncate("Unexpected error: " + e.getMessage()));
        }
    }

    @Override
    public void disconnect() throws ConnectorException {
        // OkHttp client does not require explicit close
    }

    protected abstract void doExecute() throws WhatsAppException;

    protected abstract WhatsAppConfiguration buildConfiguration();

    protected String readStringInput(String name) {
        Object val = getInputParameter(name);
        return val != null ? val.toString() : null;
    }

    protected String readMandatoryStringInput(String name) {
        String val = readStringInput(name);
        if (val == null || val.isBlank()) {
            throw new IllegalArgumentException("Parameter '" + name + "' is mandatory and cannot be blank.");
        }
        return val;
    }

    protected Integer readIntegerInput(String name, int defaultValue) {
        Object val = getInputParameter(name);
        if (val == null) return defaultValue;
        if (val instanceof Integer i) return i;
        try {
            return Integer.parseInt(val.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    protected Boolean readBooleanInput(String name, boolean defaultValue) {
        Object val = getInputParameter(name);
        if (val == null) return defaultValue;
        if (val instanceof Boolean b) return b;
        return Boolean.parseBoolean(val.toString());
    }

    protected String resolveToken() {
        String token = readStringInput("permanentToken");
        if (token != null && !token.isBlank()) return token;
        String sysProp = System.getProperty("whatsapp.token");
        if (sysProp != null && !sysProp.isBlank()) return sysProp;
        String env = System.getenv("WHATSAPP_TOKEN");
        if (env != null && !env.isBlank()) return env;
        throw new IllegalArgumentException("WhatsApp token not found. Set permanentToken input, JVM property 'whatsapp.token', or env var 'WHATSAPP_TOKEN'.");
    }

    protected String normalizePhone(String phone) {
        if (phone == null) return null;
        return phone.replaceAll("[+\\s\\-]", "");
    }

    private String truncate(String msg) {
        return msg != null && msg.length() > 1000 ? msg.substring(0, 1000) : msg;
    }
}
