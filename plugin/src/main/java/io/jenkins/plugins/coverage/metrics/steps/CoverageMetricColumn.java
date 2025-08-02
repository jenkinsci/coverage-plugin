package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;
import java.util.Optional;

import io.jenkins.plugins.coverage.metrics.CoverageAppearanceGlobalConfiguration;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.verb.POST;
import org.jenkinsci.Symbol;
import hudson.Extension;
import hudson.Functions;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.ListBoxModel;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import jenkins.model.Jenkins;

import io.jenkins.plugins.coverage.metrics.color.ColorProvider;
import io.jenkins.plugins.coverage.metrics.color.ColorProvider.DisplayColors;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.util.JenkinsFacade;

/**
 * Dashboard column model which represents coverage metrics of different coverage types.
 *
 * @author Florian Orendi
 */
public class CoverageMetricColumn extends ListViewColumn {
    private static final ElementFormatter FORMATTER = new ElementFormatter();

    private String columnName = Messages.Coverage_Column();
    private Metric metric = Metric.LINE;
    private Baseline baseline = Baseline.PROJECT;

    /**
     * Creates a new column.
     */
    @DataBoundConstructor
    public CoverageMetricColumn() {
        super();
    }

    public ElementFormatter getFormatter() {
        return FORMATTER;
    }

    /**
     * Sets the display name of the column.
     *
     * @param columnName
     *         the human-readable name of the column
     */
    @DataBoundSetter
    public void setColumnName(final String columnName) {
        this.columnName = columnName;
    }

    public String getColumnName() {
        return columnName;
    }

    /**
     * Sets the baseline of the values that will be shown.
     *
     * @param baseline
     *         the baseline to use
     */
    @DataBoundSetter
    public void setBaseline(final Baseline baseline) {
        this.baseline = baseline;
    }

    public Baseline getBaseline() {
        return baseline;
    }

    /**
     * Sets the metric of the values that will be shown.
     *
     * @param metric
     *         the metric to use
     */
    @DataBoundSetter
    public void setMetric(final Metric metric) {
        this.metric = metric;
    }

    public Metric getMetric() {
        return metric;
    }

    /**
     * Returns all available values for the specified baseline.
     *
     * @param job
     *         the job in the current row
     *
     * @return the available values
     */
    // Called by jelly view
    public List<Value> getAllValues(final Job<?, ?> job) {
        return findAction(job).map(a -> a.getAllValues(baseline)).orElse(List.of());
    }

    /**
     * Returns a formatted and localized String representation of the specified value (without metric).
     *
     * @param value
     *         the value to format
     *
     * @return the value formatted as a string
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String formatMetric(final Value value) {
        return FORMATTER.getDisplayName(value.getMetric());
    }

    /**
     * Returns a formatted and localized String representation of the specified value (without metric).
     *
     * @param value
     *         the value to format
     *
     * @return the value formatted as a string
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String formatValue(final Value value) {
        return FORMATTER.formatValue(value);
    }

    /**
     * Provides a text which represents the coverage percentage of the selected coverage type and metric.
     *
     * @param job
     *         the job in the current row
     *
     * @return the coverage text
     */
    public String getCoverageText(final Job<?, ?> job) {
        Optional<Value> coverageValue = getCoverageValue(job);
        if (coverageValue.isPresent()) {
            return FORMATTER.format(coverageValue.get(), Functions.getCurrentLocale());
        }
        return Messages.Coverage_Not_Available();
    }

    /**
     * Provides the coverage value of the selected coverage type and metric.
     *
     * @param job
     *         the job in the current row
     *
     * @return the coverage percentage
     */
    public Optional<Value> getCoverageValue(final Job<?, ?> job) {
        return findAction(job).flatMap(action -> action.getStatistics().getValue(getBaseline(), metric));
    }

    private static Optional<CoverageBuildAction> findAction(final Job<?, ?> job) {
        var lastCompletedBuild = job.getLastCompletedBuild();
        if (lastCompletedBuild == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(lastCompletedBuild.getAction(CoverageBuildAction.class));
    }

    /**
     * Provides the line color for representing the passed coverage value.
     *
     * @param job
     *         the job in the current row
     * @param coverage
     *         The coverage value as percentage
     *
     * @return the line color as hex string
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    public DisplayColors getDisplayColors(final Job<?, ?> job, final Optional<? extends Value> coverage) {
        if (coverage.isPresent() && hasCoverageAction(job)) {
            return FORMATTER.getDisplayColors(baseline, coverage.get());
        }
        return ColorProvider.DEFAULT_COLOR;
    }

    /**
     * Provides the relative URL which can be used for accessing the coverage report.
     *
     * @param job
     *         the job in the current row
     *
     * @return the relative URL or an empty string when there is no matching URL
     */
    public String getRelativeCoverageUrl(final Job<?, ?> job) {
        if (hasCoverageAction(job)) {
            var action = job.getLastCompletedBuild().getAction(CoverageBuildAction.class);
            return action.getUrlName() + "/" + baseline.getUrl();
        }
        return "";
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
        return FORMATTER.getBackgroundColorFillPercentage(percentage);
    }

    /**
     * Checks whether a {@link CoverageBuildAction} exists within the completed build.
     *
     * @param job
     *         the job in the current row
     *
     * @return {@code true} whether the action exists, else {@code false}
     */
    private boolean hasCoverageAction(final Job<?, ?> job) {
        Run<?, ?> lastCompletedBuild = job.getLastCompletedBuild();
        return lastCompletedBuild != null && !lastCompletedBuild.getActions(CoverageBuildAction.class).isEmpty();
    }

    /**
     * Descriptor of the column.
     */
    @Extension(optional = true)
    @Symbol("coverageTotalsColumn")
    public static class CoverageMetricColumnDescriptor extends ListViewColumnDescriptor {
        /**
         * Creates a new descriptor.
         */
        @SuppressWarnings("unused") // Required for Jenkins Extensions
        public CoverageMetricColumnDescriptor() {
            this(new JenkinsFacade());
        }

        @VisibleForTesting
        CoverageMetricColumnDescriptor(final JenkinsFacade jenkins) {
            super();

            this.jenkins = jenkins;
        }

        private final JenkinsFacade jenkins;

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.Coverage_Column();
        }

        @Override
        public boolean shownByDefault() {
            return CoverageAppearanceGlobalConfiguration.get().isEnableColumnByDefault();
        }

        /**
         * Returns a model with all {@link Metric metrics} that can be used in quality gates.
         *
         * @return a model with all {@link Metric metrics}.
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillMetricItems() {
            if (jenkins.hasPermission(Jenkins.READ)) {
                return FORMATTER.getMetricItems();
            }
            return new ListBoxModel();
        }

        /**
         * Returns a model with all {@link Metric metrics} that can be used in quality gates.
         *
         * @return a model with all {@link Metric metrics}.
         */
        @POST
        @SuppressWarnings("unused") // used by Stapler view data binding
        public ListBoxModel doFillBaselineItems() {
            if (jenkins.hasPermission(Jenkins.READ)) {
                return FORMATTER.getBaselineItems();
            }
            return new ListBoxModel();
        }
    }
}
