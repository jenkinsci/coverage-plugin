package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.MethodNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Metric.MetricTendency;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import hudson.model.ModelObject;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import tools.jackson.databind.ObjectMapper;

/**
 * Server side model for the "Hotspots" tab of the coverage details view. The tab answers the question <em>where</em>
 * the code needs attention: it shows the files with the most missed items (lines, branches, mutations), the files
 * that are both complex and poorly tested, the files with the worst values of the software metrics (e.g., the
 * cyclomatic complexity), and a histogram that shows how the values of a selected metric are distributed over all
 * files.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling", "PMD.CouplingBetweenObjects"})
public class HotspotsModel implements ModelObject {
    /** The maximum number of files in each ranking. */
    static final int MAX_ENTRIES = 10;

    private static final int BUCKET_WIDTH = 10;
    /** Buckets for 0-9%, 10-19%, ..., 90-99%, plus one extra bucket for files that are fully covered. */
    static final int BUCKETS = 100 / BUCKET_WIDTH + 1;

    /** The ID of the histogram that uses the overall value of each file. */
    static final String OVERALL = "overall";
    /** The ID of the histogram that uses the worst value of the elements of each file. */
    static final String WORST = "worst";

    private static final String NO_ELEMENT = "-";
    /** The best possible value of a percentage (100%). */
    private static final double PERFECT_PERCENTAGE = 1.0;

    private final CoverageViewModel parent;

    HotspotsModel(final CoverageViewModel parent) {
        this.parent = parent;
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverageHotspotsModel_displayName();
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
     * Returns the rankings of the files with the most missed items. There is one ranking for each coverage metric
     * (e.g., line, branch, or mutation coverage); metrics without any missed items in any file are omitted.
     *
     * @return the rankings, may be empty
     */
    public List<Ranking> getRankings() {
        return getCoverageMetrics().stream()
                .map(this::createRanking)
                .filter(ranking -> !ranking.mostMissed().isEmpty())
                .toList();
    }

    private Ranking createRanking(final Metric metric) {
        var files = getFiles()
                .flatMap(file -> getCoverage(file, metric).stream()
                        .filter(coverage -> coverage.getMissed() > 0)
                        .map(coverage -> createEntry(file, coverage)))
                .toList();
        var mostMissed = files.stream()
                .sorted(Comparator.comparingInt(MissedEntry::missed).reversed().thenComparing(MissedEntry::path))
                .limit(MAX_ENTRIES)
                .toList();
        var lowestCoverage = files.stream()
                .sorted(Comparator.comparingDouble(MissedEntry::coveredShare)
                        .thenComparing(
                                Comparator.comparingInt(MissedEntry::missed).reversed())
                        .thenComparing(MissedEntry::path))
                .limit(MAX_ENTRIES)
                .toList();
        return new Ranking(
                metric.toTagName(), parent.getFormatter().getDisplayName(metric), mostMissed, lowestCoverage);
    }

    private MissedEntry createEntry(final FileNode file, final Coverage coverage) {
        return new MissedEntry(
                file.getName(),
                file.getRelativePath(),
                file.getRelativePath().hashCode(),
                coverage.getMissed(),
                coverage.getTotal(),
                parent.getFormatter().format(coverage));
    }

    /**
     * Returns the files that are complex and poorly covered at the same time. The risk of a file is the cyclomatic
     * complexity of the file weighted by the share of lines that are not covered, so a complex file with few missed
     * lines ranks lower than an equally complex file without any tests.
     *
     * @return the files with the highest risk, may be empty
     */
    public List<RiskEntry> getRiskRanking() {
        return getFiles()
                .flatMap(file -> createRiskEntry(file).stream())
                .sorted(Comparator.comparingLong(RiskEntry::risk).reversed().thenComparing(RiskEntry::path))
                .limit(MAX_ENTRIES)
                .toList();
    }

    private Optional<RiskEntry> createRiskEntry(final FileNode file) {
        var complexity = file.getValue(Metric.CYCLOMATIC_COMPLEXITY);
        var coverage = getCoverage(file, Metric.LINE);
        if (complexity.isEmpty() || coverage.isEmpty()) {
            return Optional.empty();
        }
        var uncoveredShare =
                (double) coverage.get().getMissed() / coverage.get().getTotal();
        var risk = Math.round(complexity.get().asDouble() * uncoveredShare);
        if (risk <= 0) {
            return Optional.empty();
        }
        return Optional.of(new RiskEntry(
                file.getName(),
                file.getRelativePath(),
                file.getRelativePath().hashCode(),
                Math.round(complexity.get().asDouble()),
                parent.getFormatter().format(coverage.get()),
                risk));
    }

    /**
     * Returns the rankings of the files with the worst values of the software metrics (e.g., the cyclomatic
     * complexity or the class cohesion). There is one ranking for each software metric that is available in the
     * report. The files are ranked twice: by the overall value of the file (e.g., the sum of the complexity of all
     * methods) and by the worst value of the elements of the file (e.g., the complexity of the most complex method).
     * Whether a large or a small value is worse is defined by the {@link Metric#getTendency() tendency} of the metric.
     * Files that already have the best possible value are omitted, as well as metrics without any such files.
     *
     * @return the rankings, may be empty
     */
    public List<MetricRanking> getMetricRankings() {
        return getSoftwareMetrics().stream()
                .map(this::createMetricRanking)
                .filter(ranking -> !ranking.byOverall().isEmpty())
                .toList();
    }

    private MetricRanking createMetricRanking(final Metric metric) {
        var worstAggregation = WorstValuesModel.getWorstAggregation(metric);
        var files = getFiles()
                .flatMap(file -> createFileValues(file, metric, worstAggregation).stream())
                .filter(values -> isHotspot(metric, values.worst()))
                .toList();
        var byOverall = files.stream()
                .sorted(worstFirst(metric, FileValues::overall)
                        .thenComparing(worstFirst(metric, FileValues::worst))
                        .thenComparing(FileValues::path))
                .limit(MAX_ENTRIES)
                .map(this::createMetricEntry)
                .toList();
        var byWorst = files.stream()
                .sorted(worstFirst(metric, FileValues::worst)
                        .thenComparing(worstFirst(metric, FileValues::overall))
                        .thenComparing(FileValues::path))
                .limit(MAX_ENTRIES)
                .map(this::createMetricEntry)
                .toList();
        var formatter = parent.getFormatter();
        return new MetricRanking(
                metric.toTagName(),
                formatter.getDisplayName(metric),
                formatter.getDisplayName(MetricAggregation.OVERALL),
                formatter.getDisplayName(worstAggregation),
                metric.getTendency() == MetricTendency.SMALLER_IS_BETTER,
                metric.getTargetNodeMetric() == Metric.METHOD,
                byOverall,
                byWorst);
    }

    private Optional<FileValues> createFileValues(
            final FileNode file, final Metric metric, final MetricAggregation worstAggregation) {
        var overall = getFileValue(file, metric, MetricAggregation.OVERALL);
        var worst = getFileValue(file, metric, worstAggregation);
        if (overall.isEmpty() || worst.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FileValues(file, overall.get(), worst.get(), findWorstElement(file, metric)));
    }

    /**
     * Returns the value of the specified metric for the specified file. If the file provides no value for the
     * specified aggregation (e.g., a computed metric like the lines of code of a file without classes), then the
     * overall value of the file is used, since the file is the only element that provides the metric.
     *
     * @param file
     *         the file to get the value for
     * @param metric
     *         the metric to get the value for
     * @param aggregation
     *         the aggregation to use
     *
     * @return the value, or an empty result if the file does not provide the metric
     */
    private static Optional<Value> getFileValue(
            final FileNode file, final Metric metric, final MetricAggregation aggregation) {
        return file.getValue(metric, aggregation).or(() -> file.getValue(metric, MetricAggregation.OVERALL));
    }

    /**
     * Returns the name of the element (method or class) of the specified file that has the worst value of the
     * specified metric.
     *
     * @param file
     *         the file to search in
     * @param metric
     *         the metric to evaluate
     *
     * @return the name of the element, or {@link #NO_ELEMENT} if the file has no elements with a value of the metric
     */
    private static String findWorstElement(final FileNode file, final Metric metric) {
        return metric.getTargetNodes(file).stream()
                .flatMap(node -> node.getValue(metric).map(value -> new ElementValue(node, value)).stream())
                .min(worstFirst(metric, ElementValue::value))
                .map(element -> getElementName(element.node()))
                .orElse(NO_ELEMENT);
    }

    private static String getElementName(final Node node) {
        if (node instanceof MethodNode method) {
            return method.getMethodName();
        }
        var name = node.getName();
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /**
     * Returns whether the specified value of a file is a hotspot, i.e., whether the value is not already the best
     * possible value of the metric (zero for metrics where smaller values are better, 100% for percentages where
     * larger values are better).
     *
     * @param metric
     *         the metric of the value
     * @param value
     *         the value to check
     *
     * @return {@code true} if the value is a hotspot, {@code false} otherwise
     */
    private static boolean isHotspot(final Metric metric, final Value value) {
        if (metric.getTendency() == MetricTendency.SMALLER_IS_BETTER) {
            return value.asDouble() > 0;
        }
        return !metric.getUnit().isPercentage() || value.asDouble() < PERFECT_PERCENTAGE;
    }

    /**
     * Creates a comparator that sorts the worst values first: descending for metrics where smaller values are better,
     * ascending otherwise.
     *
     * @param metric
     *         the metric of the values
     * @param valueExtractor
     *         extracts the value to compare
     * @param <T>
     *         the type of the compared elements
     *
     * @return the comparator
     */
    private static <T> Comparator<T> worstFirst(final Metric metric, final Function<T, Value> valueExtractor) {
        Comparator<T> ascending = Comparator.comparingDouble(
                element -> valueExtractor.apply(element).asDouble());
        if (metric.getTendency() == MetricTendency.SMALLER_IS_BETTER) {
            return ascending.reversed();
        }
        return ascending;
    }

    private MetricEntry createMetricEntry(final FileValues values) {
        var file = values.file();
        var formatter = parent.getFormatter();
        return new MetricEntry(
                file.getName(),
                file.getRelativePath(),
                file.getRelativePath().hashCode(),
                formatter.format(values.overall()),
                formatter.format(values.worst()),
                values.element());
    }

    /**
     * Returns the distribution of the values of all metrics over all files, as JSON. For each coverage metric, the
     * number of files is counted that fall into each of the buckets 0-9%, 10-19%, ..., 90-99%, and 100%. For each
     * software metric, two distributions are provided: one for the overall value of each file and one for the worst
     * value of the elements of each file. Percentages (e.g., the class cohesion) use the same buckets as the coverage,
     * the absolute values of all other software metrics are distributed into the buckets that are computed by
     * {@link HistogramBuckets}. Files that do not provide a metric are ignored.
     *
     * @return the histogram data of all metrics (converted to a JSON string)
     */
    @JavaScriptMethod
    @SuppressWarnings("unused") // called by view
    public String getHistogramData() {
        var coverageMetrics = getCoverageMetrics().stream().map(this::createCoverageHistogram);
        var softwareMetrics = getSoftwareMetrics().stream().map(this::createMetricHistogram);
        var metrics = Stream.concat(coverageMetrics, softwareMetrics)
                .filter(HotspotsModel::hasValues)
                .toList();
        return new ObjectMapper().writeValueAsString(new HistogramData(metrics));
    }

    private static boolean hasValues(final HistogramMetric metric) {
        return metric.histograms().stream()
                .map(Histogram::counts)
                .flatMap(List::stream)
                .anyMatch(count -> count > 0);
    }

    private HistogramMetric createCoverageHistogram(final Metric metric) {
        var counts = new int[BUCKETS];
        getFiles()
                .flatMap(file -> getCoverage(file, metric).stream())
                .forEach(coverage -> counts[getBucket(coverage)]++);
        var histogram = new Histogram(
                OVERALL,
                parent.getFormatter().getDisplayName(MetricAggregation.OVERALL),
                createPercentageLabels(),
                toList(counts));
        return new HistogramMetric(
                metric.toTagName(), parent.getFormatter().getDisplayName(metric), true, false, List.of(histogram));
    }

    private HistogramMetric createMetricHistogram(final Metric metric) {
        var histograms = List.of(
                createMetricHistogram(metric, MetricAggregation.OVERALL, OVERALL),
                createMetricHistogram(metric, WorstValuesModel.getWorstAggregation(metric), WORST));
        return new HistogramMetric(
                metric.toTagName(),
                parent.getFormatter().getDisplayName(metric),
                false,
                metric.getTendency() == MetricTendency.SMALLER_IS_BETTER,
                histograms);
    }

    private Histogram createMetricHistogram(final Metric metric, final MetricAggregation aggregation, final String id) {
        var values = getFiles()
                .flatMap(file -> getFileValue(file, metric, aggregation).stream())
                .map(Value::asDouble)
                .toList();
        var name = parent.getFormatter().getDisplayName(aggregation);
        if (metric.getUnit().isPercentage()) {
            var counts = new int[BUCKETS];
            values.forEach(value -> counts[getBucket(value)]++);
            return new Histogram(id, name, createPercentageLabels(), toList(counts));
        }
        var buckets =
                HistogramBuckets.distribute(values.stream().map(Math::round).toList());
        return new Histogram(id, name, buckets.getLabels(), buckets.getCounts());
    }

    static int getBucket(final Coverage coverage) {
        if (coverage.getMissed() == 0) {
            return BUCKETS - 1;
        }
        return Math.min(BUCKETS - 2, (int) (coverage.getCovered() * 100L / coverage.getTotal()) / BUCKET_WIDTH);
    }

    /**
     * Returns the bucket of the specified percentage.
     *
     * @param share
     *         the percentage as a fraction in the interval [0, 1]
     *
     * @return the bucket
     */
    static int getBucket(final double share) {
        if (share >= PERFECT_PERCENTAGE) {
            return BUCKETS - 1;
        }
        return Math.max(0, Math.min(BUCKETS - 2, (int) Math.floor(share * 100) / BUCKET_WIDTH));
    }

    private static List<String> createPercentageLabels() {
        var labels = new ArrayList<>(IntStream.range(0, BUCKETS - 1)
                .mapToObj(bucket -> bucket * BUCKET_WIDTH + "–" + (bucket * BUCKET_WIDTH + BUCKET_WIDTH - 1) + "%")
                .toList());
        labels.add("100%");
        return labels;
    }

    private static List<Integer> toList(final int... counts) {
        return Arrays.stream(counts).boxed().toList();
    }

    /**
     * Returns the coverage metrics that are shown in the Hotspots tab. Each of these metrics provides a distribution
     * and a ranking of the files with the most missed items.
     *
     * @return the coverage metrics, sorted by their natural order
     */
    private static List<Metric> getCoverageMetrics() {
        return TrendChartFactory.COVERAGE_TREND_METRICS.stream()
                .filter(Metric::isCoverage)
                .sorted()
                .toList();
    }

    /**
     * Returns the software metrics (i.e., all metrics that are neither coverage nor container metrics) that are
     * available in the report.
     *
     * @return the software metrics, sorted by their natural order
     */
    private List<Metric> getSoftwareMetrics() {
        return parent.getNode().getMetrics().stream()
                .filter(metric -> !metric.isCoverage() && !metric.isContainer())
                .sorted()
                .toList();
    }

    private Stream<FileNode> getFiles() {
        return parent.getNode().getAllFileNodes().stream();
    }

    private static Optional<Coverage> getCoverage(final FileNode file, final Metric metric) {
        return file.getValue(metric)
                .filter(Coverage.class::isInstance)
                .map(Coverage.class::cast)
                .filter(coverage -> coverage.getTotal() > 0);
    }

    /**
     * The values of a software metric for a file.
     *
     * @param file
     *         the file
     * @param overall
     *         the overall value of the file
     * @param worst
     *         the worst value of the elements of the file
     * @param element
     *         the name of the element with the worst value
     */
    private record FileValues(FileNode file, Value overall, Value worst, String element) {
        String path() {
            return file.getRelativePath();
        }
    }

    /**
     * The value of a software metric for an element (method or class).
     *
     * @param node
     *         the element
     * @param value
     *         the value of the element
     */
    private record ElementValue(Node node, Value value) {}

    /**
     * The files with gaps for one metric. Small files with a very low coverage have only few missed items, so they
     * would never show up in a ranking by missed items. Therefore, the files are ranked twice.
     *
     * @param id
     *         the ID of the metric
     * @param name
     *         the display name of the metric
     * @param mostMissed
     *         the files with the most missed items (descending)
     * @param lowestCoverage
     *         the files with the lowest coverage (ascending)
     */
    public record Ranking(String id, String name, List<MissedEntry> mostMissed, List<MissedEntry> lowestCoverage) {}

    /**
     * A file in a ranking of missed items.
     *
     * @param name
     *         the name of the file
     * @param path
     *         the relative path of the file
     * @param hash
     *         the hash code of the path, used to link to the source code view
     * @param missed
     *         the number of missed items
     * @param total
     *         the total number of items
     * @param coverage
     *         the formatted coverage of the file
     */
    public record MissedEntry(String name, String path, int hash, int missed, int total, String coverage) {
        /**
         * Returns the share of covered items in the range [0, 1].
         *
         * @return the covered share
         */
        double coveredShare() {
            return (double) (total - missed) / total;
        }
    }

    /**
     * A file in the risk ranking.
     *
     * @param name
     *         the name of the file
     * @param path
     *         the relative path of the file
     * @param hash
     *         the hash code of the path, used to link to the source code view
     * @param complexity
     *         the cyclomatic complexity of the file
     * @param coverage
     *         the formatted line coverage of the file
     * @param risk
     *         the complexity weighted by the share of lines that are not covered
     */
    public record RiskEntry(String name, String path, int hash, long complexity, String coverage, long risk) {}

    /**
     * The files with the worst values for one software metric. The files are ranked twice: by the overall value of
     * the file and by the worst value of the elements of the file.
     *
     * @param id
     *         the ID of the metric
     * @param name
     *         the display name of the metric
     * @param overallName
     *         the display name of the overall aggregation
     * @param worstName
     *         the display name of the aggregation that selects the worst value (maximum or minimum)
     * @param smallerIsBetter
     *         determines whether smaller values are better (i.e., the rankings are sorted descending)
     * @param methodMetric
     *         determines whether the metric is provided by methods (otherwise by classes)
     * @param byOverall
     *         the files with the worst overall values
     * @param byWorst
     *         the files with the worst values of their elements
     */
    public record MetricRanking(
            String id,
            String name,
            String overallName,
            String worstName,
            boolean smallerIsBetter,
            boolean methodMetric,
            List<MetricEntry> byOverall,
            List<MetricEntry> byWorst) {}

    /**
     * A file in a ranking of a software metric.
     *
     * @param name
     *         the name of the file
     * @param path
     *         the relative path of the file
     * @param hash
     *         the hash code of the path, used to link to the source code view
     * @param overall
     *         the formatted overall value of the file
     * @param worst
     *         the formatted worst value of the elements of the file
     * @param element
     *         the name of the element (method or class) with the worst value
     */
    public record MetricEntry(String name, String path, int hash, String overall, String worst, String element) {}

    /**
     * The distributions of all metrics.
     *
     * @param metrics
     *         the distributions of each metric
     */
    public record HistogramData(List<HistogramMetric> metrics) {}

    /**
     * The distributions of one metric.
     *
     * @param id
     *         the ID of the metric
     * @param name
     *         the display name of the metric
     * @param coverage
     *         determines whether the metric is a coverage metric (then the bars are colored using the thresholds of
     *         the coverage)
     * @param smallerIsBetter
     *         determines whether smaller values are better (then the first bucket is the best one)
     * @param histograms
     *         the distributions of the metric, one for each supported aggregation
     */
    public record HistogramMetric(
            String id, String name, boolean coverage, boolean smallerIsBetter, List<Histogram> histograms) {}

    /**
     * The distribution of one metric using a specific aggregation of the values of each file.
     *
     * @param id
     *         the ID of the aggregation ({@link HotspotsModel#OVERALL} or {@link HotspotsModel#WORST})
     * @param name
     *         the display name of the aggregation
     * @param labels
     *         the labels of the buckets
     * @param counts
     *         the number of files in each bucket
     */
    public record Histogram(String id, String name, List<String> labels, List<Integer> counts) {}
}
