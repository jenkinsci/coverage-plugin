package io.jenkins.plugins.coverage.metrics.restapi;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FileWithModifiedLines}.
 */
class FileWithModifiedLinesTest {
    @Test
    void shouldObeyEqualsContract() {
        EqualsVerifier.forClass(FileWithModifiedLines.class).usingGetClass().verify();
    }
}
