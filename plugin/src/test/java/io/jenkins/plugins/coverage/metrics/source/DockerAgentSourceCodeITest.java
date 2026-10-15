package io.jenkins.plugins.coverage.metrics.source;

import hudson.model.Node;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;
import org.testcontainers.utility.MountableFile;

/**
 * Verifies if source code copying and rendering work on Docker agents.
 *
 * @author Ullrich Hafner
 */
@EnabledIfDockerAvailable
class DockerAgentSourceCodeITest extends SourceCodeITest {
    private static final String SOURCES_IN_DOCKER_PATH = "/tmp/coverage";
    private static final String ACU_COBOL_PARSER_CONTAINER_PATH =
            SOURCES_IN_DOCKER_PATH + "/" + ACU_COBOL_PARSER_PACKAGE_PATH + ACU_COBOL_PARSER_FILE_NAME;
    private static final String PATH_UTIL_CONTAINER_PATH =
            SOURCES_IN_DOCKER_PATH + "/" + PATH_UTIL_PACKAGE_PATH + PATH_UTIL_FILE_NAME;

    private static final String RESOURCES = "io/jenkins/plugins/coverage/metrics/source/";

    private static final AgentContainer AGENT_CONTAINER = new AgentContainer()
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource(RESOURCES + ACU_COBOL_PARSER_SOURCE_FILE),
                    ACU_COBOL_PARSER_CONTAINER_PATH)
            .withCopyFileToContainer(
                    MountableFile.forClasspathResource(RESOURCES + PATH_UTIL_SOURCE_FILE), PATH_UTIL_CONTAINER_PATH);

    @BeforeAll
    static void startAgent() {
        AGENT_CONTAINER.start();
    }

    @AfterAll
    static void stopAgent() {
        AGENT_CONTAINER.stop();
    }

    @Override
    Node crateCoverageAgent() {
        return createDockerAgent(AGENT_CONTAINER, AGENT_LABEL);
    }

    @Override
    String createExternalFolder() {
        return SOURCES_IN_DOCKER_PATH;
    }

    @Override
    void copySourceFileToAgent(final String sourceDirectory, final Node localAgent, final WorkflowJob job) {
        if (!sourceDirectory.startsWith(SOURCES_IN_DOCKER_PATH)) {
            copySingleFileToAgentWorkspace(
                    localAgent,
                    job,
                    ACU_COBOL_PARSER_SOURCE_FILE,
                    createDestinationPath(sourceDirectory, ACU_COBOL_PARSER_PACKAGE_PATH, ACU_COBOL_PARSER_FILE_NAME));
            copySingleFileToAgentWorkspace(
                    localAgent,
                    job,
                    PATH_UTIL_SOURCE_FILE,
                    createDestinationPath(sourceDirectory, PATH_UTIL_PACKAGE_PATH, PATH_UTIL_FILE_NAME));
        }
    }
}
