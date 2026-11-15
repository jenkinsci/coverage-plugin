package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.Run;
import io.jenkins.plugins.util.IntegrationTestWithJenkinsPerSuite;
import org.apache.commons.lang3.math.Fraction;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the shared build XStream is configured during Jenkins initialization, before build records are read.
 */
class CoverageXStreamInitializerITest extends IntegrationTestWithJenkinsPerSuite {
    @Test
    void shouldRegisterCoverageConvertersBeforeLoadingBuilds() {
        assertThat(Run.XSTREAM2.getConverterLookup().lookupConverterForType(Fraction.class))
                .isInstanceOf(CoverageXmlStream.FractionConverter.class);
    }
}
