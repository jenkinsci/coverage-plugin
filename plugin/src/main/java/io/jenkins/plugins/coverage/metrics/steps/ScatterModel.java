package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import hudson.model.ModelObject;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.charts.TreeMapNodeConverter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import tools.jackson.databind.ObjectMapper;

/**
 * Server side model that provides the data for the "Scatter Plot" tab of the coverage details view. The tab shows one
 * point per file of the current build: the X and Y coordinates are the values of two metrics that can be freely
 * selected on the client side (see the corresponding {@code scatter-model.js}). This makes it easy to spot outliers,
 * e.g., files with a high complexity and a low coverage. Since ECharts options for scatter plots are not provided by
 * the Java side, this model only delivers the raw values of all metrics for all files - the chart itself is created in
 * the browser.
 *
 * @author Ullrich Hafner
 */
public class ScatterModel implements ModelObject {
    static final String COVERAGE_GROUP = "coverage";
    static final String SOFTWARE_GROUP = "software";

    private final CoverageViewModel parent;

    ScatterModel(final CoverageViewModel parent) {
        this.parent = parent;
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverageScatterModel_displayName();
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

    /**
     * Returns all metrics that can be selected for the axes of the scatter plot: all non-container metrics that have
     * a value for at least one file. Percentages are listed before absolute values.
     *
     * @return the available metrics
     */
    public List<Metric> getMetrics() {
        var files = parent.getNode().getAllFileNodes();
        return parent.getNode().getValueMetrics().stream()
                .filter(metric -> !metric.isContainer())
                .filter(metric ->
                        files.stream().anyMatch(file -> file.getValue(metric).isPresent()))
                .sorted(Comparator.comparing(metric -> !isPercentage(metric)))
                .toList();
    }

    private static boolean isPercentage(final Metric metric) {
        return TrendChartFactory.COVERAGE_TREND_METRICS.contains(metric);
    }

    /**
     * Returns the values of all selectable metrics for all files of the current build, as JSON. The data is
     * column-oriented: for every metric, the values are stored by the index of the file. Files that do not provide
     * the metric have no entry.
     *
     * @return the values of all metrics (converted to a JSON string)
     */
    @JavaScriptMethod
    @SuppressWarnings("unused") // called by view
    public String getScatterData() {
        var files = parent.getNode().getAllFileNodes();
        var metrics = getMetrics().stream()
                .map(metric -> new ScatterMetric(
                        metric.toTagName(),
                        metric.getDisplayName(),
                        isPercentage(metric) ? COVERAGE_GROUP : SOFTWARE_GROUP,
                        isPercentage(metric) ? "%" : "",
                        getValues(files, metric)))
                .toList();
        var data = new ScatterData(
                files.stream().map(FileNode::getName).toList(),
                files.stream().map(FileNode::getRelativePath).toList(),
                files.stream().map(file -> file.getRelativePath().hashCode()).toList(),
                metrics);
        return new ObjectMapper().writeValueAsString(data);
    }

    private Map<Integer, Double> getValues(final List<FileNode> files, final Metric metric) {
        var values = new LinkedHashMap<Integer, Double>();
        for (int index = 0; index < files.size(); index++) {
            var value = getValue(files.get(index), metric);
            if (value.isPresent()) {
                values.put(index, value.get());
            }
        }
        return values;
    }

    private Optional<Double> getValue(final FileNode file, final Metric metric) {
        return file.getValue(metric)
                .map(TreeMapNodeConverter::asDisplayValue)
                .filter(Double::isFinite)
                .map(number -> BigDecimal.valueOf(number)
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue());
    }

    /**
     * The values of all metrics for all files.
     *
     * @param names
     *         the file names
     * @param paths
     *         the relative paths of the files
     * @param hashes
     *         the hash codes of the paths, used to link to the source code view
     * @param metrics
     *         the metric values
     */
    public record ScatterData(
            List<String> names, List<String> paths, List<Integer> hashes, List<ScatterMetric> metrics) {}

    /**
     * The values of one metric: one value per file.
     *
     * @param id
     *         the ID of the metric
     * @param name
     *         the display name of the metric
     * @param group
     *         either {@code coverage} (percentages) or {@code software} (absolute values)
     * @param unit
     *         the unit that is appended to each value
     * @param values
     *         the values by the index of the file, files that do not provide the metric have no entry
     */
    public record ScatterMetric(String id, String name, String group, String unit, Map<Integer, Double> values) {}
}
