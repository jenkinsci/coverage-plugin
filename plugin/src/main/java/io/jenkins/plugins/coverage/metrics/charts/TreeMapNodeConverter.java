package io.jenkins.plugins.coverage.metrics.charts;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.ItemStyle;
import edu.hm.hafner.echarts.Label;
import edu.hm.hafner.echarts.LabeledTreeMapNode;
import edu.hm.hafner.echarts.TreeMapNode;

import java.util.Optional;

import hudson.Functions;

import io.jenkins.plugins.coverage.metrics.color.ColorProvider;
import io.jenkins.plugins.coverage.metrics.color.ColorProvider.DisplayColors;
import io.jenkins.plugins.coverage.metrics.color.ColorProviderFactory;
import io.jenkins.plugins.coverage.metrics.color.CoverageLevel;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.echarts.JenkinsPalette;

/**
 * Converts a tree of {@link Node coverage nodes} to a corresponding tree of
 * {@link TreeMapNode ECharts tree map nodes}. The value of the tree map nodes is based on a given metric.
 *
 * @author Ullrich Hafner
 */
public class TreeMapNodeConverter {
    private static final ElementFormatter FORMATTER = new ElementFormatter();

    /**
     * Converts a coverage tree of {@link Node nodes} to an ECharts tree map of {@link TreeMapNode}.
     *
     * @param node
     *         The root node of the tree to be converted
     * @param metric
     *         The coverage metric that should be represented (line and branch coverage are available)
     * @param colorProvider
     *         Provides the colors to be used for highlighting the tree nodes
     *
     * @return the converted tree map representation
     */
    public LabeledTreeMapNode toTreeChartModel(final Node node, final Metric metric, final ColorProvider colorProvider) {
        var tree = mergePackages(node);
        var root = toTreeMapNode(tree, metric, colorProvider)
                .orElse(new LabeledTreeMapNode(getId(node), node.getName()));
        for (LabeledTreeMapNode child : root.getChildren()) {
            child.collapseEmptyPackages();
        }

        return root;
    }

    private String getId(final Node node) {
        var id = node.getName();
        if (node.isRoot()) {
            return id;
        }
        else {
            return getId(node.getParent()) + '/' + id;
        }
    }

    private Node mergePackages(final Node root) {
        var node = skipEmptyModules(root);
        if (node instanceof ModuleNode) {
            var copy = (ModuleNode) node.copyTree();

            copy.splitPackages();

            return copy;
        }
        return root;
    }

    private Node skipEmptyModules(final Node root) {
        var node = root;
        while (node.hasChildren()
                && node.getChildren().size() == 1
                && isTopLevel(node.getChildren().get(0))) {
            node = node.getChildren().get(0);
        }
        return node;
    }

    private boolean isTopLevel(final Node child) {
        return child.getMetric() == Metric.MODULE || child.getMetric() == Metric.CONTAINER;
    }

    private Optional<LabeledTreeMapNode> toTreeMapNode(final Node node, final Metric metric,
            final ColorProvider colorProvider) {
        var value = node.getValue(metric);
        if (value.isPresent()) {
            var rootValue = value.get();
            if (rootValue instanceof Coverage coverage) {
                return Optional.of(createCoverageTree(coverage, colorProvider, node, metric));
            }
            return Optional.of(createMetricsTree(rootValue, node, metric));
        }

        return Optional.empty();
    }

    private LabeledTreeMapNode createCoverageTree(final Coverage coverage, final ColorProvider colorProvider,
            final Node node, final Metric metric) {
        DisplayColors colors = CoverageLevel.getDisplayColorsOfCoverageLevel(coverage.asDouble(), colorProvider);

        var lineColor = colors.getLineColorAsRGBHex();
        var fillColor = colors.getFillColorAsRGBHex();

        var label = new Label(true, lineColor);

        if (node instanceof FileNode) { // stop recursion and create a colored leaf
            return createTreeNode(coverage, node, new ItemStyle(fillColor), label);
        }

        var boldFill = new ItemStyle(fillColor, fillColor, 4);
        var treeNode = createTreeNode(coverage, node, boldFill, label);

        node.getChildren().stream()
                .map(n -> toTreeMapNode(n, metric, colorProvider))
                .flatMap(Optional::stream)
                .forEach(treeNode::insertNode); // recursively build the tree

        return treeNode;
    }

    private LabeledTreeMapNode createTreeNode(final Coverage coverage, final Node node,
            final ItemStyle itemStyle, final Label label) {
        return new LabeledTreeMapNode(getId(node), node.getName(), itemStyle, label, label,
                String.valueOf(coverage.getTotal()), FORMATTER.getTooltip(coverage));
    }

    private LabeledTreeMapNode createMetricsTree(final Value value, final Node node,
            final Metric metric) {
        var label = new Label(true, JenkinsPalette.BLACK.normal());

        String fillColor = metric == Metric.TESTS ? JenkinsPalette.GREEN.light() : JenkinsPalette.ORANGE.normal();
        if (node instanceof FileNode) {
            return createValueNode(value, node, new ItemStyle(fillColor), label);
        }

        var treeNode = createValueNode(value, node,
                new ItemStyle(fillColor, fillColor, 4), label);

        node.getChildren().stream()
                .map(n -> toTreeMapNode(n, metric, ColorProviderFactory.createDefaultColorProvider()))
                .flatMap(Optional::stream)
                .forEach(treeNode::insertNode);

        return treeNode;
    }

    private LabeledTreeMapNode createValueNode(final Value value, final Node node,
            final ItemStyle itemStyle, final Label label) {
        return new LabeledTreeMapNode(getId(node), node.getName(), itemStyle, label, label,
                value.asText(Functions.getCurrentLocale()), FORMATTER.getTooltip(value));
    }
}
