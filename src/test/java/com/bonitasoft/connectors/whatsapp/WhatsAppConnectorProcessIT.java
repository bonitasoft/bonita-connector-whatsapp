package com.bonitasoft.connectors.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import org.bonitasoft.web.client.BonitaClient;
import org.bonitasoft.web.client.api.ArchivedProcessInstanceApi;
import org.bonitasoft.web.client.api.ProcessInstanceApi;
import org.bonitasoft.web.client.exception.NotFoundException;
import org.bonitasoft.web.client.model.ArchivedProcessInstance;
import org.bonitasoft.web.client.services.policies.OrganizationImportPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Process-based integration tests for WhatsApp connectors.
 *
 * These tests build a Bonita process containing the connector, deploy it
 * to a Docker Bonita instance, and verify the connector executes correctly
 * within the process engine.
 *
 * Requires:
 * - Docker running
 * - WHATSAPP_TOKEN environment variable set
 * - WHATSAPP_PHONE_NUMBER_ID environment variable set
 * - WHATSAPP_RECIPIENT_PHONE environment variable set (for send operations)
 * - WHATSAPP_WABA_ID environment variable set (for list-conversations)
 * - WHATSAPP_TEST_MESSAGE_ID environment variable set (for get-status)
 * - Project built with mvn package (JAR must exist in target/)
 */
@Testcontainers
@EnabledIfEnvironmentVariable(named = "WHATSAPP_TOKEN", matches = ".+")
class WhatsAppConnectorProcessIT {

    private static final Logger LOGGER = LoggerFactory.getLogger(WhatsAppConnectorProcessIT.class);

    // Connector definition IDs and versions (must match pom.xml properties)
    private static final String SEND_TEMPLATE_DEF_ID = "whatsapp-send-template";
    private static final String SEND_TEMPLATE_DEF_VERSION = "1.0.0";

    private static final String SEND_TEXT_DEF_ID = "whatsapp-send-text";
    private static final String SEND_TEXT_DEF_VERSION = "1.0.0";

    private static final String SEND_MEDIA_DEF_ID = "whatsapp-send-media";
    private static final String SEND_MEDIA_DEF_VERSION = "1.0.0";

    private static final String GET_STATUS_DEF_ID = "whatsapp-get-status";
    private static final String GET_STATUS_DEF_VERSION = "1.0.0";

    private static final String LIST_CONVERSATIONS_DEF_ID = "whatsapp-list-conversations";
    private static final String LIST_CONVERSATIONS_DEF_VERSION = "1.0.0";

    private static final String GET_MESSAGES_DEF_ID = "whatsapp-get-messages";
    private static final String GET_MESSAGES_DEF_VERSION = "1.0.0";

    @Container
    static GenericContainer<?> BONITA_CONTAINER = new GenericContainer<>(
            DockerImageName.parse("bonita:10.2.0"))
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/bonita"))
            .withLogConsumer(new Slf4jLogConsumer(LOGGER));

    private BonitaClient client;

    @BeforeAll
    static void installOrganization() {
        var client = BonitaClient
                .builder(String.format("http://%s:%s/bonita",
                        BONITA_CONTAINER.getHost(),
                        BONITA_CONTAINER.getFirstMappedPort()))
                .build();
        client.login("install", "install");
        client.users().importOrganization(
                new File(WhatsAppConnectorProcessIT.class.getResource("/ACME.xml").getFile()),
                OrganizationImportPolicy.IGNORE_DUPLICATES);
        client.logout();
    }

    @BeforeEach
    void login() {
        client = BonitaClient
                .builder(String.format("http://%s:%s/bonita",
                        BONITA_CONTAINER.getHost(),
                        BONITA_CONTAINER.getFirstMappedPort()))
                .build();
        client.login("install", "install");
    }

    @AfterEach
    void logout() {
        client.logout();
    }

