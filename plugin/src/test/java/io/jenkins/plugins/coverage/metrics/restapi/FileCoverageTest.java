package io.jenkins.plugins.coverage.metrics.restapi;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link FileCoverage}.
 */
class FileCoverageTest {
    @Test
    void shouldObeyEqualsContract() {
        EqualsVerifier.forClass(FileCoverage.class).usingGetClass().verify();
    }
}
