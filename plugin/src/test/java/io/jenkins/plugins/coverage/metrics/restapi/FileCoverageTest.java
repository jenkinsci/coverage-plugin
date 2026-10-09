package io.jenkins.plugins.coverage.metrics.restapi;

import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

/**
 * Tests {@link FileCoverage}.
 */
class FileCoverageTest {
    @Test
    void shouldObeyEqualsContract() {
        EqualsVerifier.forClass(FileCoverage.class).usingGetClass().verify();
    }
}
