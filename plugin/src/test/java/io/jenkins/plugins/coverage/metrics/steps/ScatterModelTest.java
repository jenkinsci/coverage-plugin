package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.*;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FileDeltas;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.steps.ScatterModel.ScatterData;
import io.jenkins.plugins.coverage.metrics.steps.ScatterModel.ScatterMetric;
import io.jenkins.plugins.util.QualityGateResult;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests the class {@link ScatterModel}.
 *
 * @author Ullrich Hafner
 */
class ScatterModelTest extends AbstractCoverageTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ID = "id";

    private static final String FIRST = "First.java";
    private static final String SECOND = "Second.java";
    private static final String THIRD = "Third.java";

    @Test
    void shouldProvideDisplayNameAndOwner() {
        Run<?, ?> run = mock(Run.class);
        var model = createModel(new ModuleNode("empty"), run);

        assertThat(model.getDisplayName()).isEqualTo(Messages.CoverageScatterModel_displayName());
        assertThat(model.getId()).isEqualTo(ID);
        assertThat(model.getObject()).isSameAs(run);
        assertThat(model.getTab()).isNotNull();
    }

    @Test
    void shouldProvideEmptyDataIfThereAreNoFiles() {
        var model = createModel(new ModuleNode("empty"));

        assertThat(model.getMetrics()).isEmpty();

        var data = readData(model);
        assertThat(data.names()).isEmpty();
        assertThat(data.paths()).isEmpty();
        assertThat(data.hashes()).isEmpty();
        assertThat(data.metrics()).isEmpty();
    }

    @Test
    void shouldListPercentagesBeforeAbsoluteValues() {
        var model = createModel(readJacocoResult(JACOCO_CODING_STYLE_FILE));

        var metrics = model.getMetrics();
        var percentages = metrics.stream()
                .takeWhile(metric -> metric.getUnit().isPercentage())
                .toList();
        var absoluteValues = metrics.subList(percentages.size(), metrics.size());

        assertThat(percentages).contains(Metric.LINE, Metric.BRANCH);
        assertThat(absoluteValues).contains(Metric.CYCLOMATIC_COMPLEXITY);
        assertThat(absoluteValues).noneMatch(metric -> metric.getUnit().isPercentage());
        assertThat(metrics).noneMatch(Metric::isContainer);
    }

    @Test
    void shouldProvideOneEntryPerFile() {
        var node = readJacocoResult(JACOCO_CODING_STYLE_FILE);
        var files = node.getAllFileNodes();
        var data = readData(createModel(node));

        assertThat(data.names()).hasSameSizeAs(files);
        assertThat(data.paths()).hasSameSizeAs(files);
        assertThat(data.hashes()).hasSameSizeAs(files);

        for (int index = 0; index < files.size(); index++) {
            var file = files.get(index);
            assertThat(data.names()).element(index).isEqualTo(file.getName());
            assertThat(data.paths()).element(index).isEqualTo(file.getRelativePath());
            assertThat(data.hashes())
                    .element(index)
                    .isEqualTo(file.getRelativePath().hashCode());
        }
    }

    @Test
    void shouldProvideRoundedPercentagesByFileIndex() {
        var node = readJacocoResult(JACOCO_CODING_STYLE_FILE);
        var data = readData(createModel(node));

        var line = findMetric(data, Metric.LINE);
        assertThat(line.name()).isEqualTo(Metric.LINE.getDisplayName());
        assertThat(line.group()).isEqualTo(ScatterModel.COVERAGE_GROUP);
        assertThat(line.unit()).isEqualTo("%");

        var files = node.getAllFileNodes();
        assertThat(line.values()).isNotEmpty();
        for (int index = 0; index < files.size(); index++) {
            var value = files.get(index).getValue(Metric.LINE);
            if (value.isPresent()) {
                assertThat(line.values()).containsEntry(index, round(value.get().asDouble()));
            } else {
                assertThat(line.values()).doesNotContainKey(index);
            }
        }
        assertThat(line.values().values()).allSatisfy(value -> assertThat(value).isBetween(0.0, 100.0));
    }

    @Test
    void shouldRoundPercentagesToTwoDecimals() {
        var file = new FileNode(FIRST, "src/" + FIRST);
        file.addValue(new CoverageBuilder()
                .withMetric(Metric.LINE)
                .withCovered(1)
                .withMissed(2)
                .build());
        var data = readData(createModel(createModule(file)));

        assertThat(findMetric(data, Metric.LINE).values()).containsOnly(entry(0, 33.33));
    }

    @Test
    void shouldGroupAbsoluteValuesAsSoftwareMetrics() {
        var data = readData(createModel(createModuleWithMixedFiles()));

        var complexity = findMetric(data, Metric.CYCLOMATIC_COMPLEXITY);
        assertThat(complexity.name()).isEqualTo(Metric.CYCLOMATIC_COMPLEXITY.getDisplayName());
        assertThat(complexity.group()).isEqualTo(ScatterModel.SOFTWARE_GROUP);
        assertThat(complexity.unit()).isEmpty();
    }

    @Test
    void shouldOmitFilesThatDoNotProvideTheMetric() {
        var data = readData(createModel(createModuleWithMixedFiles()));

        assertThat(data.names()).containsExactlyInAnyOrder(FIRST, SECOND, THIRD);

        var complexity = findMetric(data, Metric.CYCLOMATIC_COMPLEXITY);
        assertThat(complexity.values())
                .containsOnly(entry(indexOf(data, FIRST), 5.0), entry(indexOf(data, THIRD), 7.0))
                .doesNotContainKey(indexOf(data, SECOND));

        var loc = findMetric(data, Metric.LOC);
        assertThat(loc.values()).containsOnly(entry(indexOf(data, FIRST), 100.0), entry(indexOf(data, SECOND), 50.0));
    }

    @Test
    void shouldScaleFractionsToPercentages() {
        var model = createModel(createModuleWithMixedFiles());
        var data = readData(model);

        assertThat(model.getMetrics()).contains(Metric.COHESION);

        var cohesion = findMetric(data, Metric.COHESION);
        assertThat(cohesion.group()).isEqualTo(ScatterModel.COVERAGE_GROUP);
        assertThat(cohesion.unit()).isEqualTo("%");
        assertThat(cohesion.values()).containsOnly(entry(indexOf(data, THIRD), 33.0));
    }

    @Test
    void shouldIgnoreMetricsThatNoFileProvides() {
        var module = createModuleWithMixedFiles();
        module.addValue(new Value(Metric.NPATH_COMPLEXITY, 42));

        var model = createModel(module);

        assertThat(model.getMetrics()).doesNotContain(Metric.NPATH_COMPLEXITY);
        assertThat(readData(model).metrics())
                .extracting(ScatterMetric::id)
                .doesNotContain(Metric.NPATH_COMPLEXITY.toTagName());
    }

    @Test
    void shouldAggregateValuesOfMethodsAndClasses() {
        var model = createModel(createModuleWithMethods());

        var overall = readData(model);
        assertThat(findMetric(overall, Metric.CYCLOMATIC_COMPLEXITY)).satisfies(complexity -> {
            assertThat(complexity.name()).isEqualTo(Metric.CYCLOMATIC_COMPLEXITY.getDisplayName());
            assertThat(complexity.values())
                    .containsOnly(entry(indexOf(overall, FIRST), 12.0), entry(indexOf(overall, SECOND), 4.0));
        });

        var maximum = readData(model, MetricAggregation.MAXIMUM);
        assertThat(maximum.names()).isEqualTo(overall.names());
        assertThat(findMetric(maximum, Metric.CYCLOMATIC_COMPLEXITY)).satisfies(complexity -> {
            assertThat(complexity.name())
                    .isEqualTo(Metric.CYCLOMATIC_COMPLEXITY.getDisplayName() + " ("
                            + model.getFormatter().getDisplayName(MetricAggregation.MAXIMUM) + ")");
            assertThat(complexity.values())
                    .containsOnly(entry(indexOf(maximum, FIRST), 10.0), entry(indexOf(maximum, SECOND), 4.0));
        });

        var minimum = readData(model, MetricAggregation.MINIMUM);
        assertThat(findMetric(minimum, Metric.CYCLOMATIC_COMPLEXITY).values())
                .containsOnly(entry(indexOf(minimum, FIRST), 2.0), entry(indexOf(minimum, SECOND), 4.0));
    }

    @Test
    void shouldProvideSameCoverageForAllAggregations() {
        var node = readJacocoResult(JACOCO_CODING_STYLE_FILE);
        var model = createModel(node);

        var overall = findMetric(readData(model), Metric.LINE);
        for (MetricAggregation aggregation : MetricAggregation.values()) {
            assertThat(findMetric(readData(model, aggregation), Metric.LINE)).isEqualTo(overall);
        }
    }

    private static ModuleNode createModuleWithMethods() {
        var first = new FileNode(FIRST, "src/" + FIRST);
        var firstClass = first.createClassNode("First");
        firstClass.createMethodNode("one", "()V").addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 2));
        firstClass.createMethodNode("two", "()V").addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 10));

        var second = new FileNode(SECOND, "src/" + SECOND);
        second.createClassNode("Second")
                .createMethodNode("three", "()V")
                .addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 4));

        return createModule(first, second);
    }

    private static ModuleNode createModuleWithMixedFiles() {
        var first = new FileNode(FIRST, "src/" + FIRST);
        first.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 5));
        first.addValue(new Value(Metric.LOC, 100));

        var second = new FileNode(SECOND, "src/" + SECOND);
        second.addValue(new Value(Metric.LOC, 50));

        var third = new FileNode(THIRD, "src/" + THIRD);
        third.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 7));
        third.addValue(new Value(Metric.COHESION, 33, 100));

        return createModule(first, second, third);
    }

    private static ModuleNode createModule(final FileNode... files) {
        var module = new ModuleNode("module");
        for (FileNode file : files) {
            module.addChild(file);
        }
        return module;
    }

    private static int indexOf(final ScatterData data, final String fileName) {
        return data.names().indexOf(fileName);
    }

    private static ScatterMetric findMetric(final ScatterData data, final Metric metric) {
        return data.metrics().stream()
                .filter(scatterMetric -> scatterMetric.id().equals(metric.toTagName()))
                .findFirst()
                .orElseThrow();
    }

    private static double round(final double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static ScatterData readData(final ScatterModel model) {
        return readData(model, MetricAggregation.OVERALL);
    }

    private static ScatterData readData(final ScatterModel model, final MetricAggregation aggregation) {
        return MAPPER.readValue(model.getScatterData(aggregation.name()), ScatterData.class);
    }

    private static ScatterModel createModel(final Node node) {
        return createModel(node, mock(Run.class));
    }

    private static ScatterModel createModel(final Node node, final Run<?, ?> run) {
        return new ScatterModel(new CoverageViewModel(
                run,
                ID,
                "",
                node,
                ChangeSet.empty(),
                FileDeltas.empty(),
                createStatistics(),
                new QualityGateResult(),
                "-",
                new FilteredLog("Errors"),
                Function.identity(),
                Function.identity()));
    }
}
