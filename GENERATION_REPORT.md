# Generation Report — bonita-connector-whatsapp

| Field | Value |
|-------|-------|
| Connector | `bonita-connector-whatsapp` |
| Version | `1.0.0-beta.1` |
| Generated | 2026-03-25 |
| Operations | 6 |
| Tech Stack | OkHttp 4.12.0 + Jackson 2.17.2 |
| Java | 17 |
| Bonita Runtime | 10.2.0 |

## Operations

| # | Definition ID | Connector Class | Description |
|---|--------------|-----------------|-------------|
| 1 | `whatsapp-send-template` | `SendTemplateConnector` | Send a pre-approved template message |
| 2 | `whatsapp-send-text` | `SendTextConnector` | Send a free-form text message |
| 3 | `whatsapp-send-media` | `SendMediaConnector` | Send image, document, audio, or video |
| 4 | `whatsapp-get-status` | `GetStatusConnector` | Get delivery status of a message |
| 5 | `whatsapp-list-conversations` | `ListConversationsConnector` | Conversation analytics for a WABA |
| 6 | `whatsapp-get-messages` | `GetMessagesConnector` | Retrieve message history for a contact |

## Test Summary

| Test Type | Class | Test Count |
|-----------|-------|------------|
| Unit | `SendTemplateConnectorTest` | 10 |
| Unit | `SendTextConnectorTest` | — |
| Unit | `SendMediaConnectorTest` | — |
| Unit | `GetStatusConnectorTest` | — |
| Unit | `ListConversationsConnectorTest` | — |
| Unit | `GetMessagesConnectorTest` | — |
| Unit | `WhatsAppClientTest` | — |
| Unit | `RetryPolicyTest` | — |
| Property | `WhatsAppConnectorPropertyTest` | 13 (@Property) |
| Integration | `WhatsAppConnectorIT` | 3 (env-gated) |

## Coverage

- JaCoCo minimum: 85% line, 25% branch
- Coverage check enforced at `verify` phase

## Build Status

**GREEN** — `mvn clean verify` passes with all unit and property tests.

## Architecture

```
AbstractWhatsAppConnector (base lifecycle)
├── SendTemplateConnector
├── SendTextConnector
├── SendMediaConnector
├── GetStatusConnector
├── ListConversationsConnector
└── GetMessagesConnector

WhatsAppClient (OkHttp + Jackson, retry-aware)
RetryPolicy (exponential backoff, retryable HTTP/Meta codes)
WhatsAppConfiguration (Lombok @Builder)
WhatsAppException (status code + error code + retryable flag)
```

## Packaging

- `all-assembly.xml` — Single ZIP with all 6 operations
- Individual assembly per operation for selective deployment
