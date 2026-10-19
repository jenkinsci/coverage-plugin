package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Metric.MetricTendency;
import edu.hm.hafner.coverage.MetricAggregation;
import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import java.util.Arrays;
import java.util.List;
import org.jvnet.localizer.ResourceBundleHolder;

/**
 * Server side model that provides the data for the "Help" tab of the coverage details view. Explains the concepts
 * that are used in the other tabs: the baselines, the aggregations, and the metrics of the report. The tables of this
 * view are created from the model, so the rules that are shown (e.g., the tendency of a metric or the elements that
 * provide the maximum and minimum of a metric) are always consistent with the actual computation. The metrics table
 * shows only the metrics that are part of the report. The layout of the associated view is defined in the
 * corresponding jelly view 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class HelpModel implements ModelObject {
    private static final ResourceBundleHolder MESSAGES = ResourceBundleHolder.get(Messages.class);

    private final CoverageViewModel parent;

    HelpModel(final CoverageViewModel parent) {
        this.parent = parent;
    }

    @Override
    public String getDisplayName() {
        return Messages.HelpModel_displayName();
    }

    public String getId() {
        return parent.getId();
    }

    public Run<?, ?> getObject() {
        return parent.getOwner();
    }

    public RunTab getTab() {
        return new RunTab(parent.getOwner());
    }

    public ElementFormatter getFormatter() {
        return parent.getFormatter();
    }

    /**
     * Returns all baselines.
     *
     * @return the baselines
     */
    public List<Baseline> getBaselines() {
        return Arrays.asList(Baseline.values());
    }

    /**
     * Returns a description of the specified baseline.
     *
     * @param baseline
     *         the baseline to describe
     *
     * @return the localized description
     */
    public String getBaselineDescription(final Baseline baseline) {
        return MESSAGES.format("HelpModel.baseline." + baseline.name());
    }

    /**
     * Returns all aggregations.
     *
     * @return the aggregations
     */
    public List<MetricAggregation> getAggregations() {
        return Arrays.asList(MetricAggregation.values());
    }

    /**
     * Returns a description of the specified aggregation.
     *
     * @param aggregation
     *         the aggregation to describe
     *
     * @return the localized description
     */
    public String getAggregationDescription(final MetricAggregation aggregation) {
        return MESSAGES.format("HelpModel.aggregation." + aggregation.name());
    }

    /**
     * Returns the metrics of the report, i.e., all metrics that provide a value in the coverage tree.
     *
     * @return the metrics of the report
     */
    public List<Metric> getMetrics() {
        return List.copyOf(parent.getNode().getMetrics());
    }

    /**
     * Returns a description of the specified metric.
     *
     * @param metric
     *         the metric to describe
     *
     * @return the localized description
     */
    public String getMetricDescription(final Metric metric) {
        return MESSAGES.format("HelpModel.metric." + metric.name());
    }

    /**
     * Returns the name of the group of the specified metric: percentages or absolute values. Metrics of different
     * groups are shown in separate charts, since they cannot share the same axis.
     *
     * @param metric
     *         the metric
     *
     * @return the localized name of the group
     */
    public String getGroup(final Metric metric) {
        return metric.getUnit().isPercentage()
                ? Messages.HelpModel_group_percentage()
                : Messages.HelpModel_group_absolute();
    }

    /**
     * Returns a description of the tendency of the specified metric.
     *
     * @param metric
     *         the metric
     *
     * @return the localized tendency, e.g. "larger is better"
     */
    public String getTendency(final Metric metric) {
        return metric.getTendency() == MetricTendency.LARGER_IS_BETTER
                ? Messages.HelpModel_tendency_larger()
                : Messages.HelpModel_tendency_smaller();
    }

    /**
     * Returns a description of how the overall value of the specified metric is computed.
     *
     * @param metric
     *         the metric
     *
     * @return the localized description of the computation
     */
    public String getComputation(final Metric metric) {
        return MESSAGES.format(
                "HelpModel.computation." + metric.getComputation().name());
    }

    /**
     * Returns a description of the elements that provide the maximum and minimum of the specified metric.
     *
     * @param metric
     *         the metric
     *
     * @return the localized name of the elements, or a dash if the metric provides no maximum and minimum
     */
    public String getAggregationElements(final Metric metric) {
        if (metric.isContainer()) {
            return "–";
        }
        return switch (metric.getTargetNodeMetric()) {
            case METHOD -> Messages.HelpModel_elements_methods();
            case FILE -> Messages.HelpModel_elements_files();
            default -> Messages.HelpModel_elements_classes();
        };
    }
}
