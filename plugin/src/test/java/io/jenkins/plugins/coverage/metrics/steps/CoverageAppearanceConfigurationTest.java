package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Metric;

import jenkins.model.Jenkins;

import io.jenkins.plugins.util.GlobalConfigurationFacade;
import io.jenkins.plugins.util.JenkinsFacade;

import static io.jenkins.plugins.coverage.metrics.Assertions.*;
import static org.mockito.Mockito.*;

class CoverageAppearanceConfigurationTest {
    @Test
    void shouldInitializeConfiguration() {
        var facade = mock(GlobalConfigurationFacade.class);
        var jenkins = mock(JenkinsFacade.class);
        var configuration = new CoverageAppearanceConfiguration(facade, jenkins);

        assertThat(configuration.doFillDefaultMetricItems()).isEmpty();
        when(jenkins.hasPermission(Jenkins.READ)).thenReturn(true);
        assertThat(configuration.doFillDefaultMetricItems()).map(o -> o.value)
                .contains("MODULE",
                        "PACKAGE",
                        "FILE",
                        "CLASS",
                        "METHOD",
                        "LINE",
                        "BRANCH",
                        "INSTRUCTION",
                        "MCDC_PAIR",
                        "FUNCTION_CALL",
                        "MUTATION",
                        "TEST_STRENGTH",
                        "TESTS",
                        "LOC",
                        "NCSS",
                        "CYCLOMATIC_COMPLEXITY",
                        "COGNITIVE_COMPLEXITY",
                        "NPATH_COMPLEXITY",
                        "ACCESS_TO_FOREIGN_DATA",
                        "COHESION",
                        "FAN_OUT",
                        "NUMBER_OF_ACCESSORS",
                        "WEIGHT_OF_CLASS",
                        "WEIGHED_METHOD_COUNT");

        verify(facade).load();

        assertThat(configuration).isEnableColumnByDefault();
        assertThat(configuration).hasDefaultMetric(Metric.LINE);
        assertThat(configuration).hasDefaultName(Messages.Coverage_Column());

        configuration.setEnableColumnByDefault(false);

        verify(facade).save();
        assertThat(configuration).isNotEnableColumnByDefault();

        configuration.setDefaultMetric(Metric.BRANCH);

        verify(facade, times(2)).save();
        assertThat(configuration).hasDefaultMetric(Metric.BRANCH);

        var branchCoverage = "Branch Coverage";
        configuration.setDefaultName(branchCoverage);

        verify(facade, times(3)).save();
        assertThat(configuration).hasDefaultName(branchCoverage);
    }
}
