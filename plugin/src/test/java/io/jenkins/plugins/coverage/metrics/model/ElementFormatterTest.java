package io.jenkins.plugins.coverage.metrics.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junitpioneer.jupiter.DefaultLocale;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;

import java.util.Locale;

import static org.assertj.core.api.Assertions.*;

@DefaultLocale("en")
class ElementFormatterTest {
    @Test
    void shouldFormatTooltipForMetrics() {
        var formatter = new ElementFormatter();

        var density = new Value(Metric.COHESION, 33, 100);
        assertThat(formatter.getTooltip(density)).isEqualTo("Class Cohesion: 33.00%");
        assertThat(formatter.formatValueWithMetric(density)).isEqualTo("Class Cohesion: 33.00%");
        assertThat(formatter.formatDetailedValueWithMetric(density)).isEqualTo("Class Cohesion: 33.00%");
        assertThat(formatter.format(density)).isEqualTo("33.00%");
        assertThat(formatter.formatDetails(density)).isEqualTo("33.00%");
        assertThat(formatter.formatValue(density)).isEqualTo("33.00%");
        assertThat(formatter.formatAdditionalInformation(density)).isEmpty();

        var complexity = new Value(Metric.CYCLOMATIC_COMPLEXITY, 33);
        assertThat(formatter.getTooltip(complexity)).isEqualTo("Cyclomatic Complexity: 33");
        assertThat(formatter.formatValueWithMetric(complexity)).isEqualTo("Cyclomatic Complexity: 33");
        assertThat(formatter.formatDetailedValueWithMetric(complexity)).isEqualTo("Cyclomatic Complexity: 33");
        assertThat(formatter.format(complexity)).isEqualTo("33");
        assertThat(formatter.formatDetails(complexity)).isEqualTo("33");
        assertThat(formatter.formatValue(complexity)).isEqualTo("33");
        assertThat(formatter.formatAdditionalInformation(complexity)).isEmpty();

        var loc = new Value(Metric.LOC, 123);
        assertThat(formatter.getTooltip(loc)).isEqualTo("Lines of Code: 123");
        assertThat(formatter.formatValueWithMetric(loc)).isEqualTo("Lines of Code: 123");
        assertThat(formatter.formatDetailedValueWithMetric(loc)).isEqualTo("Lines of Code: 123");
        assertThat(formatter.format(loc)).isEqualTo("123");
        assertThat(formatter.formatDetails(loc)).isEqualTo("123");
        assertThat(formatter.formatValue(loc)).isEqualTo("123");
        assertThat(formatter.formatAdditionalInformation(loc)).isEmpty();
    }

    @Test
    void shouldFormatTooltipForCoverage() {
        var formatter = new ElementFormatter();

        var empty = Coverage.nullObject(Metric.BRANCH);
        assertThat(formatter.getTooltip(empty)).isEqualTo("Branch Coverage: n/a");
        assertThat(formatter.formatValueWithMetric(empty)).isEqualTo("Branch Coverage: n/a");
        assertThat(formatter.formatDetailedValueWithMetric(empty)).isEqualTo("Branch Coverage: n/a");
        assertThat(formatter.format(empty)).isEqualTo("n/a");
        assertThat(formatter.formatPercentage(empty, Locale.ENGLISH)).isEqualTo("n/a");
        assertThat(formatter.formatDetails(empty)).isEqualTo("n/a");
        assertThat(formatter.formatValue(empty)).isEqualTo("n/a");
        assertThat(formatter.formatAdditionalInformation(empty)).isEmpty();

        var line = new CoverageBuilder(Metric.LINE).withCovered(3).withMissed(1).build();
        assertThat(formatter.getTooltip(line)).isEqualTo("Line Coverage: 75.00% (3/4)");
        assertThat(formatter.formatValueWithMetric(line)).isEqualTo("Line Coverage: 75.00%");
        assertThat(formatter.formatDetailedValueWithMetric(line)).isEqualTo("Line Coverage: 75.00% (3/4)");
        assertThat(formatter.format(line)).isEqualTo("75.00%");
        assertThat(formatter.formatPercentage(line, Locale.ENGLISH)).isEqualTo("75.00%");
        assertThat(formatter.formatDetails(line)).isEqualTo("75.00% (3/4)");
        assertThat(formatter.formatValue(line)).isEqualTo("75.00% (3/4)");
        assertThat(formatter.formatAdditionalInformation(line)).isEqualTo("Covered: 3 - Missed: 1");

        var mutation = new CoverageBuilder(Metric.MUTATION).withCovered(3).withMissed(1).build();
        assertThat(formatter.getTooltip(mutation)).isEqualTo("Mutation Coverage: 75.00% (3/4)");
        assertThat(formatter.formatValueWithMetric(mutation)).isEqualTo("Mutation Coverage: 75.00%");
        assertThat(formatter.formatDetailedValueWithMetric(mutation)).isEqualTo("Mutation Coverage: 75.00% (3/4)");
        assertThat(formatter.format(mutation)).isEqualTo("75.00%");
        assertThat(formatter.formatPercentage(mutation, Locale.ENGLISH)).isEqualTo("75.00%");
        assertThat(formatter.formatDetails(mutation)).isEqualTo("75.00% (3/4)");
        assertThat(formatter.formatValue(mutation)).isEqualTo("75.00% (3/4)");
        assertThat(formatter.formatAdditionalInformation(mutation)).isEqualTo("Killed: 3 - Survived: 1");
    }

