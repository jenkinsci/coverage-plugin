package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileDeltas;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import hudson.Functions;
import io.jenkins.plugins.coverage.metrics.color.ColorProvider;
import io.jenkins.plugins.datatables.DetailedCell;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A base class for coverage table models that handle the changes to a result of a reference build.
 */
abstract class ChangesTableModel extends CoverageTableModel {
    private final Node changeRoot;

    ChangesTableModel(
            final String id,
            final Node root,
            final ChangeSet changes,
            final FileDeltas deltas,
            final Node changeRoot,
            final RowRenderer renderer,
            final ColorProvider colorProvider) {
        super(id, root, changes, deltas, renderer, colorProvider);

        this.changeRoot = changeRoot;
    }

    @Override
    List<CoverageRow> createRows() {
        Locale browserLocale = Functions.getCurrentLocale();
        return changeRoot.getAllFileNodes().stream()
                .map(file -> createRow(file, browserLocale))
                .collect(Collectors.toList());
    }

    abstract CoverageRow createRow(FileNode file, Locale browserLocale);

    FileNode getOriginalNode(final FileNode fileNode) {
        return getRoot().getAllFileNodes().stream()
                .filter(node -> node.getRelativePath().equals(fileNode.getRelativePath())
                        && node.getName().equals(fileNode.getName()))
                .findFirst()
                .orElse(fileNode); // return this as fallback to prevent exceptions
    }

    /**
     * UI row model for the rows of a table that show the changes.
     */
    static class ChangesRow extends CoverageRow {
        private final FileNode originalFile;

        ChangesRow(
                final FileNode originalFile,
                final FileNode changedFileNode,
                final ChangeSet changes,
                final FileDeltas deltas,
                final Locale browserLocale,
                final RowRenderer renderer,
                final ColorProvider colorProvider) {
            super(changedFileNode, changes, deltas, browserLocale, renderer, colorProvider);

            this.originalFile = originalFile;
        }

        FileNode getOriginalFile() {
            return originalFile;
        }

        @Override
        public DetailedCell<?> getLineCoverageDelta() {
            return createColoredModifiedLinesCoverageDeltaColumn(Metric.LINE);
        }

        @Override
        public DetailedCell<?> getBranchCoverageDelta() {
            return createColoredModifiedLinesCoverageDeltaColumn(Metric.BRANCH);
        }

        @Override
        public DetailedCell<?> getMutationCoverageDelta() {
            return createColoredModifiedLinesCoverageDeltaColumn(Metric.MUTATION);
        }

        @Override
        public DetailedCell<?> getTestStrengthDelta() {
            return createColoredModifiedLinesCoverageDeltaColumn(Metric.TEST_STRENGTH);
        }

        DetailedCell<?> createColoredModifiedLinesCoverageDeltaColumn(final Metric metric) {
            return getModifiedLinesCoverageDelta(metric)
                    .<DetailedCell<?>>map(delta -> createColoredCoverageDeltaColumn(metric, delta))
                    .orElse(NO_COVERAGE);
        }

        /**
         * Returns the delta of the specified metric. For coverage metrics, the delta is the difference between the
         * coverage of the changed lines and the coverage of the whole file. All other metrics use the delta of the
         * file with respect to the reference build.
         *
         * @param metric
         *         the metric to get the delta for
         *
         * @return the delta of the metric, or an empty result if no delta is available
         */
        @Override
        Optional<Value> getMetricDelta(final Metric metric) {
            if (metric.isCoverage()) {
                return getModifiedLinesCoverageDelta(metric);
            }
            return super.getMetricDelta(metric);
        }

        private Optional<Value> getModifiedLinesCoverageDelta(final Metric metric) {
            var modifiedLinesCoverage = getFile().getTypedValue(metric, Coverage.nullObject(metric));
            if (modifiedLinesCoverage.isSet()) {
                return Optional.of(modifiedLinesCoverage.subtract(
                        originalFile.getTypedValue(metric, Coverage.nullObject(metric))));
            }
            return Optional.empty();
        }
    }
}
