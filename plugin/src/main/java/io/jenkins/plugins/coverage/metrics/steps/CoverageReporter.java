package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.coverage.ChangeSet;
import edu.hm.hafner.coverage.ChangeSet.ChangeSetBuilder;
import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.MetricAggregation;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.coverage.metrics.source.SourceCodePainter;
import io.jenkins.plugins.forensics.delta.Delta;
import io.jenkins.plugins.forensics.delta.FileChanges;
import io.jenkins.plugins.forensics.reference.ReferenceFinder;
import io.jenkins.plugins.prism.SourceCodeRetention;
import io.jenkins.plugins.util.ResultHandler;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Transforms the old model to the new model and invokes all steps that work on the new model. Currently, only the
 * source code painting and copying have been moved to this new reporter class.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling", "PMD.CouplingBetweenObjects"})
public class CoverageReporter {
    private static final Map<MetricAggregation, List<Value>> EMPTY_AGGREGATION = Map.of();
    /** All aggregations are computed, so that the values are available in quality gates, views, and trends. */
    private static final Set<MetricAggregation> ALL_AGGREGATIONS = EnumSet.allOf(MetricAggregation.class);

    private static final Map<MetricAggregation, List<Difference>> EMPTY_DELTA = Map.of();

    @SuppressWarnings({"checkstyle:ParameterNumber", "checkstyle:JavaNCSS"})
    CoverageBuildAction publishAction(
            final String id,
            final String optionalName,
            final String icon,
            final Node rootNode,
            final Run<?, ?> build,
            final FilePath workspace,
            final TaskListener listener,
            final List<CoverageQualityGate> qualityGates,
            final String scm,
            final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention,
            final ResultHandler notifier,
            final FilteredLog log)
            throws InterruptedException {
        Optional<CoverageBuildAction> possibleReferenceResult = getReferenceBuildAction(build, id, log);

        CoverageBuildAction action;
        if (possibleReferenceResult.isPresent()) {
            action = computeCoverageBasedOnReferenceBuild(
                    id,
                    optionalName,
                    icon,
                    rootNode,
                    build,
                    workspace,
                    qualityGates,
                    sourceCodeEncoding,
                    sourceCodeRetention,
                    notifier,
                    possibleReferenceResult.get(),
                    scm,
                    listener,
                    log);
        } else {
            action = computeActionWithoutHistory(
                    id,
                    optionalName,
                    icon,
                    rootNode,
                    build,
                    workspace,
                    qualityGates,
                    sourceCodeEncoding,
                    sourceCodeRetention,
                    notifier,
                    log);
        }

        build.addAction(action);
        return action;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private CoverageBuildAction computeActionWithoutHistory(
            final String id,
            final String optionalName,
            final String icon,
            final Node rootNode,
            final Run<?, ?> build,
            final FilePath workspace,
            final List<CoverageQualityGate> qualityGates,
            final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention,
            final ResultHandler notifier,
            final FilteredLog log)
            throws InterruptedException {
        var statistics = new CoverageStatistics(
                aggregateValues(rootNode, ALL_AGGREGATIONS),
                EMPTY_DELTA,
                EMPTY_AGGREGATION,
                EMPTY_DELTA,
                EMPTY_AGGREGATION,
                EMPTY_DELTA);
        var evaluator = new CoverageQualityGateEvaluator(qualityGates, statistics);
        var qualityGateStatus = evaluator.evaluate(notifier, log);

        paintSourceFiles(
                build,
                workspace,
                sourceCodeEncoding,
                sourceCodeRetention,
                id,
                rootNode,
                rootNode.getAllFileNodes(),
                ChangeSet.empty(),
                log);

        return new CoverageBuildAction(build, id, optionalName, icon, rootNode, qualityGateStatus, log, statistics);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private CoverageBuildAction computeCoverageBasedOnReferenceBuild(
            final String id,
            final String optionalName,
            final String icon,
            final Node rootNode,
            final Run<?, ?> build,
            final FilePath workspace,
            final List<CoverageQualityGate> qualityGates,
            final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention,
            final ResultHandler notifier,
            final CoverageBuildAction referenceAction,
            final String scm,
            final TaskListener listener,
            final FilteredLog log)
            throws InterruptedException {
        log.logInfo("Calculating the code delta...");
        var codeDeltaCalculator = new CodeDeltaCalculator(build, workspace, listener, scm);
        Optional<Delta> delta = codeDeltaCalculator.calculateCodeDeltaToReference(referenceAction.getOwner(), log);

        var referenceRoot = referenceAction.getResult();
        var changes = delta.map(value -> createDeltaReports(rootNode, log, referenceRoot, codeDeltaCalculator, value))
                .orElse(ChangeSet.empty());

        log.logInfo("Calculating coverage deltas...");

        var modifiedLinesCoverageRoot = rootNode.filterByModifiedLines(changes);

        Map<MetricAggregation, List<Difference>> modifiedLinesDelta;
        Map<MetricAggregation, List<Value>> modifiedFilesAggregation;
        Map<MetricAggregation, List<Difference>> modifiedFilesDelta;
        if (hasModifiedLinesCoverage(modifiedLinesCoverageRoot)) {
            var modifiedFilesCoverageRoot = rootNode.filterByModifiedFiles(changes);
            modifiedFilesAggregation = aggregateValues(modifiedFilesCoverageRoot, ALL_AGGREGATIONS);
            modifiedFilesDelta = computeDelta(
                    modifiedFilesCoverageRoot,
                    referenceRoot.filterByFileNames(modifiedFilesCoverageRoot.getFiles()),
                    ALL_AGGREGATIONS);
            modifiedLinesDelta = computeDelta(modifiedLinesCoverageRoot, modifiedFilesCoverageRoot, ALL_AGGREGATIONS);
        } else {
            modifiedLinesDelta = EMPTY_DELTA;
            modifiedFilesAggregation = EMPTY_AGGREGATION;
            modifiedFilesDelta = EMPTY_DELTA;

            if (changes.hasModifiedLines()) {
                log.logInfo("No detected code changes affect the code coverage");
            }
        }

        var overallAggregation = aggregateValues(rootNode, ALL_AGGREGATIONS);
        var overallDelta = computeDelta(rootNode, referenceRoot, ALL_AGGREGATIONS);
        var modifiedLinesAggregation = aggregateValues(modifiedLinesCoverageRoot, ALL_AGGREGATIONS);

        var statistics = new CoverageStatistics(
                overallAggregation,
                overallDelta,
                modifiedLinesAggregation,
                modifiedLinesDelta,
                modifiedFilesAggregation,
                modifiedFilesDelta);
        var evaluator = new CoverageQualityGateEvaluator(qualityGates, statistics);
        var qualityGateResult = evaluator.evaluate(notifier, log);

        var filesToStore = computePaintedFiles(rootNode, sourceCodeRetention, log, modifiedLinesCoverageRoot);
        paintSourceFiles(
                build, workspace, sourceCodeEncoding, sourceCodeRetention, id, rootNode, filesToStore, changes, log);

        return new CoverageBuildAction(
                build,
                id,
                optionalName,
                icon,
                rootNode,
                qualityGateResult,
                log,
                referenceAction.getOwner().getExternalizableId(),
                statistics,
                changes,
                rootNode.filterByIndirectChanges(changes).aggregateValues());
    }

    /**
     * Aggregates the values of the specified tree for the specified aggregations.
     *
     * @param node
     *         the root of the tree
     * @param aggregations
     *         the aggregations to compute
     *
     * @return the values grouped by aggregation
     */
    private static Map<MetricAggregation, List<Value>> aggregateValues(
            final Node node, final Set<MetricAggregation> aggregations) {
        var aggregatedValues = new EnumMap<MetricAggregation, List<Value>>(MetricAggregation.class);
        aggregations.forEach(aggregation -> aggregatedValues.put(aggregation, node.aggregateValues(aggregation)));
        return aggregatedValues;
    }

    /**
     * Computes the differences between the aggregated values of the specified tree and the reference tree for the
     * specified aggregations. A difference is only computed for metrics that are available in both trees.
     *
     * @param node
     *         the root of the tree
     * @param reference
     *         the root of the reference tree
     * @param aggregations
     *         the aggregations to compute
     *
     * @return the differences grouped by aggregation
     */
    private static Map<MetricAggregation, List<Difference>> computeDelta(
            final Node node, final Node reference, final Set<MetricAggregation> aggregations) {
        var deltas = new EnumMap<MetricAggregation, List<Difference>>(MetricAggregation.class);
        aggregations.forEach(aggregation -> deltas.put(aggregation, node.computeDelta(reference, aggregation)));
        return deltas;
    }

    private List<FileNode> computePaintedFiles(
            final Node rootNode,
            final SourceCodeRetention sourceCodeRetention,
            final FilteredLog log,
            final Node modifiedLinesCoverageRoot) {
        List<FileNode> filesToStore;
        if (sourceCodeRetention == SourceCodeRetention.MODIFIED) {
            filesToStore = modifiedLinesCoverageRoot.getAllFileNodes();
            log.logInfo("-> Selecting %d modified files for source code painting", filesToStore.size());
        } else {
            filesToStore = rootNode.getAllFileNodes();
        }
        return filesToStore;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void paintSourceFiles(
            final Run<?, ?> build,
            final FilePath workspace,
            final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention,
            final String id,
            final Node rootNode,
            final List<FileNode> filesToStore,
            final ChangeSet changes,
            final FilteredLog log)
            throws InterruptedException {
        log.logInfo("Executing source code painting...");
        var sourceCodePainter = new SourceCodePainter(build, workspace, id);
        sourceCodePainter.processSourceCodePainting(
                rootNode, filesToStore, changes, sourceCodeEncoding, sourceCodeRetention, log);
        log.logInfo("Finished coverage processing - adding the action to the build...");
    }

    private ChangeSet createDeltaReports(
            final Node rootNode,
            final FilteredLog log,
            final Node referenceRoot,
            final CodeDeltaCalculator codeDeltaCalculator,
            final Delta delta) {
        var fileChangesProcessor = new FileChangesProcessor();
        var changeSetBuilder = new ChangeSetBuilder();

        try {
            log.logInfo("Preprocessing code changes...");
            Set<FileChanges> changes = codeDeltaCalculator.getCoverageRelevantChanges(delta);
            var mappedChanges = codeDeltaCalculator.mapScmChangesToReportPaths(changes, rootNode, log);
            var oldPathMapping = codeDeltaCalculator.createOldPathMapping(rootNode, referenceRoot, mappedChanges, log);

            log.logInfo("Obtaining code changes for files...");
            fileChangesProcessor.attachChangedCodeLines(rootNode, mappedChanges, changeSetBuilder);

            log.logInfo("Obtaining indirect coverage changes...");
            fileChangesProcessor.attachIndirectCoveragesChanges(
                    rootNode, referenceRoot, mappedChanges, oldPathMapping, changeSetBuilder);

            log.logInfo("Obtaining coverage delta for files...");
            fileChangesProcessor.attachFileCoverageDeltas(rootNode, referenceRoot, oldPathMapping);
        } catch (IllegalStateException exception) {
            log.logError("An error occurred while processing code and coverage changes:");
            log.logError("-> Message: " + exception.getMessage());
            log.logError("-> Skipping calculating modified lines coverage, modified files coverage"
                    + " and indirect coverage changes");
        }
        return changeSetBuilder.build();
    }

    private boolean hasModifiedLinesCoverage(final Node modifiedLinesCoverageRoot) {
        Optional<Value> lineCoverage = modifiedLinesCoverageRoot.getValue(Metric.LINE);
        if (lineCoverage.isPresent() && hasLineCoverageSet(lineCoverage.get())) {
            return true;
        }
        Optional<Value> branchCoverage = modifiedLinesCoverageRoot.getValue(Metric.BRANCH);
        return branchCoverage.filter(this::hasLineCoverageSet).isPresent();
    }

    private boolean hasLineCoverageSet(final Value value) {
        return value instanceof Coverage coverage && coverage.isSet();
    }

    Optional<CoverageBuildAction> getReferenceBuildAction(
            final Run<?, ?> build, final String id, final FilteredLog log) {
        log.logInfo("Obtaining result action of reference build");

        var referenceFinder = createReferenceFinder();
        Optional<Run<?, ?>> reference = referenceFinder.findReference(build, log);

        if (reference.isPresent()) {
            Run<?, ?> referenceBuild = reference.get();

            log.logInfo("-> Using reference build '%s'", referenceBuild);
            Optional<CoverageBuildAction> possibleResult = getAction(id, referenceBuild);
            if (possibleResult.isEmpty()) {
                log.logInfo("-> Reference build has no action for ID '%s'", id);
                possibleResult = Optional.ofNullable(referenceBuild.getPreviousBuild())
                        .flatMap(previousBuild -> findActionInBuildHistory(id, previousBuild));
                possibleResult.ifPresent(
                        action -> log.logInfo("-> Reference build information adjusted to '%s'", action.getOwner()));
            }
            return possibleResult;
        }
        log.logInfo("-> Found no reference build");

        return Optional.empty();
    }

    /**
     * Creates a new {@link ReferenceFinder} instance. Override in tests to inject a stub.
     *
     * @return a new {@link ReferenceFinder}
     */
    @VisibleForTesting
    ReferenceFinder createReferenceFinder() {
        return new ReferenceFinder();
    }

    private Optional<CoverageBuildAction> getAction(final String id, final Run<?, ?> build) {
        List<CoverageBuildAction> actions = build.getActions(CoverageBuildAction.class);
        for (CoverageBuildAction action : actions) {
            if (action.getUrlName().equals(id)) {
                return Optional.of(action);
            }
        }
        return Optional.empty();
    }

    Optional<CoverageBuildAction> findActionInBuildHistory(final String id, final Run<?, ?> build) {
        for (Run<?, ?> current = build; current != null; current = current.getPreviousBuild()) {
            Optional<CoverageBuildAction> found = getAction(id, current);
            if (found.isPresent()) {
                return found;
            }
        }

        return Optional.empty();
    }
}
