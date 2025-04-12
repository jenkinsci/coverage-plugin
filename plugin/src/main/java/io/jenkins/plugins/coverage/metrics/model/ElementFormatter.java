package io.jenkins.plugins.coverage.metrics.model;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Percentage;
import edu.hm.hafner.coverage.Value;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import hudson.Functions;
import hudson.util.ListBoxModel;

import io.jenkins.plugins.coverage.metrics.color.ColorProvider.DisplayColors;
import io.jenkins.plugins.coverage.metrics.color.ColorProviderFactory;

/**
 * A localized formatter for coverages, metrics, baselines, etc.
 *
 * @author Florian Orendi
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity"})
public final class ElementFormatter {
    private static final Pattern PERCENTAGE = Pattern.compile("\\d+(\\.\\d+)?%");

    /**
     * Formats a generic value using a specific rendering method. The type of the given {@link Value} instance is used
     * to select the best matching rendering method. This non-object-oriented approach is required since the
     * {@link Value} instances are provided by a library that is not capable of localizing these values for the user.
     *
     * @param value
     *         the value to format
     *
     * @return the formatted value as plain text
     */
    public String getTooltip(final Value value) {
        return value.getDetails(getLocale());
    }

    /**
     * Formats a generic value using a specific rendering method. The type of the given {@link Value} instance is used
     * to select the best matching rendering method. This non-object-oriented approach is required since the
     * {@link Value} instances are provided by a library that is not capable of localizing these values for the user.
     *
     * @param value
     *         the value to format
     *
     * @return the formatted value as plain text
     */
    public String format(final Value value) {
        return format(value, getLocale());
    }

    private Locale getLocale() {
        return Functions.getCurrentLocale();
    }

    /**
     * Formats a generic value using a specific rendering method. The type of the given {@link Value} instance is used
     * to select the best matching rendering method. This non-object-oriented approach is required since the
     * {@link Value} instances are provided by a library that is not capable of localizing these values for the user.
     *
     * @param value
     *         the value to format
     * @param locale
     *         the locale to use to render the values
     *
     * @return the formatted value as plain text
     */
    public String format(final Value value, final Locale locale) {
        return value.asText(locale);
    }

    /**
     * Formats a generic value using a specific rendering method. The type of the given {@link Value} instance is used
     * to select the best matching rendering method. This non-object-oriented approach is required since the
     * {@link Value} instances are provided by a library that is not capable of localizing these values for the user.
     *
     * @param value
     *         the value to format
     *
     * @return the formatted value as plain text
     */
    public String formatDetails(final Value value) {
        return formatDetails(value, getLocale());
    }

    /**
     * Formats a generic value using a specific rendering method. The type of the given {@link Value} instance is used
     * to select the best matching rendering method. This non-object-oriented approach is required since the
     * {@link Value} instances are provided by a library that is not capable of localizing these values for the user.
     *
     * @param value
     *         the value to format
     * @param locale
     *         the locale to use to render the values
     *
     * @return the formatted value as plain text
     */
    private String formatDetails(final Value value, final Locale locale) {
        return value.asInformativeText(locale);
    }

    /**
     * Formats additional information for a generic value using a specific rendering method. This information can be
     * added as a tooltip. The type of the given {@link Value} instance is used to select the best matching
     * rendering method. This non-object-oriented approach is required since the {@link Value} instances are
     * provided by a library that is not capable of localizing these values for the user.
     *
     * @param value
     *         the value to format
     *
     * @return the formatted value as plain text
     */
    public String formatAdditionalInformation(final Value value) {
        if (value instanceof Coverage coverage && coverage.isSet()) {
            return formatAdditionalCoverageInformation(coverage);
        }
        return StringUtils.EMPTY;
    }

    private String formatAdditionalCoverageInformation(final Coverage coverage) {
        if (coverage.getMetric() == Metric.MUTATION
                || coverage.getMetric() == Metric.TEST_STRENGTH) {
            return formatCoverage(coverage, Messages.Metric_MUTATION_Killed(),
                    Messages.Metric_MUTATION_Survived());
        }
        return formatCoverage(coverage, Messages.Metric_Coverage_Covered(),
                Messages.Metric_Coverage_Missed());
    }

    private static String formatCoverage(final Coverage coverage, final String coveredText, final String missedText) {
        return String.format("%s: %d - %s: %d", coveredText, coverage.getCovered(),
                missedText, coverage.getMissed());
    }

    /**
     * Returns whether the value should be rendered by using a color badge.
     *
     * @param value
     *         the value to render
     *
     * @return {@code true} if the value should be rendered by using a color badge, {@code false} otherwise
     */
    public boolean showColors(final Value value) {
        return value instanceof Coverage;
    }

    /**
     * Provides the colors to render a given coverage percentage.
     *
     * @param baseline
     *         the baseline to show
     * @param value
     *         the value to format
     *
     * @return the display colors to use
     */
    public DisplayColors getDisplayColors(final Baseline baseline, final Value value) {
        var defaultColorProvider = ColorProviderFactory.createDefaultColorProvider();
        if (value instanceof Coverage) {
            return baseline.getDisplayColors(((Coverage) value).getCoveredPercentage().toDouble(),
                    defaultColorProvider);
        }
        else {
            return baseline.getDisplayColors(value.asDouble(), defaultColorProvider);
        }
    }

    /**
     * Returns a formatted and localized String representation of the specified value (without metric).
     *
     * @param value
     *         the value to format
     *
     * @return the value formatted as a string
     */
    public String formatValue(final Value value) {
        return formatDetails(value, getLocale());
    }

    /**
     * Returns a formatted and localized String representation of the specified value prefixed with the metric name.
     *
     * @param value
     *         the value to format
     *
     * @return the value formatted as a string
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String formatValueWithMetric(final Value value) {
        return value.getSummary(getLocale());
    }

    /**
     * Returns a formatted and localized String representation of the specified value prefixed with the metric name.
     * The value will be printed with all details (number of covered and missed items).
     *
     * @param value
     *         the value to format
     *
     * @return the value formatted as a string
     */
    @SuppressWarnings("unused") // Called by jelly view
    // FIXME: delete
    public String formatDetailedValueWithMetric(final Value value) {
        return value.getDetails(getLocale());
    }

    /**
     * Transforms percentages with a ',' decimal separator to a representation using a '.' in order to use the
     * percentage for styling HTML tags.
     *
     * @param percentage
     *         The text representation of a percentage
     *
     * @return the formatted percentage string
     */
    public String getBackgroundColorFillPercentage(final String percentage) {
        String formattedPercentage = percentage.replace(",", ".");
        if (PERCENTAGE.matcher(formattedPercentage).matches()) {
            return formattedPercentage;
        }
        return "100%";
    }

    /**
     * Returns the fill percentage for the specified value.
     *
     * @param value
     *         the value to format
     *
     * @return the percentage string
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getBackgroundColorFillPercentage(final Value value) {
        if (value instanceof Coverage) {
            return format(value, Locale.ENGLISH);
        }
        return "100%";
    }

    /**
     * Formats a coverage as a percentage number. The shown value is multiplied by 100 and rounded by two decimals.
     *
     * @param coverage
     *         the coverage to format
     * @param locale
     *         the locale to use to render the values
     *
     * @return the formatted percentage as plain text
     */
    public String formatPercentage(final Coverage coverage, final Locale locale) {
        return coverage.asText(locale);
    }

    /**
     * Formats a fraction in the interval [0, 1] as a percentage number. The shown value is multiplied by 100 and
     * rounded by two decimals.
     *
     * @param fraction
     *         the fraction to format (in the interval [0, 1])
     * @param locale
     *         the locale to use to render the values
     *
     * @return the formatted percentage as plain text
     */
    private String formatPercentage(final Percentage fraction, final Locale locale) {
        return String.format(locale, "%.2f%%", fraction.toDouble());
    }

    /**
     * Formats a coverage given by covered and total elements as a percentage number. The shown value is multiplied by
     * 100 and * rounded by two decimals.
     *
     * @param covered
     *         the number of covered items
     * @param total
     *         the number of total items
     * @param locale
     *         the locale to use to render the values
     *
     * @return the formatted percentage as plain text
     */
    public String formatPercentage(final int covered, final int total, final Locale locale) {
        return formatPercentage(Percentage.valueOf(covered, total), locale);
    }

    /**
     * Formats a delta percentage to its plain text representation with a leading sign and rounds the value to two
     * decimals.
     *
     * @param metric
     *         the metric of the value
     * @param value
     *         the value of the delta
     * @param locale
     *         the locale to use to render the values
     *
     * @return the formatted delta percentage as plain text with a leading sign
     */
    public String formatDelta(@SuppressWarnings("unused") final Metric metric, final Value value, final Locale locale) {
        return formatDelta(value, locale);
    }

    /**
     * Formats a delta percentage to its plain text representation with a leading sign and rounds the value to two
     * decimals.
     *
     * @param value
     *         the value of the delta
     * @param locale
     *         the locale to use to render the values
     *
     * @return the formatted delta percentage as plain text with a leading sign
     */
    public String formatDelta(final Value value, final Locale locale) {
        return value.asText(locale);
    }

    /**
     * Returns a localized human-readable name for the specified metric.
     *
     * @param metric
     *         the metric to get the name for
     *
     * @return the display name
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public String getDisplayName(final Metric metric) {
        return metric.getDisplayName();
    }

    /**
     * Gets the display names of the existing {@link Metric coverage metrics}, sorted by the metrics ordinal.
     *
     * @return the sorted metric display names
     */
    public List<String> getSortedCoverageDisplayNames() {
        return Metric.getCoverageMetrics().stream()
                .map(this::getDisplayName)
                .collect(Collectors.toList());
    }

    /**
     * Formats a stream of values to their display representation by using the given locale.
     *
     * @param values
     *         The values to be formatted
     * @param locale
     *         The locale to be used for formatting
     *
     * @return the formatted values in the origin order of the stream
     */
    public List<String> getFormattedValues(final Stream<? extends Value> values, final Locale locale) {
        return values.map(value -> formatDetails(value, locale)).collect(Collectors.toList());
    }

    /**
     * Returns a localized human-readable label for the specified metric.
     *
     * @param metric
     *         the metric to get the label for
     *
     * @return the display name
     */
    @SuppressWarnings("PMD.CyclomaticComplexity")
    public String getLabel(final Metric metric) {
        return metric.getLabel();
    }

    /**
     * Returns a localized human-readable name for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the name for
     *
     * @return the display name
     */
    public String getDisplayName(final Baseline baseline) {
        return switch (baseline) {
            case PROJECT -> Messages.Baseline_PROJECT();
            case MODIFIED_LINES -> Messages.Baseline_MODIFIED_LINES();
            case MODIFIED_FILES -> Messages.Baseline_MODIFIED_FILES();
            case PROJECT_DELTA -> Messages.Baseline_PROJECT_DELTA();
            case MODIFIED_LINES_DELTA -> Messages.Baseline_MODIFIED_LINES_DELTA();
            case MODIFIED_FILES_DELTA -> Messages.Baseline_MODIFIED_FILES_DELTA();
            case INDIRECT -> Messages.Baseline_INDIRECT();
        };
    }

    /**
     * Returns all available metrics as a {@link ListBoxModel}.
     *
     * @return the metrics in a {@link ListBoxModel}
     */
    public ListBoxModel getMetricItems() {
        ListBoxModel options = new ListBoxModel();

        Arrays.stream(Metric.values())
                .filter(m -> m != Metric.CONTAINER)
                .forEach(m -> options.add(m.getDisplayName(), m.name()));

        return options;
    }

    /**
     * Returns all available baselines as a {@link ListBoxModel}.
     *
     * @return the baselines in a {@link ListBoxModel}
     */
    public ListBoxModel getBaselineItems() {
        ListBoxModel options = new ListBoxModel();
        add(options, Baseline.PROJECT);
        add(options, Baseline.MODIFIED_LINES);
        add(options, Baseline.MODIFIED_FILES);
        add(options, Baseline.PROJECT_DELTA);
        add(options, Baseline.MODIFIED_LINES_DELTA);
        add(options, Baseline.MODIFIED_FILES_DELTA);
        return options;
    }

    private void add(final ListBoxModel options, final Baseline baseline) {
        options.add(getDisplayName(baseline), baseline.name());
    }
}
