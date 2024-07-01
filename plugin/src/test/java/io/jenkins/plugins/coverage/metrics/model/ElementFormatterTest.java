package io.jenkins.plugins.coverage.metrics.model;

import java.util.Locale;

import org.apache.commons.lang3.math.Fraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junitpioneer.jupiter.DefaultLocale;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FractionValue;
import edu.hm.hafner.coverage.LinesOfCode;
import edu.hm.hafner.coverage.Metric;

import static org.assertj.core.api.Assertions.*;

@DefaultLocale("en")
class ElementFormatterTest {
    @Test
    void shouldFormatTooltip() {
        var formatter = new ElementFormatter();

        var density = new FractionValue(Metric.COMPLEXITY_DENSITY, 33, 100);
        assertThat(formatter.getTooltip(density)).isEqualTo("Complexity Density: 0.33");
        assertThat(formatter.formatValueWithMetric(density)).isEqualTo("Complexity Density: 0.33");
        assertThat(formatter.formatDetailedValueWithMetric(density)).isEqualTo("Complexity Density: 0.33");
        assertThat(formatter.format(density)).isEqualTo("0.33");
        assertThat(formatter.formatDetails(density)).isEqualTo("0.33");
        assertThat(formatter.formatValue(density)).isEqualTo("0.33");
        assertThat(formatter.formatAdditionalInformation(density)).isEmpty();

        var loc = new LinesOfCode(123);
        assertThat(formatter.getTooltip(loc)).isEqualTo("LOC: 123");
        assertThat(formatter.formatValueWithMetric(loc)).isEqualTo("Lines of Code: 123");
        assertThat(formatter.formatDetailedValueWithMetric(loc)).isEqualTo("Lines of Code: 123");
        assertThat(formatter.format(loc)).isEqualTo("123");
        assertThat(formatter.formatDetails(loc)).isEqualTo("123");
        assertThat(formatter.formatValue(loc)).isEqualTo("123");
        assertThat(formatter.formatAdditionalInformation(loc)).isEmpty();

        var empty = Coverage.nullObject(Metric.BRANCH);
        assertThat(formatter.getTooltip(empty)).isEqualTo("Branch Coverage: -");
        assertThat(formatter.formatValueWithMetric(empty)).isEqualTo("Branch Coverage: -");
        assertThat(formatter.formatDetailedValueWithMetric(empty)).isEqualTo("Branch Coverage: -");
        assertThat(formatter.format(empty)).isEqualTo("-");
        assertThat(formatter.formatPercentage(empty, Locale.ENGLISH)).isEqualTo("-");
        assertThat(formatter.formatDetails(empty)).isEqualTo("-");
        assertThat(formatter.formatValue(empty)).isEqualTo("-");
        assertThat(formatter.formatAdditionalInformation(empty)).isEmpty();

        var line = new CoverageBuilder(Metric.LINE).withCovered(3).withMissed(1).build();
        assertThat(formatter.getTooltip(line)).isEqualTo("Line Coverage: 75.00% (Covered: 3 - Missed: 1)");
        assertThat(formatter.formatValueWithMetric(line)).isEqualTo("Line Coverage: 75.00%");
        assertThat(formatter.formatDetailedValueWithMetric(line)).isEqualTo("Line Coverage: 75.00% (3/4)");
        assertThat(formatter.format(line)).isEqualTo("75.00%");
        assertThat(formatter.formatPercentage(line, Locale.ENGLISH)).isEqualTo("75.00%");
        assertThat(formatter.formatDetails(line)).isEqualTo("75.00% (3/4)");
        assertThat(formatter.formatValue(line)).isEqualTo("75.00% (3/4)");
        assertThat(formatter.formatAdditionalInformation(line)).isEqualTo("Covered: 3 - Missed: 1");

        var mutation = new CoverageBuilder(Metric.MUTATION).withCovered(3).withMissed(1).build();
        assertThat(formatter.getTooltip(mutation)).isEqualTo("Mutation Coverage: 75.00% (Killed: 3 - Survived: 1)");
        assertThat(formatter.formatValueWithMetric(mutation)).isEqualTo("Mutation Coverage: 75.00%");
        assertThat(formatter.formatDetailedValueWithMetric(mutation)).isEqualTo("Mutation Coverage: 75.00% (3/4)");
        assertThat(formatter.format(mutation)).isEqualTo("75.00%");
        assertThat(formatter.formatPercentage(mutation, Locale.ENGLISH)).isEqualTo("75.00%");
        assertThat(formatter.formatDetails(mutation)).isEqualTo("75.00% (3/4)");
        assertThat(formatter.formatValue(mutation)).isEqualTo("75.00% (3/4)");
        assertThat(formatter.formatAdditionalInformation(mutation)).isEqualTo("Killed: 3 - Survived: 1");

        var delta = new FractionValue(Metric.FILE, -1, 5);
        // These do not make sense when the fraction represents a delta
        // assertThat(formatter.getTooltip(delta)).isEqualTo("File Coverage: -20.00%");
        // assertThat(formatter.formatValueWithMetric(delta)).isEqualTo("File Coverage: -20.00%");
        // assertThat(formatter.format(delta)).isEqualTo("-20.00%");
        assertThat(formatter.formatDetailedValueWithMetric(delta)).isEqualTo("File Coverage: -0.20%");
        assertThat(formatter.formatDetails(delta)).isEqualTo("-0.20%");
        assertThat(formatter.formatValue(delta)).isEqualTo("-0.20%");
        assertThat(formatter.formatAdditionalInformation(delta)).isEmpty();
    }

    @Test
    void shouldHandleOverflowGracefully() {
        var formatter = new ElementFormatter();

        var fraction = Fraction.getFraction(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1);
        var delta = formatter.formatDelta(Metric.LINE, fraction, Locale.ENGLISH);

        assertThat(delta).isEqualTo("+100.00%");
    }

    @Test
    void shouldFormatDelta() {
        var formatter = new ElementFormatter();

        assertThat(formatter.formatDelta(Metric.LINE, Fraction.getFraction(-1, 1),
                Locale.ENGLISH)).isEqualTo("-100.00%");
        assertThat(formatter.formatDelta(Metric.LOC, Fraction.getFraction(-1, 1),
                Locale.ENGLISH)).isEqualTo("-1");
        assertThat(formatter.formatDelta(Metric.COMPLEXITY_DENSITY, Fraction.getFraction(-1, 1),
                Locale.ENGLISH)).isEqualTo("-1.00");
    }

    @ParameterizedTest
    @EnumSource(value = Metric.class, mode = EnumSource.Mode.EXCLUDE, names = {"CONTAINER", "COMPLEXITY_DENSITY"})
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
