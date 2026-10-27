package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.steps.HotspotsModel.HistogramData;
import io.jenkins.plugins.coverage.metrics.steps.HotspotsModel.HistogramMetric;
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
    void shouldCountFilesInBuckets() {
        var model = createModel(createModule(
                createFile("None.java", Metric.LINE, 0, 10),
                createFile("Third.java", Metric.LINE, 1, 2),
                createFile("Ninety.java", Metric.LINE, 90, 10),
                createFile("Almost.java", Metric.LINE, 99, 1),
                createFile("High.java", Metric.LINE, 95, 5),
                createFile("Full.java", Metric.LINE, 10, 0)));

        var histogram = readHistogram(model);

        assertThat(histogram.labels()).hasSize(HotspotsModel.BUCKETS);
        assertThat(histogram.labels()).startsWith("0–9%", "10–19%").endsWith("90–99%", "100%");
        assertThat(histogram.metrics()).hasSize(1);

        var line = histogram.metrics().get(0);
        assertThat(line.id()).isEqualTo(Metric.LINE.toTagName());
        assertThat(line.name()).isEqualTo(Metric.LINE.getDisplayName());
        assertThat(line.counts()).containsExactly(1, 0, 0, 1, 0, 0, 0, 0, 0, 3, 1);
    }

    @Test
    void shouldCountEveryFileOnce() {
        var jacoco = readJacocoResult(JACOCO_CODING_STYLE_FILE);

        var line = readHistogram(createModel(jacoco)).metrics().stream()
                .filter(metric -> metric.id().equals(Metric.LINE.toTagName()))
                .findFirst()
                .orElseThrow();

        assertThat(line.counts().stream().mapToInt(Integer::intValue).sum()).isEqualTo((int)
                jacoco.getAllFileNodes().stream()
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

    private static HotspotsModel createModel(final Node node) {
        return createModel(node, mock(Run.class));
    }

    private static HotspotsModel createModel(final Node node, final Run<?, ?> run) {
        return new HotspotsModel(new CoverageViewModel(
                run,
                ID,
                "",
                node,
                ChangeSet.empty(),
                createStatistics(),
                new QualityGateResult(),
                "-",
                new FilteredLog("Errors"),
                Function.identity(),
                Function.identity()));
    }
}