    @Test
    void testSendTemplateConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("recipientPhone", System.getenv("WHATSAPP_RECIPIENT_PHONE"));
        inputs.put("templateName", "hello_world");
        inputs.put("templateLanguage", "en_US");

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultMessageId", ConnectorTestToolkit.Output.create("messageId", String.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                SEND_TEMPLATE_DEF_ID, SEND_TEMPLATE_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");

        var messageId = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultMessageId");
        assertThat(messageId).isNotEmpty();
    }

    @Test
    void testSendTextConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("recipientPhone", System.getenv("WHATSAPP_RECIPIENT_PHONE"));
        inputs.put("messageBody", "Integration test message - " + System.currentTimeMillis());

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultMessageId", ConnectorTestToolkit.Output.create("messageId", String.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                SEND_TEXT_DEF_ID, SEND_TEXT_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");

        var messageId = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultMessageId");
        assertThat(messageId).isNotEmpty();
    }

    @Test
    void testSendMediaConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("recipientPhone", System.getenv("WHATSAPP_RECIPIENT_PHONE"));
        inputs.put("mediaUrl", "https://www.w3.org/WAI/WCAG21/Techniques/pdf/img/table-word.jpg");
        inputs.put("mediaType", "image");

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultMessageId", ConnectorTestToolkit.Output.create("messageId", String.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                SEND_MEDIA_DEF_ID, SEND_MEDIA_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");

        var messageId = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultMessageId");
        assertThat(messageId).isNotEmpty();
    }

    @Test
    void testGetStatusConnector() throws Exception {
        var inputs = new HashMap<String, String>();
        inputs.put("permanentToken", System.getenv("WHATSAPP_TOKEN"));
        inputs.put("messageId", System.getenv("WHATSAPP_TEST_MESSAGE_ID"));

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultStatus", ConnectorTestToolkit.Output.create("status", String.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                GET_STATUS_DEF_ID, GET_STATUS_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");
    }

    @Test
    void testListConversationsConnector() throws Exception {
        var inputs = new HashMap<String, String>();
        inputs.put("permanentToken", System.getenv("WHATSAPP_TOKEN"));
        inputs.put("wabaId", System.getenv("WHATSAPP_WABA_ID"));
        inputs.put("startDate", "1704067200");
        inputs.put("endDate", "1706745600");
        inputs.put("granularity", "DAILY");

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultTotalCount", ConnectorTestToolkit.Output.create("totalCount", Integer.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                LIST_CONVERSATIONS_DEF_ID, LIST_CONVERSATIONS_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");
    }

    @Test
    void testGetMessagesConnector() throws Exception {
        var inputs = commonInputs();
        inputs.put("contactPhone", System.getenv("WHATSAPP_RECIPIENT_PHONE"));

        var outputs = Map.of(
                "resultSuccess", ConnectorTestToolkit.Output.create("success", Boolean.class.getName()),
                "resultMessageCount", ConnectorTestToolkit.Output.create("messageCount", Integer.class.getName()));

        var barFile = ConnectorTestToolkit.buildConnectorToTest(
                GET_MESSAGES_DEF_ID, GET_MESSAGES_DEF_VERSION, inputs, outputs);
        var processResponse = ConnectorTestToolkit.importAndLaunchProcess(barFile, client);

        await().until(pollInstanceState(processResponse.getCaseId()), "started"::equals);

        var success = ConnectorTestToolkit.getProcessVariableValue(client,
                processResponse.getCaseId(), "resultSuccess");
        assertThat(success).isEqualTo("true");
    }

    private Map<String, String> commonInputs() {
        var inputs = new HashMap<String, String>();
        inputs.put("permanentToken", System.getenv("WHATSAPP_TOKEN"));
        inputs.put("phoneNumberId", System.getenv("WHATSAPP_PHONE_NUMBER_ID"));
        return inputs;
    }

    private Callable<String> pollInstanceState(String id) {
        return () -> {
            try {
                var instance = client.get(ProcessInstanceApi.class)
                        .getProcessInstanceById(id, (String) null);
                return instance.getState().name().toLowerCase();
            } catch (NotFoundException e) {
                return getCompletedProcess(id).getState().name().toLowerCase();
            }
        };
    }

    private ArchivedProcessInstance getCompletedProcess(String id) {
        var archivedInstances = client.get(ArchivedProcessInstanceApi.class)
                .searchArchivedProcessInstances(
                        new ArchivedProcessInstanceApi.SearchArchivedProcessInstancesQueryParams()
                                .c(1)
                                .p(0)
                                .f(List.of("caller=any", "sourceObjectId=" + id)));
        if (!archivedInstances.isEmpty()) {
            return archivedInstances.get(0);
        }
        return null;
    }
}
