package com.bonitasoft.connectors.whatsapp;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
public class WhatsAppClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper;
    private final WhatsAppConfiguration config;
    private final RetryPolicy retryPolicy;

    public WhatsAppClient(WhatsAppConfiguration config, RetryPolicy retryPolicy) {
        this.config = config;
        this.retryPolicy = retryPolicy;
        this.mapper = new ObjectMapper();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getConnectTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getReadTimeout(), TimeUnit.MILLISECONDS)
                .addInterceptor(chain -> chain.proceed(
                        chain.request().newBuilder()
                                .header("Authorization", "Bearer " + config.getPermanentToken())
                                .build()))
                .build();
    }

    // Visible for testing
    WhatsAppClient(OkHttpClient httpClient, ObjectMapper mapper,
                   WhatsAppConfiguration config, RetryPolicy retryPolicy) {
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.config = config;
        this.retryPolicy = retryPolicy;
    }

    public SendMessageResult sendTemplateMessage(WhatsAppConfiguration cfg) throws WhatsAppException {
        return retryPolicy.execute(() -> {
            ObjectNode body = mapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("to", cfg.getRecipientPhone());
            body.put("type", "template");

            ObjectNode template = body.putObject("template");
            template.put("name", cfg.getTemplateName());
            template.putObject("language").put("code", cfg.getTemplateLanguage());

            if (cfg.getTemplateParameters() != null && !cfg.getTemplateParameters().isBlank()) {
                ArrayNode params = (ArrayNode) mapper.readTree(cfg.getTemplateParameters());
                ObjectNode bodyComponent = mapper.createObjectNode().put("type", "body");
                ArrayNode parameters = bodyComponent.putArray("parameters");
                for (JsonNode param : params) {
                    parameters.addObject().put("type", "text").put("text", param.asText());
                }
                template.putArray("components").add(bodyComponent);
            }

            String url = cfg.getBaseUrl() + "/" + cfg.getPhoneNumberId() + "/messages";
            return executeSendRequest(url, body);
        });
    }

    public SendMessageResult sendTextMessage(WhatsAppConfiguration cfg) throws WhatsAppException {
        return retryPolicy.execute(() -> {
            ObjectNode body = mapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("to", cfg.getRecipientPhone());
            body.put("type", "text");
            body.putObject("text")
                    .put("body", cfg.getMessageBody())
                    .put("preview_url", cfg.isPreviewUrl());

            String url = cfg.getBaseUrl() + "/" + cfg.getPhoneNumberId() + "/messages";
            return executeSendRequest(url, body);
        });
    }

    public SendMessageResult sendMediaMessage(WhatsAppConfiguration cfg) throws WhatsAppException {
        return retryPolicy.execute(() -> {
            ObjectNode body = mapper.createObjectNode();
            body.put("messaging_product", "whatsapp");
            body.put("to", cfg.getRecipientPhone());
            body.put("type", cfg.getMediaType());

            ObjectNode mediaNode = body.putObject(cfg.getMediaType());
            if (cfg.getMediaUrl() != null && !cfg.getMediaUrl().isBlank()) {
                mediaNode.put("link", cfg.getMediaUrl());
            } else if (cfg.getMediaId() != null && !cfg.getMediaId().isBlank()) {
                mediaNode.put("id", cfg.getMediaId());
            }
            if (cfg.getCaption() != null && !cfg.getCaption().isBlank()
                    && !"audio".equals(cfg.getMediaType())) {
                mediaNode.put("caption", cfg.getCaption());
            }
            if (cfg.getFilename() != null && !cfg.getFilename().isBlank()
                    && "document".equals(cfg.getMediaType())) {
                mediaNode.put("filename", cfg.getFilename());
            }

            String url = cfg.getBaseUrl() + "/" + cfg.getPhoneNumberId() + "/messages";
            return executeSendRequest(url, body);
        });
    }

    public MessageStatusResult getMessageStatus(WhatsAppConfiguration cfg) throws WhatsAppException {
        return retryPolicy.execute(() -> {
            String url = cfg.getBaseUrl() + "/" + cfg.getMessageId();
            Request request = new Request.Builder().url(url).get().build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    handleErrorResponse(response.code(), responseBody);
                }
                JsonNode json = mapper.readTree(responseBody);
                return new MessageStatusResult(
                        json.path("status").asText("unknown"),
                        json.path("timestamp").asText(""),
                        json.path("error_code").asText("")
                );
            }
        });
    }

    public ConversationAnalyticsResult listConversations(WhatsAppConfiguration cfg) throws WhatsAppException {
        return retryPolicy.execute(() -> {
            StringBuilder url = new StringBuilder(cfg.getBaseUrl())
                    .append("/").append(cfg.getWabaId())
                    .append("/conversation_analytics?start=")
                    .append(toUnixTimestamp(cfg.getStartDate()))
                    .append("&end=").append(toUnixTimestamp(cfg.getEndDate()))
                    .append("&granularity=").append(cfg.getGranularity());
            if (cfg.getConversationType() != null && !cfg.getConversationType().isBlank()) {
                url.append("&conversation_type=").append(cfg.getConversationType());
            }
            if (cfg.getDirection() != null && !cfg.getDirection().isBlank()) {
                url.append("&conversation_direction=").append(cfg.getDirection());
            }
            url.append("&limit=").append(cfg.getLimit());
            if (cfg.getCursor() != null && !cfg.getCursor().isBlank()) {
                url.append("&after=").append(cfg.getCursor());
            }

            Request request = new Request.Builder().url(url.toString()).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    handleErrorResponse(response.code(), responseBody);
                }
                JsonNode json = mapper.readTree(responseBody);
                JsonNode data = json.path("conversation_analytics").path("data");
                String conversations = mapper.writeValueAsString(data);
                int totalCount = data.size();
                JsonNode paging = json.path("paging");
                boolean hasMore = paging.has("next");
                String nextCursor = paging.path("cursors").path("after").asText("");
                return new ConversationAnalyticsResult(conversations, totalCount, hasMore, nextCursor);
            }
        });
    }

    public MessageListResult getMessages(WhatsAppConfiguration cfg) throws WhatsAppException {
        return retryPolicy.execute(() -> {
            StringBuilder url = new StringBuilder(cfg.getBaseUrl())
                    .append("/").append(cfg.getPhoneNumberId())
                    .append("/messages?contact_phone=").append(cfg.getContactPhone())
                    .append("&limit=").append(cfg.getLimit());
            if (cfg.getCursor() != null && !cfg.getCursor().isBlank()) {
                url.append("&after=").append(cfg.getCursor());
            }

            Request request = new Request.Builder().url(url.toString()).get().build();
            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    handleErrorResponse(response.code(), responseBody);
                }
                JsonNode json = mapper.readTree(responseBody);
                JsonNode data = json.path("data");
                String messages = mapper.writeValueAsString(data);
                int messageCount = data.size();
                JsonNode paging = json.path("paging");
                boolean hasMore = paging.has("next");
                String nextCursor = paging.path("cursors").path("after").asText("");
                return new MessageListResult(messages, messageCount, hasMore, nextCursor);
            }
        });
    }

    private SendMessageResult executeSendRequest(String url, ObjectNode body) throws WhatsAppException {
        RequestBody requestBody;
        try {
            requestBody = RequestBody.create(mapper.writeValueAsBytes(body), JSON);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new WhatsAppException("Failed to serialize request: " + e.getMessage(), e);
        }
        Request request = new Request.Builder().url(url).post(requestBody).build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                handleErrorResponse(response.code(), responseBody);
            }
            JsonNode json = mapper.readTree(responseBody);
            String messageId = json.path("messages").path(0).path("id").asText("");
            String recipientPhone = json.path("contacts").path(0).path("wa_id").asText("");
            return new SendMessageResult(messageId, recipientPhone);
        } catch (WhatsAppException e) {
            throw e;
        } catch (IOException e) {
            throw new WhatsAppException("Network error: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new WhatsAppException("Unexpected error: " + e.getMessage(), e);
        }
    }

    private void handleErrorResponse(int httpCode, String responseBody) throws WhatsAppException {
        try {
            JsonNode json = mapper.readTree(responseBody);
            JsonNode error = json.path("error");
            int metaCode = error.path("code").asInt(0);
            String message = error.path("message").asText("Unknown error");
            boolean retryable = isRetryableError(httpCode, metaCode);
            throw new WhatsAppException(
                    String.format("Meta API error %d [%d]: %s", httpCode, metaCode, message),
                    httpCode, metaCode, retryable);
        } catch (WhatsAppException e) {
            throw e;
        } catch (Exception e) {
            throw new WhatsAppException("HTTP " + httpCode + ": " + responseBody, httpCode, false);
        }
    }

    private boolean isRetryableError(int httpCode, int metaCode) {
        if (httpCode >= 500) return true;
        if (httpCode == 429) return true;
        return metaCode == 130429 || metaCode == 131056 || metaCode == 4;
    }

    long toUnixTimestamp(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            throw new WhatsAppException("Date parameter is required", 0, false);
        }
        try {
            return Long.parseLong(dateStr);
        } catch (NumberFormatException e) {
            return LocalDate.parse(dateStr).atStartOfDay(ZoneOffset.UTC).toEpochSecond();
        }
    }
}
