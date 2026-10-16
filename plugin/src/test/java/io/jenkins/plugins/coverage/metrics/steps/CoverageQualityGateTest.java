package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.security.AnyTypePermission;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.util.QualityGate.QualityGateCriticality;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests the class {@link CoverageQualityGate}.
 */
class CoverageQualityGateTest {
    @Test
    void shouldUseTotalAggregationByDefault() {
        var gate = new CoverageQualityGate(80.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);

        assertThat(gate.getAggregation()).isEqualTo(MetricAggregation.TOTAL);
        assertThat(gate.getName()).isEqualTo("Overall project - Line Coverage");
    }

    @Test
    void shouldShowAggregationInName() {
        var gate = new CoverageQualityGate(
                15.0,
                Metric.CYCLOMATIC_COMPLEXITY,
                Baseline.PROJECT,
                QualityGateCriticality.UNSTABLE,
                MetricAggregation.MAXIMUM);

        assertThat(gate.getAggregation()).isEqualTo(MetricAggregation.MAXIMUM);
        assertThat(gate.getName()).isEqualTo("Overall project - Cyclomatic Complexity (Maximum)");
    }

    @ParameterizedTest
    @EnumSource(MetricAggregation.class)
    void shouldPersistAggregation(final MetricAggregation aggregation) {
        var xStream = createXStream();
        var gate = new CoverageQualityGate(
                15.0, Metric.CYCLOMATIC_COMPLEXITY, Baseline.PROJECT, QualityGateCriticality.UNSTABLE, aggregation);

        var restored = (CoverageQualityGate) xStream.fromXML(xStream.toXML(gate));

        assertThat(restored.getAggregation()).isEqualTo(aggregation);
        assertThat(restored.getName()).isEqualTo(gate.getName());
    }

    @Test
    void shouldRestoreDefaultAggregationForConfigurationWithoutAggregation() {
        var xStream = createXStream();
        var gate = new CoverageQualityGate(80.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);

        var legacyXml = removeAggregation(xStream.toXML(gate));
        assertThat(legacyXml).doesNotContain("aggregation");

        var restored = (CoverageQualityGate) xStream.fromXML(legacyXml);

        assertThat(restored.getAggregation()).isEqualTo(MetricAggregation.TOTAL);
        assertThat(restored.getName()).isEqualTo("Overall project - Line Coverage");
        assertThat(restored.getMetric()).isEqualTo(Metric.LINE);
        assertThat(restored.getBaseline()).isEqualTo(Baseline.PROJECT);
        assertThat(restored.getThreshold()).isEqualTo(80.0);
    }

    @Test
    void shouldReplaceMissingAggregationWithDefault() {
        var gate = new CoverageQualityGate(80.0, Metric.LINE, Baseline.PROJECT, QualityGateCriticality.UNSTABLE);
        gate.setAggregation(null);

        assertThat(gate.readResolve()).isSameAs(gate);
        assertThat(gate.getAggregation()).isEqualTo(MetricAggregation.TOTAL);
    }

    private static String removeAggregation(final String xml) {
        return xml.replaceAll("(?m)^\\s*<aggregation>.*</aggregation>\\R", "");
    }

    private static XStream createXStream() {
        var xStream = new XStream();
        xStream.addPermission(AnyTypePermission.ANY);
        return xStream;
    }
}
