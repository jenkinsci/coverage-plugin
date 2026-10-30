package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.ClassNode;
import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.MethodNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.steps.HotspotsModel.Histogram;
import io.jenkins.plugins.coverage.metrics.steps.HotspotsModel.HistogramData;
import io.jenkins.plugins.coverage.metrics.steps.HotspotsModel.HistogramMetric;
import io.jenkins.plugins.coverage.metrics.steps.HotspotsModel.MetricEntry;
import io.jenkins.plugins.coverage.metrics.steps.HotspotsModel.MissedEntry;
import io.jenkins.plugins.coverage.metrics.steps.HotspotsModel.RiskEntry;
import io.jenkins.plugins.util.QualityGateResult;
import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests the class {@link HotspotsModel}.
 *
 * @author Ullrich Hafner
 */
class HotspotsModelTest extends AbstractCoverageTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ID = "id";

    @Test
    void shouldProvideDisplayNameAndOwner() {
        Run<?, ?> run = mock(Run.class);
        var model = createModel(new ModuleNode("empty"), run);

        assertThat(model.getDisplayName()).isEqualTo(Messages.CoverageHotspotsModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(run);
        assertThat(model.getTab()).isNotNull();
    }

    @Test
    void shouldProvideNothingIfThereAreNoFiles() {
        var model = createModel(new ModuleNode("empty"));

        assertThat(model.getRankings()).isEmpty();
        assertThat(model.getRiskRanking()).isEmpty();
        assertThat(model.getMetricRankings()).isEmpty();
        assertThat(readHistogram(model).metrics()).isEmpty();
    }

    @Test
    void shouldRankFilesByMissedItems() {
        var model = createModel(createModule(
                createFile("Small.java", Metric.LINE, 9, 1),
                createFile("Large.java", Metric.LINE, 50, 30),
                createFile("Medium.java", Metric.LINE, 20, 10)));

        var rankings = model.getRankings();
        assertThat(rankings).hasSize(1);

        var ranking = rankings.get(0);
        assertThat(ranking.id()).isEqualTo(Metric.LINE.toTagName());
        assertThat(ranking.name()).isEqualTo(Metric.LINE.getDisplayName());
        assertThat(ranking.mostMissed())
                .extracting(MissedEntry::name)
                .containsExactly("Large.java", "Medium.java", "Small.java");
        assertThat(ranking.mostMissed()).extracting(MissedEntry::missed).containsExactly(30, 10, 1);
        assertThat(ranking.mostMissed().get(0).total()).isEqualTo(80);
        assertThat(ranking.mostMissed().get(0).path()).isEqualTo("src/Large.java");
        assertThat(ranking.mostMissed().get(0).hash()).isEqualTo("src/Large.java".hashCode());
    }

    @Test
    void shouldAlsoRankFilesByLowestCoverage() {
        var model = createModel(createModule(
                createFile("Large.java", Metric.LINE, 50, 30),
                createFile("Tiny.java", Metric.LINE, 1, 9),
                createFile("Small.java", Metric.LINE, 9, 1),
                createFile("Medium.java", Metric.LINE, 5, 5)));

        var ranking = model.getRankings().get(0);

        assertThat(ranking.lowestCoverage())
                .extracting(MissedEntry::name)
                .containsExactly("Tiny.java", "Medium.java", "Large.java", "Small.java");
        assertThat(ranking.mostMissed())
                .extracting(MissedEntry::name)
                .containsExactly("Large.java", "Tiny.java", "Medium.java", "Small.java");
    }

    @Test
    void shouldCreateOneRankingPerMetric() {
        var file = createFile("First.java", Metric.LINE, 1, 2);
        file.addValue(new CoverageBuilder()
                .withMetric(Metric.BRANCH)
                .withCovered(3)
                .withMissed(4)
                .build());

        var rankings = createModel(createModule(file)).getRankings();

        assertThat(rankings)
                .extracting(HotspotsModel.Ranking::name)
                .containsExactly(Metric.LINE.getDisplayName(), Metric.BRANCH.getDisplayName());
    }

    @Test
    void shouldCreateRankingForEveryCoverageMetric() {
        var file = createFile("Mutated.java", Metric.TEST_STRENGTH, 3, 1);
        file.addValue(new CoverageBuilder()
                .withMetric(Metric.INSTRUCTION)
                .withCovered(5)
                .withMissed(6)
                .build());

        var rankings = createModel(createModule(file)).getRankings();

        assertThat(rankings)
                .extracting(HotspotsModel.Ranking::id)
                .containsExactly(Metric.INSTRUCTION.toTagName(), Metric.TEST_STRENGTH.toTagName());
    }

    @Test
    void shouldIgnoreFilesWithoutMissedItems() {
        var model = createModel(createModule(
                createFile("Covered.java", Metric.LINE, 10, 0), createFile("Missed.java", Metric.LINE, 10, 1)));

        assertThat(model.getRankings().get(0).mostMissed())
                .extracting(MissedEntry::name)
                .containsExactly("Missed.java");
    }

    @Test
    void shouldOmitRankingsWithoutMissedItems() {
        var model = createModel(createModule(createFile("Covered.java", Metric.LINE, 10, 0)));

        assertThat(model.getRankings()).isEmpty();
    }

    @Test
    void shouldLimitRankingsToMaximumNumberOfEntries() {
        var files = IntStream.rangeClosed(1, HotspotsModel.MAX_ENTRIES + 2)
                .mapToObj(index -> createFile("File" + index + ".java", Metric.LINE, 1, index))
                .toArray(FileNode[]::new);

        var ranking = createModel(createModule(files)).getRankings().get(0);

        assertThat(ranking.mostMissed()).hasSize(HotspotsModel.MAX_ENTRIES);
        assertThat(ranking.lowestCoverage()).hasSize(HotspotsModel.MAX_ENTRIES);
        assertThat(ranking.mostMissed().get(0).missed()).isEqualTo(HotspotsModel.MAX_ENTRIES + 2);
    }

    @Test
    void shouldRankFilesByComplexityWeightedWithUncoveredLines() {
        var halfCovered = createFile("Half.java", Metric.LINE, 5, 5);
        halfCovered.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 30));
        var untested = createFile("Untested.java", Metric.LINE, 0, 10);
        untested.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 10));
        var covered = createFile("Covered.java", Metric.LINE, 10, 0);
        covered.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 50));
        var withoutComplexity = createFile("Simple.java", Metric.LINE, 0, 10);

        var ranking = createModel(createModule(untested, covered, withoutComplexity, halfCovered))
                .getRiskRanking();

        assertThat(ranking).extracting(RiskEntry::name).containsExactly("Half.java", "Untested.java");
        assertThat(ranking).extracting(RiskEntry::risk).containsExactly(15L, 10L);
        assertThat(ranking).extracting(RiskEntry::complexity).containsExactly(30L, 10L);
        assertThat(ranking.get(0).path()).isEqualTo("src/Half.java");
        assertThat(ranking.get(0).hash()).isEqualTo("src/Half.java".hashCode());
        assertThat(ranking.get(0).coverage()).isNotEmpty();
    }

    @Test
    void shouldIgnoreFilesWithoutLineCoverageInRiskRanking() {
        var file = new FileNode("Complex.java", "src/Complex.java");
        file.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 30));

        assertThat(createModel(createModule(file)).getRiskRanking()).isEmpty();
    }

    @Test
    void shouldRankFilesByOverallAndWorstComplexity() {
        var model = createModel(createModule(
                createFileWithMethods("Many.java", Metric.CYCLOMATIC_COMPLEXITY, 5, 5, 5, 5),
                createFileWithMethods("Monster.java", Metric.CYCLOMATIC_COMPLEXITY, 1, 15),
                createFileWithMethods("Simple.java", Metric.CYCLOMATIC_COMPLEXITY, 2)));

        assertThat(model.getMetricRankings()).hasSize(1);

        var ranking = model.getMetricRankings().get(0);
        assertThat(ranking.id()).isEqualTo(Metric.CYCLOMATIC_COMPLEXITY.toTagName());
        assertThat(ranking.name()).isEqualTo(Metric.CYCLOMATIC_COMPLEXITY.getDisplayName());
        assertThat(ranking.smallerIsBetter()).isTrue();
        assertThat(ranking.methodMetric()).isTrue();
        assertThat(ranking.overallName())
                .isEqualTo(io.jenkins.plugins.coverage.metrics.model.Messages.Aggregation_OVERALL());
        assertThat(ranking.worstName())
                .isEqualTo(io.jenkins.plugins.coverage.metrics.model.Messages.Aggregation_MAXIMUM());

        assertThat(ranking.byOverall())
                .extracting(MetricEntry::name)
                .containsExactly("Many.java", "Monster.java", "Simple.java");
        assertThat(ranking.byOverall()).extracting(MetricEntry::overall).containsExactly("20", "16", "2");
        assertThat(ranking.byWorst())
                .extracting(MetricEntry::name)
                .containsExactly("Monster.java", "Many.java", "Simple.java");
        assertThat(ranking.byWorst()).extracting(MetricEntry::worst).containsExactly("15", "5", "2");

        var monster = ranking.byWorst().get(0);
        assertThat(monster.element()).isEqualTo("method1");
        assertThat(monster.path()).isEqualTo("src/Monster.java");
        assertThat(monster.hash()).isEqualTo("src/Monster.java".hashCode());
    }

    @Test
    void shouldRankFilesWithLowestCohesionFirst() {
        var model = createModel(createModule(
                createFileWithClass("High.java", new Value(Metric.COHESION, 9, 10)),
                createFileWithClass("Perfect.java", new Value(Metric.COHESION, 1, 1)),
                createFileWithClass("Low.java", new Value(Metric.COHESION, 1, 10))));

        var ranking = model.getMetricRankings().get(0);

        assertThat(ranking.smallerIsBetter()).isFalse();
        assertThat(ranking.methodMetric()).isFalse();
        assertThat(ranking.worstName())
                .isEqualTo(io.jenkins.plugins.coverage.metrics.model.Messages.Aggregation_MINIMUM());
        assertThat(ranking.byWorst()).extracting(MetricEntry::name).containsExactly("Low.java", "High.java");
        assertThat(ranking.byWorst()).extracting(MetricEntry::element).containsExactly("Low", "High");
    }

    @Test
    void shouldOmitFilesAndMetricsWithBestValues() {
        var model = createModel(createModule(createFileWithMethods("Empty.java", Metric.NPATH_COMPLEXITY, 0, 0)));

        assertThat(model.getMetricRankings()).isEmpty();
    }

    @Test
    void shouldUseFileValueIfThereAreNoElements() {
        var file = new FileNode("Flat.java", "src/Flat.java");
        file.addValue(new Value(Metric.FAN_OUT, 7));

        var ranking = createModel(createModule(file)).getMetricRankings().get(0);

        assertThat(ranking.byWorst()).extracting(MetricEntry::worst).containsExactly("7");
        assertThat(ranking.byWorst()).extracting(MetricEntry::element).containsExactly("-");
    }

    @Test
    void shouldLimitMetricRankingsToMaximumNumberOfEntries() {
        var files = IntStream.rangeClosed(1, HotspotsModel.MAX_ENTRIES + 2)
                .mapToObj(index -> createFileWithMethods("File" + index + ".java", Metric.CYCLOMATIC_COMPLEXITY, index))
                .toArray(FileNode[]::new);

        var ranking = createModel(createModule(files)).getMetricRankings().get(0);

        assertThat(ranking.byOverall()).hasSize(HotspotsModel.MAX_ENTRIES);
        assertThat(ranking.byWorst()).hasSize(HotspotsModel.MAX_ENTRIES);
        assertThat(ranking.byOverall().get(0).name()).isEqualTo("File12.java");
    }

    @Test
    void shouldCountFilesInBuckets() {
        var model = createModel(createModule(
                createFile("None.java", Metric.LINE, 0, 10),
                createFile("Third.java", Metric.LINE, 1, 2),
                createFile("Ninety.java", Metric.LINE, 90, 10),
                createFile("Almost.java", Metric.LINE, 99, 1),
                createFile("High.java", Metric.LINE, 95, 5),
                createFile("Full.java", Metric.LINE, 10, 0)));

        var line = findHistogramMetric(readHistogram(model), Metric.LINE);
        assertThat(line.name()).isEqualTo(Metric.LINE.getDisplayName());
        assertThat(line.coverage()).isTrue();
        assertThat(line.histograms()).hasSize(1);

        var histogram = line.histograms().get(0);
        assertThat(histogram.id()).isEqualTo(HotspotsModel.OVERALL);
        assertThat(histogram.labels()).hasSize(HotspotsModel.BUCKETS);
        assertThat(histogram.labels()).startsWith("0–9%", "10–19%").endsWith("90–99%", "100%");
        assertThat(histogram.counts()).containsExactly(1, 0, 0, 1, 0, 0, 0, 0, 0, 3, 1);
    }

    @Test
    void shouldProvideOverallAndWorstHistogramForSoftwareMetrics() {
        var model = createModel(createModule(
                createFileWithMethods("Many.java", Metric.CYCLOMATIC_COMPLEXITY, 1, 1, 1),
                createFileWithMethods("Monster.java", Metric.CYCLOMATIC_COMPLEXITY, 1, 4),
                createFileWithMethods("Simple.java", Metric.CYCLOMATIC_COMPLEXITY, 2)));

        var complexity = findHistogramMetric(readHistogram(model), Metric.CYCLOMATIC_COMPLEXITY);
        assertThat(complexity.coverage()).isFalse();
        assertThat(complexity.smallerIsBetter()).isTrue();
        assertThat(complexity.histograms())
                .extracting(Histogram::id)
                .containsExactly(HotspotsModel.OVERALL, HotspotsModel.WORST);
        assertThat(complexity.histograms())
                .extracting(Histogram::name)
                .containsExactly(
                        io.jenkins.plugins.coverage.metrics.model.Messages.Aggregation_OVERALL(),
                        io.jenkins.plugins.coverage.metrics.model.Messages.Aggregation_MAXIMUM());

        var overall = complexity.histograms().get(0);
        assertThat(overall.labels()).containsExactly("2", "3", "4", "5");
        assertThat(overall.counts()).containsExactly(1, 1, 0, 1);

        var worst = complexity.histograms().get(1);
        assertThat(worst.labels()).containsExactly("1", "2", "3", "4");
        assertThat(worst.counts()).containsExactly(1, 1, 0, 1);
    }

    @Test
    void shouldUsePercentageBucketsForPercentageMetrics() {
        var model = createModel(createModule(
                createFileWithClass("Low.java", new Value(Metric.COHESION, 1, 10)),
                createFileWithClass("Perfect.java", new Value(Metric.COHESION, 1, 1))));

        var cohesion = findHistogramMetric(readHistogram(model), Metric.COHESION);
        assertThat(cohesion.smallerIsBetter()).isFalse();

        var overall = cohesion.histograms().get(0);
        assertThat(overall.labels()).hasSize(HotspotsModel.BUCKETS).endsWith("100%");
        assertThat(overall.counts()).containsExactly(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 1);
    }

    @Test
    void shouldCountEveryFileOnce() {
        var jacoco = readJacocoResult(JACOCO_CODING_STYLE_FILE);

        var line = findHistogramMetric(readHistogram(createModel(jacoco)), Metric.LINE);

        assertThat(line.histograms().get(0).counts().stream()
                        .mapToInt(Integer::intValue)
                        .sum())
                .isEqualTo((int) jacoco.getAllFileNodes().stream()
                        .flatMap(file -> file.getValue(Metric.LINE).stream())
                        .filter(value -> value instanceof Coverage coverage && coverage.getTotal() > 0)
                        .count());
    }

    @Test
    void shouldOmitMetricsWithoutValues() {
        var histogram = readHistogram(createModel(createModule(new FileNode("Empty.java", "src/Empty.java"))));

        assertThat(histogram.metrics()).isEmpty();
    }

    @Test
    void shouldIgnoreCoverageWithoutItems() {
        var histogram = readHistogram(createModel(createModule(createFile("None.java", Metric.LINE, 0, 0))));

        assertThat(histogram.metrics()).extracting(HistogramMetric::id).doesNotContain(Metric.LINE.toTagName());
    }

    @Test
    void shouldAssignBucketsByRoundingDown() {
        assertThat(HotspotsModel.getBucket(createCoverage(0, 1))).isZero();
        assertThat(HotspotsModel.getBucket(createCoverage(9, 91))).isZero();
        assertThat(HotspotsModel.getBucket(createCoverage(10, 90))).isEqualTo(1);
        assertThat(HotspotsModel.getBucket(createCoverage(999, 1))).isEqualTo(HotspotsModel.BUCKETS - 2);
        assertThat(HotspotsModel.getBucket(createCoverage(1, 0))).isEqualTo(HotspotsModel.BUCKETS - 1);
    }

    @Test
    void shouldAssignPercentagesToBuckets() {
        assertThat(HotspotsModel.getBucket(0.0)).isZero();
        assertThat(HotspotsModel.getBucket(0.099)).isZero();
        assertThat(HotspotsModel.getBucket(0.1)).isEqualTo(1);
        assertThat(HotspotsModel.getBucket(0.999)).isEqualTo(HotspotsModel.BUCKETS - 2);
        assertThat(HotspotsModel.getBucket(1.0)).isEqualTo(HotspotsModel.BUCKETS - 1);
    }

    private static Coverage createCoverage(final int covered, final int missed) {
        return new CoverageBuilder()
                .withMetric(Metric.LINE)
                .withCovered(covered)
                .withMissed(missed)
                .build();
    }

    private static FileNode createFile(final String name, final Metric metric, final int covered, final int missed) {
        var file = new FileNode(name, "src/" + name);
        file.addValue(new CoverageBuilder()
                .withMetric(metric)
                .withCovered(covered)
                .withMissed(missed)
                .build());
        return file;
    }

    /**
     * Creates a file with a single class that contains one method for each of the specified values.
     *
     * @param name
     *         the name of the file
     * @param metric
     *         the metric of the values
     * @param values
     *         the values of the methods: the methods are named method0, method1, ...
     *
     * @return the file
     */
    private static FileNode createFileWithMethods(final String name, final Metric metric, final int... values) {
        var file = new FileNode(name, "src/" + name);
        var classNode = new ClassNode(name.replace(".java", ""));
        file.addChild(classNode);
        for (int index = 0; index < values.length; index++) {
            var method = new MethodNode("method" + index, "()V", index + 1);
            method.addValue(new Value(metric, values[index]));
            classNode.addChild(method);
        }
        return file;
    }

    /**
     * Creates a file with a single class that provides the specified value.
     *
     * @param name
     *         the name of the file
     * @param value
     *         the value of the class
     *
     * @return the file
     */
    private static FileNode createFileWithClass(final String name, final Value value) {
        var file = new FileNode(name, "src/" + name);
        var classNode = new ClassNode("edu.hm." + name.replace(".java", ""));
        classNode.addValue(value);
        file.addChild(classNode);
        return file;
    }

    private static ModuleNode createModule(final FileNode... files) {
        var module = new ModuleNode("module");
        for (FileNode file : List.of(files)) {
            module.addChild(file);
        }
        return module;
    }

    private static HistogramData readHistogram(final HotspotsModel model) {
        return MAPPER.readValue(model.getHistogramData(), HistogramData.class);
    }

    /**
     * Returns the distribution of the specified metric.
     *
     * @param histogram
     *         the distributions of all metrics
     * @param metric
     *         the metric to find
     *
     * @return the distribution of the metric
     */
    private static HistogramMetric findHistogramMetric(final HistogramData histogram, final Metric metric) {
        return histogram.metrics().stream()
                .filter(candidate -> candidate.id().equals(metric.toTagName()))
                .findFirst()
                .orElseThrow();
    }

    private static HotspotsModel createModel(final Node node) {
        return createModel(node, mock(Run.class));
    }

    private static HotspotsModel createModel(final Node node, final Run<?, ?> run) {
        return new HotspotsModel(new CoverageViewModel(
                run,
                ID,
                "",
                node,
                createStatistics(),
                new QualityGateResult(),
                "-",
                new FilteredLog("Errors"),
                Function.identity(),
                Function.identity()));
    }
}