    @Test
    void shouldFormatTooltipForDelta() {
        var formatter = new ElementFormatter();

        var delta = new Value(Metric.FILE, -20);
        assertThat(formatter.getTooltip(delta)).isEqualTo("File Coverage: -20.00%");
        assertThat(formatter.formatValueWithMetric(delta)).isEqualTo("File Coverage: -20.00%");
        assertThat(formatter.format(delta)).isEqualTo("-20.00%");
        assertThat(formatter.formatDetailedValueWithMetric(delta)).isEqualTo("File Coverage: -20.00%");
        assertThat(formatter.formatDetails(delta)).isEqualTo("-20.00%");
        assertThat(formatter.formatValue(delta)).isEqualTo("-20.00%");
        assertThat(formatter.formatAdditionalInformation(delta)).isEmpty();
    }

    @Test
    void shouldHandleOverflowGracefully() {
        var formatter = new ElementFormatter();

        var fraction = new Difference(Metric.LINE, Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1);
        var delta = formatter.formatDelta(Metric.LINE, fraction, Locale.ENGLISH);

        assertThat(delta).isEqualTo("+1.00%");
    }

    @Test
    void shouldFormatDelta() {
        var formatter = new ElementFormatter();

        assertThat(formatter.formatDelta(Metric.LINE,
                Value.valueOf("LINE: -100"),
                Locale.ENGLISH)).isEqualTo("-100.00%");
        assertThat(formatter.formatDelta(Metric.LOC,
                Value.valueOf("LOC: -1"),
                Locale.ENGLISH)).isEqualTo("-1");
        assertThat(formatter.formatDelta(Metric.CYCLOMATIC_COMPLEXITY,
                Value.valueOf("CYCLOMATIC_COMPLEXITY: -1"),
                Locale.ENGLISH)).isEqualTo("-1");
    }

    @ParameterizedTest
    @EnumSource(value = Metric.class, mode = EnumSource.Mode.EXCLUDE, names = {"CONTAINER", "CYCLOMATIC_COMPLEXITY"})
    void shouldSupportAllMetrics(final Metric metric) {
        var formatter = new ElementFormatter();

        assertThat(formatter.getMetricItems()).extracting(o -> o.value).contains(metric.name());
        assertThat(formatter.getLabel(metric)).isNotEmpty();
        assertThat(formatter.getDisplayName(metric)).isNotEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = Baseline.class, mode = EnumSource.Mode.EXCLUDE, names = "INDIRECT")
    void shouldSupportAllBaseline(final Baseline baseline) {
        var formatter = new ElementFormatter();

        assertThat(formatter.getBaselineItems()).extracting(o -> o.value).contains(baseline.name());
        assertThat(formatter.getDisplayName(baseline)).isNotEmpty();
    }
}
