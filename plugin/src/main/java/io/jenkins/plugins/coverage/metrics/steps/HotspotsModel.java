package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import hudson.model.ModelObject;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.kohsuke.stapler.bind.JavaScriptMethod;
import tools.jackson.databind.ObjectMapper;

/**
 * Server side model for the "Hotspots" tab of the coverage details view. The tab answers the question <em>where</em>
 * the coverage needs attention: it shows the files with the most missed items (lines, branches, mutations), the files
 * that are both complex and poorly tested, and a histogram that shows how the coverage is distributed over all files.
 *
 * @author Ullrich Hafner
 */
public class HotspotsModel implements ModelObject {
    /** The maximum number of files in each ranking. */
    static final int MAX_ENTRIES = 10;

    private static final int BUCKET_WIDTH = 10;
    /** Buckets for 0-9%, 10-19%, ..., 90-99%, plus one extra bucket for files that are fully covered. */
    static final int BUCKETS = 100 / BUCKET_WIDTH + 1;

    private static final List<Metric> RANKED_METRICS = List.of(Metric.LINE, Metric.BRANCH, Metric.MUTATION);

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
     * Returns the rankings of the files with the most missed items. There is one ranking for each of the metrics
     * line, branch, and mutation coverage; metrics without any missed items in any file are omitted.
     *
     * @return the rankings, may be empty
     */
    public List<Ranking> getRankings() {
        return RANKED_METRICS.stream()
                .map(this::createRanking)
                .filter(ranking -> !ranking.mostMissed().isEmpty())
                .toList();
    }

    private Ranking createRanking(final Metric metric) {
        var files = parent.getNode().getAllFileNodes().stream()
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
        return new Ranking(parent.getFormatter().getDisplayName(metric), mostMissed, lowestCoverage);
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
        return parent.getNode().getAllFileNodes().stream()
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
     * Returns the distribution of the coverage over all files, as JSON. For each coverage metric, the number of files
     * is counted that fall into each of the buckets 0-9%, 10-19%, ..., 90-99%, and 100%. Files that do not provide
     * the metric are ignored.
     *
     * @return the histogram data of all metrics (converted to a JSON string)
     */
    @JavaScriptMethod
    @SuppressWarnings("unused") // called by view
    public String getHistogramData() {
        var metrics = TrendChartFactory.COVERAGE_TREND_METRICS.stream()
                .sorted()
                .map(metric -> new HistogramMetric(
                        metric.toTagName(), parent.getFormatter().getDisplayName(metric), countFiles(metric)))
                .filter(histogram -> histogram.counts().stream().anyMatch(count -> count > 0))
                .toList();
        return new ObjectMapper().writeValueAsString(new HistogramData(createLabels(), metrics));
    }

    private List<Integer> countFiles(final Metric metric) {
        var counts = new int[BUCKETS];
        parent.getNode().getAllFileNodes().stream()
                .flatMap(file -> getCoverage(file, metric).stream())
                .forEach(coverage -> counts[getBucket(coverage)]++);
        return Arrays.stream(counts).boxed().toList();
    }

    static int getBucket(final Coverage coverage) {
        if (coverage.getMissed() == 0) {
            return BUCKETS - 1;
        }
        return Math.min(BUCKETS - 2, (int) (coverage.getCovered() * 100L / coverage.getTotal()) / BUCKET_WIDTH);
    }

    private static List<String> createLabels() {
        var labels = new ArrayList<>(IntStream.range(0, BUCKETS - 1)
                .mapToObj(bucket -> bucket * BUCKET_WIDTH + "–" + (bucket * BUCKET_WIDTH + BUCKET_WIDTH - 1) + "%")
                .toList());
        labels.add("100%");
        return labels;
    }

    private static Optional<Coverage> getCoverage(final FileNode file, final Metric metric) {
        return file.getValue(metric)
                .filter(Coverage.class::isInstance)
                .map(Coverage.class::cast)
                .filter(coverage -> coverage.getTotal() > 0);
    }

    /**
     * The files with gaps for one metric. Small files with a very low coverage have only few missed items, so they
     * would never show up in a ranking by missed items. Therefore, the files are ranked twice.
     *
     * @param name
     *         the display name of the metric
     * @param mostMissed
     *         the files with the most missed items (descending)
     * @param lowestCoverage
     *         the files with the lowest coverage (ascending)
     */
    public record Ranking(String name, List<MissedEntry> mostMissed, List<MissedEntry> lowestCoverage) {}

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
     * The coverage distribution of all metrics.
     *
     * @param labels
     *         the labels of the buckets
     * @param metrics
     *         the number of files in each bucket, for each metric
     */
    public record HistogramData(List<String> labels, List<HistogramMetric> metrics) {}

    /**
     * The coverage distribution of one metric.
     *
     * @param id
     *         the ID of the metric
     * @param name
     *         the display name of the metric
     * @param counts
     *         the number of files in each bucket
     */
    public record HistogramMetric(String id, String name, List<Integer> counts) {}
}
