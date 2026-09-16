package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.echarts.LabeledTreeMapNode;

import java.util.List;
import java.util.NavigableSet;
import java.util.Set;

import org.kohsuke.stapler.bind.JavaScriptMethod;
import hudson.model.ModelObject;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;

/**
 * Server side model that provides the data for the "Hierarchy" tab of the coverage details view. Shows a single tree
 * map chart that visualizes the coverage hierarchy (packages, files) for one metric at a time. Which metric is shown
 * can be switched inline on the client side, as can the two coloring thresholds (the value from which a result is
 * considered fully green, and the value from which it is considered fully red); see the corresponding
 * {@code hierarchy-model.js}. This model simply exposes the available metrics and delegates the actual tree map
 * lookup to the owning {@link CoverageViewModel}. The layout of the associated view is defined in the corresponding
 * jelly view 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class TreeMapModel implements ModelObject {
    private final CoverageViewModel parent;

    TreeMapModel(final CoverageViewModel parent) {
        this.parent = parent;
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverageHierarchyModel_displayName();
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
     * Returns the metrics that can be visualized in the tree map, i.e., the metrics the metric selector offers.
     *
     * @return the available metrics
     */
    public NavigableSet<Metric> getTreeMetrics() {
        return parent.getTreeMetrics();
    }

    /**
     * Returns whether the given metric uses the same green/red thresholds as the Overview tab's gauge cards (see
     * {@link OverviewModel#isCardMetric}), rather than its own per-metric thresholds. Used by the metric
     * selector's client script ({@code treemap-model.js}) to decide which local storage entry to read and write
     * for the currently selected metric, so a threshold change made here (or on the Overview tab, or picked up by
     * the build-summary widget) stays in sync everywhere it applies. Metrics that are not on a 0-100 coverage
     * scale (e.g. Complexity, LOC) are not affected and keep their own, metric-specific thresholds.
     *
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if the metric shares the Overview tab's thresholds
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean sharesOverviewThresholds(final Metric metric) {
        return OverviewModel.isCardMetric(metric);
    }

    /**
     * Returns the set of Jenkins color IDs whose current (theme-dependent) hex values the client should look up and
     * pass back via {@link #setJenkinsColors(String)}, so that the red/yellow/green anchor colors used for the
     * threshold-based coloring follow the currently active Jenkins theme.
     *
     * @return the available color IDs
     */
    @JavaScriptMethod
    @SuppressWarnings("unused")
    public Set<String> getJenkinsColorIDs() {
        return parent.getJenkinsColorIDs();
    }

    /**
     * Sets the Jenkins colors to be used for the red/yellow/green anchor colors of the threshold-based coloring.
     *
     * @param colors
     *         the dynamically loaded Jenkins colors as a JSON string, see {@link #getJenkinsColorIDs()}
     */
    @JavaScriptMethod
    @SuppressWarnings("unused")
    public void setJenkinsColors(final String colors) {
        parent.setJenkinsColors(colors);
    }

    /**
     * Returns the root of the tree of nodes for the ECharts treemap, colored by linearly interpolating between red
     * and green using the two given thresholds.
     *
     * @param coverageMetric
     *         the used coverage metric (line, branch, instruction, mutation, ...)
     * @param greenThreshold
     *         the value from which on a result is considered fully green (best)
     * @param redThreshold
     *         the value from which on a result is considered fully red (worst)
     *
     * @return the tree of nodes for the ECharts treemap
     */
    @JavaScriptMethod
    @SuppressWarnings("unused")
    public LabeledTreeMapNode getThresholdCoverageTree(final String coverageMetric,
            final double greenThreshold, final double redThreshold) {
        return parent.getThresholdCoverageTree(coverageMetric, greenThreshold, redThreshold);
    }

    /**
     * Returns the minimum and maximum value of the given metric across all files. Used by the client to initialize
     * sensible default green/red threshold values.
     *
     * @param coverageMetric
     *         the used coverage metric (line, branch, instruction, mutation, ...)
     *
     * @return a list with exactly two elements: the minimum (index 0) and the maximum (index 1) value
     */
    @JavaScriptMethod
    @SuppressWarnings("unused")
    public List<Double> getMetricValueRange(final String coverageMetric) {
        return parent.getMetricValueRange(coverageMetric);
    }
}
