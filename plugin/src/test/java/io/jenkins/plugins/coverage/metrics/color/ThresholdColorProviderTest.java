package io.jenkins.plugins.coverage.metrics.color;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Metric.MetricTendency;

import java.awt.*;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests the class {@link ThresholdColorProvider}.
 *
 * @author Ullrich Hafner
 */
class ThresholdColorProviderTest {
    private static final double GREEN_THRESHOLD = 80.0;
    private static final double RED_THRESHOLD = 20.0;

    /** Pure red, yellow, and green anchor colors so the interpolated hex value can be computed by hand. */
    private static final ColorProvider PURE_COLORS = new ColorProvider(Map.of(
            ColorId.INSUFFICIENT, new ColorProvider.DisplayColors(Color.BLACK, Color.RED),
            ColorId.AVERAGE, new ColorProvider.DisplayColors(Color.BLACK, Color.YELLOW),
            ColorId.EXCELLENT, new ColorProvider.DisplayColors(Color.BLACK, Color.GREEN)));

    @Test
    void shouldReturnFullyGreenAtOrAboveGreenThreshold() {
        assertThat(fillColor(GREEN_THRESHOLD, MetricTendency.LARGER_IS_BETTER)).isEqualTo("#00ff00");
        assertThat(fillColor(GREEN_THRESHOLD + 1000, MetricTendency.LARGER_IS_BETTER))
                .as("values beyond the green threshold are clamped to fully green")
                .isEqualTo("#00ff00");
    }

    @Test
    void shouldReturnFullyRedAtOrBelowRedThreshold() {
        assertThat(fillColor(RED_THRESHOLD, MetricTendency.LARGER_IS_BETTER)).isEqualTo("#ff0000");
        assertThat(fillColor(RED_THRESHOLD - 1000, MetricTendency.LARGER_IS_BETTER))
                .as("values beyond the red threshold are clamped to fully red")
                .isEqualTo("#ff0000");
    }

    @Test
    void shouldReturnYellowAtTheMidpoint() {
        var midpoint = (GREEN_THRESHOLD + RED_THRESHOLD) / 2.0;

        assertThat(fillColor(midpoint, MetricTendency.LARGER_IS_BETTER)).isEqualTo("#ffff00");
    }

    @Test
    void shouldInterpolateBetweenRedAndYellow() {
        // one quarter of the way from red (20) to green (80) lands exactly halfway between red and yellow
        var quarter = RED_THRESHOLD + (GREEN_THRESHOLD - RED_THRESHOLD) * 0.25;

        assertThat(fillColor(quarter, MetricTendency.LARGER_IS_BETTER)).isEqualTo("#ff8000");
    }

    @Test
    void shouldInterpolateBetweenYellowAndGreen() {
        // three quarters of the way from red (20) to green (80) lands exactly halfway between yellow and green
        var threeQuarters = RED_THRESHOLD + (GREEN_THRESHOLD - RED_THRESHOLD) * 0.75;

        assertThat(fillColor(threeQuarters, MetricTendency.LARGER_IS_BETTER)).isEqualTo("#80ff00");
    }

    @Test
    void shouldInvertRatioForSmallerIsBetterMetrics() {
        // for a "smaller is better" metric (e.g. complexity), the green threshold is the lower value
        assertThat(ThresholdColorProvider.getFillColorAsHex(0, MetricTendency.SMALLER_IS_BETTER,
                0, 100, PURE_COLORS)).isEqualTo("#00ff00");
        assertThat(ThresholdColorProvider.getFillColorAsHex(100, MetricTendency.SMALLER_IS_BETTER,
                0, 100, PURE_COLORS)).isEqualTo("#ff0000");
        assertThat(ThresholdColorProvider.getFillColorAsHex(50, MetricTendency.SMALLER_IS_BETTER,
                0, 100, PURE_COLORS)).isEqualTo("#ffff00");
    }

    @Test
    void shouldReturnFullyGreenWhenThresholdsAreEqual() {
        // a zero denominator (green == red) must not divide by zero, and is defined to be fully green
        assertThat(fillColor(50.0, MetricTendency.LARGER_IS_BETTER, 50.0, 50.0)).isEqualTo("#00ff00");
        assertThat(fillColor(-1000.0, MetricTendency.LARGER_IS_BETTER, 50.0, 50.0))
                .as("even far-away values are still fully green when both thresholds coincide")
                .isEqualTo("#00ff00");
    }

    @Test
    void shouldSanitizeNonFiniteValues() {
        // NaN and infinite inputs fall back to 0.0 rather than propagating NaN through the calculation
        assertThat(fillColor(Double.NaN, MetricTendency.LARGER_IS_BETTER)).isEqualTo("#ff0000");
        // all three inputs sanitize to 0.0, so numerator and denominator both become 0 -- defined as fully green
        assertThat(fillColor(Double.POSITIVE_INFINITY, MetricTendency.LARGER_IS_BETTER,
                Double.NaN, Double.NEGATIVE_INFINITY)).isEqualTo("#00ff00");
    }

    @Test
    void shouldPickBlackTextOnALightBackground() {
        assertThat(ThresholdColorProvider.getTextColorAsHex("#ffffff")).isEqualTo("#000000");
    }

    @Test
    void shouldPickWhiteTextOnADarkBackground() {
        assertThat(ThresholdColorProvider.getTextColorAsHex("#000000")).isEqualTo("#ffffff");
    }

    private String fillColor(final double value, final MetricTendency tendency) {
        return fillColor(value, tendency, GREEN_THRESHOLD, RED_THRESHOLD);
    }

    private String fillColor(final double value, final MetricTendency tendency,
            final double greenThreshold, final double redThreshold) {
        return ThresholdColorProvider.getFillColorAsHex(value, tendency, greenThreshold, redThreshold, PURE_COLORS);
    }
}
