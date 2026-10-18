package io.jenkins.plugins.coverage.metrics.charts;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.LabeledTreeMapNode;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.color.ColorProvider;
import io.jenkins.plugins.coverage.metrics.color.ColorProviderFactory;
import io.jenkins.plugins.coverage.metrics.color.CoverageLevel;
import io.jenkins.plugins.coverage.metrics.color.ThresholdColorProvider;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Tests the class {@link TreeMapNodeConverter}.
 *
 * @author Ullrich Hafner
 */
class TreeMapNodeConverterTest extends AbstractCoverageTest {
    private static final ColorProvider COLOR_PROVIDER = ColorProviderFactory.createDefaultColorProvider();
    private static final String PREFIX = "../steps/";

    @Test
    void shouldConvertCodingStyleToTree() {
        var tree = readJacocoResult(PREFIX + JACOCO_CODING_STYLE_FILE);

        var root = new TreeMapNodeConverter().toTreeChartModel(tree, Metric.LINE, COLOR_PROVIDER);
        assertThat(root.getName()).isEqualTo("Java coding style");

        var overallCoverage = String.valueOf(JACOCO_CODING_STYLE_TOTAL);
        assertThat(root.getValue()).contains(overallCoverage);

        var overallCoveragePercentage = 100.0 * JACOCO_CODING_STYLE_COVERED / JACOCO_CODING_STYLE_TOTAL;
        assertThat(root.getItemStyle().getColor()).isEqualTo(getNodeColorAsRGBHex(overallCoveragePercentage));

        assertThat(root.getChildren()).hasSize(1).element(0).satisfies(node -> {
            assertThat(node.getName()).isEqualTo("edu.hm.hafner.util");
            assertThat(node.getValue()).contains(overallCoverage);
            assertThat(root.getItemStyle().getColor()).isEqualTo(getNodeColorAsRGBHex(overallCoveragePercentage));
        });
    }

