# Beta Status — bonita-connector-whatsapp 1.0.0-beta.1

| Operation | Definition ID | Status | Tested By | Tested Date |
|-----------|--------------|--------|-----------|-------------|
| Send Template Message | `whatsapp-send-template` | Untested | — | — |
| Send Text Message | `whatsapp-send-text` | Untested | — | — |
| Send Media Message | `whatsapp-send-media` | Untested | — | — |
| Get Message Status | `whatsapp-get-status` | Untested | — | — |
| List Conversations | `whatsapp-list-conversations` | Untested | — | — |
| Get Messages | `whatsapp-get-messages` | Untested | — | — |

## How to test

1. Set environment variables:
   ```bash
   export WHATSAPP_TOKEN="your-system-user-token"
   export WHATSAPP_PHONE_NUMBER_ID="your-phone-number-id"
   export WHATSAPP_TEST_RECIPIENT="34612345678"
   ```

2. Run integration tests:
   ```bash
   ./mvnw verify -DskipTests=false
   ```

3. Update this table and `beta-status.json` after each validated operation.
