package io.jenkins.plugins.coverage.metrics.model;

import java.util.Locale;

import org.apache.commons.lang3.math.Fraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import edu.hm.hafner.coverage.FractionValue;
import edu.hm.hafner.coverage.Metric;

import static org.assertj.core.api.Assertions.*;

class ElementFormatterTest {
    @Test
    void shouldFormatDensity() {
        var formatter = new ElementFormatter();

        var delta = formatter.format(new FractionValue(Metric.COMPLEXITY_DENSITY, 33, 100), Locale.ENGLISH);

        assertThat(delta).isEqualTo("0.33");
    }

    @Test
    void shouldHandleOverflowGracefully() {
        var formatter = new ElementFormatter();

        var fraction = Fraction.getFraction(Integer.MAX_VALUE - 1, Integer.MAX_VALUE - 1);
        var delta = formatter.formatDelta(fraction, Metric.LINE, Locale.ENGLISH);

        assertThat(delta).isEqualTo("+100.00%");
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
