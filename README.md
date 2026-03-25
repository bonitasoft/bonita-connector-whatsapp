# Bonita WhatsApp Business Connector

Official Bonita BPM connector for the [WhatsApp Business Cloud API](https://developers.facebook.com/docs/whatsapp/cloud-api). Enables outbound messaging, delivery tracking, and conversation analytics directly from Bonita processes.

**Version:** 1.0.0-beta.1
**License:** [GPL-2.0](LICENSE)

## Operations

| # | Operation | Definition ID | Description |
|---|-----------|--------------|-------------|
| 1 | Send Template Message | `whatsapp-send-template` | Send a pre-approved template message (marketing, utility, authentication) |
| 2 | Send Text Message | `whatsapp-send-text` | Send a free-form text message (requires 24h conversation window) |
| 3 | Send Media Message | `whatsapp-send-media` | Send image, document, audio, or video via URL or media ID |
| 4 | Get Message Status | `whatsapp-get-status` | Retrieve delivery status of a previously sent message |
| 5 | List Conversations | `whatsapp-list-conversations` | Get conversation analytics for a WhatsApp Business Account |
| 6 | Get Messages | `whatsapp-get-messages` | Retrieve message history for a specific contact |

## Prerequisites

1. **Meta Business Manager** account with a verified business
2. **WhatsApp Business Account (WABA)** linked to Meta Business Manager
3. **System User Token** — permanent token with `whatsapp_business_messaging` and `whatsapp_business_management` permissions
4. **Registered Phone Number** — a phone number added to your WhatsApp Business Account
5. **Approved Templates** — at least one message template approved by Meta (for template operations)

## Installation

1. Build the connector (see [Build](#build) section)
2. In Bonita Studio, go to **Development > Connector definitions > Import...**
3. Import the desired ZIP from `target/`:
   - `bonita-connector-whatsapp-1.0.0-beta.1-all.zip` — all 6 operations
   - Or individual ZIPs per operation (e.g., `...-send-template.zip`)

## Configuration

### Common Inputs (all operations)

| Input | Type | Required | Description |
|-------|------|----------|-------------|
| `permanentToken` | String | Yes* | System User permanent token |
| `baseUrl` | String | No | API base URL (default: `https://graph.facebook.com/v23.0`) |
| `connectTimeout` | Integer | No | Connection timeout in ms (default: 30000) |
| `readTimeout` | Integer | No | Read timeout in ms (default: 60000) |

*Token resolution order: `permanentToken` input > `whatsapp.token` system property > `WHATSAPP_TOKEN` environment variable.

### Send Template Message

| Input | Type | Required | Description |
|-------|------|----------|-------------|
| `phoneNumberId` | String | Yes | WhatsApp Phone Number ID |
| `recipientPhone` | String | Yes | Recipient phone with country code (e.g., `34612345678`) |
| `templateName` | String | Yes | Approved template name |
| `templateLanguage` | String | No | Template language code (default: `es`) |
| `templateParameters` | String | No | JSON array of parameter values (e.g., `["ORD-123","Madrid"]`) |

### Send Text Message

| Input | Type | Required | Description |
|-------|------|----------|-------------|
| `phoneNumberId` | String | Yes | WhatsApp Phone Number ID |
| `recipientPhone` | String | Yes | Recipient phone with country code |
| `messageBody` | String | Yes | Text message body (max 4096 characters) |
| `previewUrl` | Boolean | No | Enable URL preview (default: `false`) |

### Send Media Message

| Input | Type | Required | Description |
|-------|------|----------|-------------|
| `phoneNumberId` | String | Yes | WhatsApp Phone Number ID |
| `recipientPhone` | String | Yes | Recipient phone with country code |
| `mediaType` | String | Yes | One of: `image`, `document`, `audio`, `video` |
| `mediaUrl` | String | Conditional | Public URL of the media file |
| `mediaId` | String | Conditional | Meta media ID (alternative to URL) |
| `caption` | String | No | Caption (not supported for `audio`) |
| `filename` | String | No | Filename (only for `document`) |

### Get Message Status

| Input | Type | Required | Description |
|-------|------|----------|-------------|
| `messageId` | String | Yes | WhatsApp message ID (e.g., `wamid.HBgL...`) |

### List Conversations

| Input | Type | Required | Description |
|-------|------|----------|-------------|
| `wabaId` | String | Yes | WhatsApp Business Account ID |
| `startDate` | String | Yes | Start date (ISO 8601 or UNIX timestamp) |
| `endDate` | String | Yes | End date (ISO 8601 or UNIX timestamp, max 90 days range) |
| `granularity` | String | No | `DAILY` or `HALF_HOUR` (default: `DAILY`) |
| `conversationType` | String | No | Filter by conversation type |
| `direction` | String | No | Filter by direction |
| `limit` | Integer | No | Max results (default: 500) |
| `cursor` | String | No | Pagination cursor |

### Get Messages

| Input | Type | Required | Description |
|-------|------|----------|-------------|
| `phoneNumberId` | String | Yes | WhatsApp Phone Number ID |
| `contactPhone` | String | Yes | Contact phone number |
| `limit` | Integer | No | Max results (default: 20) |
| `cursor` | String | No | Pagination cursor |

### Common Outputs (all operations)

| Output | Type | Description |
|--------|------|-------------|
| `success` | Boolean | `true` if the operation completed successfully |
| `errorMessage` | String | Error details (empty on success) |

Additional outputs vary per operation (e.g., `messageId`, `recipientPhone`, `status`, `conversations`, etc.).

## Build

```bash
# Full build with tests and coverage check
./mvnw clean verify

# Build without tests
./mvnw clean package -DskipTests

# Run unit tests only
./mvnw test

# Run integration tests (requires env vars)
export WHATSAPP_TOKEN="your-token"
export WHATSAPP_PHONE_NUMBER_ID="your-phone-id"
export WHATSAPP_TEST_RECIPIENT="34612345678"
./mvnw verify
```

### Requirements

- Java 17+
- Maven 3.8+ (or use included Maven Wrapper)
- Bonita Runtime 10.2.0+

## Technical Details

- **HTTP Client:** OkHttp 4.12.0
- **JSON Processing:** Jackson 2.17.2
- **Retry Policy:** Exponential backoff with jitter for HTTP 429/5xx and Meta error codes 130429, 131056, 4
- **Phone Normalization:** Automatic removal of `+`, spaces, and dashes
- **Coverage:** JaCoCo enforced at 85% line / 25% branch minimum

## License

This project is licensed under the [GNU General Public License v2.0](LICENSE).