    @Test
    void shouldScalePercentageMetricsToHundred() {
        assertThat(TreeMapNodeConverter.asDisplayValue(new Value(Metric.COHESION, 33, 100)))
                .isEqualTo(33.0);
        assertThat(TreeMapNodeConverter.asDisplayValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 33)))
                .isEqualTo(33.0);
        assertThat(TreeMapNodeConverter.asDisplayValue(new Value(Metric.LOC, 1234)))
                .isEqualTo(1234.0);
    }

    @Test
    void shouldReadBranchCoverage() {
        var tree = readJacocoResult(PREFIX + JACOCO_ANALYSIS_MODEL_FILE);

        var root = new TreeMapNodeConverter().toTreeChartModel(tree, Metric.BRANCH, COLOR_PROVIDER);

        var nodes = aggregateChildren(root);
        nodes.stream()
                .filter(node -> node.getName().endsWith(".java"))
                .forEach(node -> assertThat(node.getValue()).hasSize(2));
    }

    @Test
    void shouldColorTreeUsingConfiguredThresholds() {
        var tree = readJacocoResult(PREFIX + JACOCO_CODING_STYLE_FILE);

        var greenThreshold = 100.0;
        var redThreshold = 0.0;
        var root = new TreeMapNodeConverter()
                .toThresholdTreeChartModel(tree, Metric.LINE, greenThreshold, redThreshold, COLOR_PROVIDER);

        assertThat(root.getName()).isEqualTo("Java coding style");

        var overallCoveragePercentage = 100.0 * JACOCO_CODING_STYLE_COVERED / JACOCO_CODING_STYLE_TOTAL;
        var expectedFillColor = ThresholdColorProvider.getFillColorAsHex(
                overallCoveragePercentage, Metric.LINE.getTendency(), greenThreshold, redThreshold, COLOR_PROVIDER);
        assertThat(root.getItemStyle().getColor()).isEqualTo(expectedFillColor);

        var overallCoverage = String.valueOf(JACOCO_CODING_STYLE_TOTAL);
        assertThat(root.getValue()).contains(overallCoverage);
    }

    @Test
    void shouldBuildTheSameTreeShapeRegardlessOfColoringStrategy() {
        var tree = readJacocoResult(PREFIX + JACOCO_ANALYSIS_MODEL_FILE);

        var plain = new TreeMapNodeConverter().toTreeChartModel(tree, Metric.BRANCH, COLOR_PROVIDER);
        var threshold =
                new TreeMapNodeConverter().toThresholdTreeChartModel(tree, Metric.BRANCH, 100.0, 0.0, COLOR_PROVIDER);

        // same node count and same size ("value") independent of how the nodes end up being colored
        assertThat(aggregateChildren(threshold)).hasSameSizeAs(aggregateChildren(plain));
        assertThat(threshold.getValue()).isEqualTo(plain.getValue());
    }

    @Test
    void shouldClampEveryNodeToFullyGreenWhenTheGreenThresholdIsUnreachablyLow() {
        var tree = readJacocoResult(PREFIX + JACOCO_CODING_STYLE_FILE);

        // a green threshold below the worst possible value (0%) means every node, however covered, is fully green
        var root = new TreeMapNodeConverter().toThresholdTreeChartModel(tree, Metric.LINE, 0.0, -1.0, COLOR_PROVIDER);

        var fullyGreen =
                ThresholdColorProvider.getFillColorAsHex(100.0, Metric.LINE.getTendency(), 0.0, -1.0, COLOR_PROVIDER);
        assertThat(root.getItemStyle().getColor()).isEqualTo(fullyGreen);
        aggregateChildren(root)
                .forEach(node -> assertThat(node.getItemStyle().getColor()).isEqualTo(fullyGreen));
    }

    @Test
    void shouldColorTreeUsingTheSelectedAggregation() {
        var tree = createComplexityTree();

        var root = new TreeMapNodeConverter()
                .toThresholdTreeChartModel(
                        tree, Metric.CYCLOMATIC_COMPLEXITY, MetricAggregation.MAXIMUM, 1.0, 20.0, COLOR_PROVIDER);

        assertThat(root.getValue()).first().isEqualTo("16");
        assertThat(root.getItemStyle().getColor()).isEqualTo(getComplexityColor(10.0));
        assertThat(root.getValue().get(1))
                .endsWith("(" + new ElementFormatter().getDisplayName(MetricAggregation.MAXIMUM) + ")");

        assertThat(findNode(root, "A.java")).satisfies(file -> {
            assertThat(file.getValue()).first().isEqualTo("12");
            assertThat(file.getItemStyle().getColor()).isEqualTo(getComplexityColor(10.0));
        });
        assertThat(findNode(root, "B.java")).satisfies(file -> {
            assertThat(file.getValue()).first().isEqualTo("4");
            assertThat(file.getItemStyle().getColor()).isEqualTo(getComplexityColor(4.0));
        });

        var minimum = new TreeMapNodeConverter()
                .toThresholdTreeChartModel(
                        tree, Metric.CYCLOMATIC_COMPLEXITY, MetricAggregation.MINIMUM, 1.0, 20.0, COLOR_PROVIDER);
        assertThat(minimum.getValue()).first().isEqualTo("16");
        assertThat(minimum.getItemStyle().getColor()).isEqualTo(getComplexityColor(2.0));
    }

    @Test
    void shouldUseOverallValueForUnaggregatedTree() {
        var tree = createComplexityTree();

        var root = new TreeMapNodeConverter()
                .toThresholdTreeChartModel(
                        tree, Metric.CYCLOMATIC_COMPLEXITY, MetricAggregation.OVERALL, 1.0, 20.0, COLOR_PROVIDER);

        assertThat(root.getValue()).first().isEqualTo("16");
        assertThat(root.getItemStyle().getColor()).isEqualTo(getComplexityColor(16.0));
        assertThat(root.getValue().get(1)).doesNotEndWith(")");
        assertThat(root)
                .usingRecursiveComparison()
                .isEqualTo(new TreeMapNodeConverter()
                        .toThresholdTreeChartModel(tree, Metric.CYCLOMATIC_COMPLEXITY, 1.0, 20.0, COLOR_PROVIDER));
    }

    @Test
    void shouldUseOverallValueForContainerMetrics() {
        var tree = readJacocoResult(PREFIX + JACOCO_CODING_STYLE_FILE);

        var root = new TreeMapNodeConverter()
                .toThresholdTreeChartModel(tree, Metric.FILE, MetricAggregation.MAXIMUM, 100.0, 0.0, COLOR_PROVIDER);

        assertThat(root.getValue()).isNotEmpty();
        assertThat(root)
                .usingRecursiveComparison()
                .isEqualTo(new TreeMapNodeConverter()
                        .toThresholdTreeChartModel(tree, Metric.FILE, 100.0, 0.0, COLOR_PROVIDER));
    }

    private String getComplexityColor(final double value) {
        return ThresholdColorProvider.getFillColorAsHex(
                value, Metric.CYCLOMATIC_COMPLEXITY.getTendency(), 1.0, 20.0, COLOR_PROVIDER);
    }

    private LabeledTreeMapNode findNode(final LabeledTreeMapNode root, final String name) {
        return aggregateChildren(root).stream()
                .filter(node -> node.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private ModuleNode createComplexityTree() {
        var root = new ModuleNode("module");
        var packageNode = root.createPackageNode("pkg");

        var a = new FileNode("A.java", "pkg/A.java");
        packageNode.addChild(a);
        var classA = a.createClassNode("A");
        classA.createMethodNode("m1", "()V").addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 2));
        classA.createMethodNode("m2", "()V").addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 10));

        var b = new FileNode("B.java", "pkg/B.java");
        packageNode.addChild(b);
        b.createClassNode("B").createMethodNode("m3", "()V").addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 4));

        return root;
    }

    private List<LabeledTreeMapNode> aggregateChildren(final LabeledTreeMapNode root) {
        var children = root.getChildren();
        var subChildren = children.stream()
                .map(this::aggregateChildren)
                .flatMap(List::stream)
                .collect(Collectors.toList());
        subChildren.addAll(children);
        return subChildren;
    }

    /**
     * Gets the matching fill color for the coverage percentage.
     *
     * @param coveredPercentage
     *         The coverage percentage
     *
     * @return the fill color as a hex string
     */
    private String getNodeColorAsRGBHex(final Double coveredPercentage) {
        return CoverageLevel.getDisplayColorsOfCoverageLevel(coveredPercentage, COLOR_PROVIDER)
                .getFillColorAsRGBHex();
    }
}
