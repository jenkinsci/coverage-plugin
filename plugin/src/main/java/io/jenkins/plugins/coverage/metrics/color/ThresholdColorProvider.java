package io.jenkins.plugins.coverage.metrics.color;

import edu.hm.hafner.coverage.Metric;

import java.awt.*;

/**
 * Computes a fill color for a numeric metric value by linearly interpolating between a red, a yellow, and a green
 * color, based on two user configurable thresholds: the value from which a result is considered fully green (best)
 * and the value from which a result is considered fully red (worst). Values beyond the two thresholds are clamped to
 * the corresponding boundary color. The metric's {@link Metric.MetricTendency tendency} decides which of the two
 * thresholds is the "good" (green) side and which is the "bad" (red) side.
 *
 * <p>Unlike {@link ColorProvider}, which maps a coverage percentage to one of eight discrete color steps, this class
 * interpolates continuously between only three anchor colors (red, yellow, green). Those three anchor colors are
 * still taken from a {@link ColorProvider} instance, though, so the result still follows the currently configured
 * (and possibly Jenkins theme dependent) plugin colors. It is used for views that let the user freely configure the
 * coloring thresholds (e.g. the hierarchy tree map).</p>
 *
 * @author Ullrich Hafner
 */
public final class ThresholdColorProvider {
    private static final double LUMINANCE_THRESHOLD = 0.230; // WCAG 2 ratio is 0.179
    private static final double MIDDLE = 0.5;

    private ThresholdColorProvider() {
        // prevents instantiation
    }

    /**
     * Computes the fill color for the given value. The red, yellow, and green anchor colors that are interpolated
     * between are taken from the passed {@link ColorProvider}, so the result follows the same (possibly Jenkins
     * theme dependent) colors as the rest of the plugin, rather than a fixed palette.
     *
     * @param value
     *         the metric value to colorize
     * @param tendency
     *         whether larger or smaller values are considered better
     * @param greenThreshold
     *         the value from which on a result is considered fully green (best)
     * @param redThreshold
     *         the value from which on a result is considered fully red (worst)
     * @param colorProvider
     *         provides the red ({@link ColorId#INSUFFICIENT}), yellow ({@link ColorId#AVERAGE}), and green
     *         ({@link ColorId#EXCELLENT}) anchor colors to interpolate between
     *
     * @return the interpolated fill color as a {@code #rrggbb} hex string
     */
    public static String getFillColorAsHex(final double value, final Metric.MetricTendency tendency,
            final double greenThreshold, final double redThreshold, final ColorProvider colorProvider) {
        var sanitizedValue = sanitizeFinite(value, 0.0);
        var sanitizedGreenThreshold = sanitizeFinite(greenThreshold, 0.0);
        var sanitizedRedThreshold = sanitizeFinite(redThreshold, 0.0);
        var ratio = computeRatio(sanitizedValue, tendency, sanitizedGreenThreshold, sanitizedRedThreshold);
        var red = colorProvider.getDisplayColorsOf(ColorId.INSUFFICIENT).getFillColor();
        var yellow = colorProvider.getDisplayColorsOf(ColorId.AVERAGE).getFillColor();
        var green = colorProvider.getDisplayColorsOf(ColorId.EXCELLENT).getFillColor();
        return toHex(interpolate(ratio, red, yellow, green));
    }

    /**
     * Returns a readable text color ({@code #000000} or {@code #ffffff}) that has sufficient contrast against the
     * given fill color.
     *
     * @param fillColorHex
     *         the fill color as a {@code #rrggbb} hex string
     *
     * @return the text color as a hex string
     */
    public static String getTextColorAsHex(final String fillColorHex) {
        var color = Color.decode(fillColorHex);
        return relativeLuminance(color) > LUMINANCE_THRESHOLD ? "#000000" : "#ffffff";
    }

    private static double computeRatio(final double value, final Metric.MetricTendency tendency,
            final double greenThreshold, final double redThreshold) {
        double numerator;
        double denominator;
        if (tendency == Metric.MetricTendency.LARGER_IS_BETTER) {
            // higher values are better: 0 at the red (worst) threshold, 1 at the green (best) threshold
            numerator = value - redThreshold;
            denominator = greenThreshold - redThreshold;
        }
        else {
            // lower values are better: 0 at the red (worst) threshold, 1 at the green (best) threshold
            numerator = redThreshold - value;
            denominator = redThreshold - greenThreshold;
        }
        if (Double.compare(denominator, 0.0) == 0) {
            return 1.0;
        }
        double ratio = numerator / denominator;
        return Math.clamp(ratio, 0.0, 1.0);
    }

    private static Color interpolate(final double ratio, final Color red, final Color yellow, final Color green) {
        if (ratio <= MIDDLE) {
            return lerp(red, yellow, ratio * 2.0);
        }
        return lerp(yellow, green, (ratio - MIDDLE) * 2.0);
    }

    private static Color lerp(final Color from, final Color to, final double ratio) {
        int r = Math.round(from.getRed() + (to.getRed() - from.getRed()) * (float) ratio);
        int g = Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * (float) ratio);
        int b = Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * (float) ratio);
        return new Color(clamp(r), clamp(g), clamp(b));
    }

    private static int clamp(final int channel) {
        return Math.max(0, Math.min(255, channel));
    }

    private static double relativeLuminance(final Color color) {
        return 0.2126 * linearize(color.getRed())
                + 0.7152 * linearize(color.getGreen())
                + 0.0722 * linearize(color.getBlue());
    }

    private static double linearize(final int channel) {
        double c = channel / 255.0;
        return c <= 0.039_28 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double sanitizeFinite(final double candidate, final double fallback) {
        return Double.isFinite(candidate) ? candidate : fallback;
    }

    private static String toHex(final Color color) {
        return "#%02x%02x%02x".formatted(color.getRed(), color.getGreen(), color.getBlue());
    }
}
