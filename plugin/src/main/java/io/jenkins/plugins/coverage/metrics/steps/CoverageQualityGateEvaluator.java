package io.jenkins.plugins.coverage.metrics.steps;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.umd.cs.findbugs.annotations.CheckForNull;

import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.coverage.metrics.model.MetricAggregation;
import io.jenkins.plugins.util.QualityGateEvaluator;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.QualityGateStatus;

/**
 * Evaluates a given set of quality gates.
 *
 * @author Johannes Walter
 */
class CoverageQualityGateEvaluator extends QualityGateEvaluator<CoverageQualityGate> {
    private static final ElementFormatter FORMATTER = new ElementFormatter();
    private final CoverageStatistics statistics;
    @CheckForNull
    private final Node rootNode;

    CoverageQualityGateEvaluator(final Collection<? extends CoverageQualityGate> qualityGates,
            final CoverageStatistics statistics) {
        this(qualityGates, statistics, null);
    }

    CoverageQualityGateEvaluator(final Collection<? extends CoverageQualityGate> qualityGates,
            final CoverageStatistics statistics, @CheckForNull final Node rootNode) {
        super(qualityGates);

        this.statistics = statistics;
        this.rootNode = rootNode;
    }

    @Override
    protected void evaluate(final CoverageQualityGate qualityGate, final QualityGateResult result) {
        var baseline = qualityGate.getBaseline();
        var metric = qualityGate.getMetric();
        var aggregation = qualityGate.getAggregation();

        Optional<Value> possibleValue;
        if (MetricAggregation.isSupported(metric) && aggregation != MetricAggregation.TOTAL && rootNode != null) {
            possibleValue = computeAggregatedValue(rootNode, metric, aggregation, baseline);
        }
        else {
            possibleValue = statistics.getValue(baseline, metric);
        }

        if (possibleValue.isPresent()) {
            var actualValue = possibleValue.get();
            var status = actualValue.isOutOfValidRange(
                    qualityGate.getThreshold()) ? qualityGate.getStatus() : QualityGateStatus.PASSED;
            result.add(qualityGate, status, FORMATTER.format(possibleValue.get(), Locale.ENGLISH));
        }
        else {
            result.add(qualityGate, QualityGateStatus.INACTIVE, "n/a");
        }
    }

    /**
     * Computes an aggregated value (maximum or average) for a metric from the node tree.
     *
     * @param node
     *         the root node to compute from
     * @param metric
     *         the metric to compute
     * @param aggregation
     *         the aggregation mode (MAXIMUM or AVERAGE)
     * @param baseline
     *         the baseline (currently only PROJECT is supported for custom aggregation)
     *
     * @return the computed value, or empty if not computable
     */
    private Optional<Value> computeAggregatedValue(final Node node, final Metric metric,
            final MetricAggregation aggregation, final Baseline baseline) {
        if (baseline != Baseline.PROJECT) {
            return statistics.getValue(baseline, metric);
        }

        var allValues = collectLeafValues(node, metric).toList();

        if (allValues.isEmpty()) {
            return Optional.empty();
        }

        if (aggregation == MetricAggregation.MAXIMUM) {
            return allValues.stream().reduce(Value::max);
        }
        else if (aggregation == MetricAggregation.AVERAGE) {
            return computeAverage(allValues);
        }

        return Optional.empty();
    }

    /**
     * Collects all leaf values for a metric from a node tree. For metrics computed at the method level (like
     * complexity), this collects values from all methods. For class-level metrics, it collects from all classes.
     *
     * @param node
     *         the node to start from
     * @param metric
     *         the metric to collect
     *
     * @return a stream of all leaf values
     */
    private Stream<Value> collectLeafValues(final Node node, final Metric metric) {
        Stream<Value> nodeValue = node.getValue(metric).stream();

        Stream<Value> childValues = node.getChildren().stream()
                .flatMap(child -> collectLeafValues(child, metric));

        if (node.getMetric() == Metric.METHOD
                || node.getMetric() == Metric.CLASS) {
            return Stream.concat(nodeValue, childValues);
        }

        var childValuesList = childValues.toList();
        return childValuesList.isEmpty() ? nodeValue : childValuesList.stream();
    }

    /**
     * Computes the average of a list of values. For integer metrics like complexity, this computes the arithmetic
     * mean. For coverage metrics, this computes the average percentage.
     *
     * @param values
     *         the values to average
     *
     * @return the average value, or empty if no values
     */
    private Optional<Value> computeAverage(final List<Value> values) {
        if (values.isEmpty()) {
            return Optional.empty();
        }

        var sum = values.stream().reduce(Value::add);
        if (sum.isEmpty()) {
            return Optional.empty();
        }

        var metric = values.get(0).getMetric();
        var totalValue = sum.get();

        return Optional.of(new Value(metric, totalValue.asDouble() / values.size()));
    }
}
