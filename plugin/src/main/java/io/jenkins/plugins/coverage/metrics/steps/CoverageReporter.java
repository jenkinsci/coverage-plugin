package io.jenkins.plugins.coverage.metrics.steps;

import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.FilteredLog;

import hudson.FilePath;
import hudson.model.Run;
import hudson.model.TaskListener;

import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.coverage.metrics.source.SourceCodePainter;
import io.jenkins.plugins.forensics.delta.Delta;
import io.jenkins.plugins.forensics.delta.FileChanges;
import io.jenkins.plugins.forensics.reference.ReferenceFinder;
import io.jenkins.plugins.prism.SourceCodeRetention;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.ResultHandler;

/**
 * Transforms the old model to the new model and invokes all steps that work on the new model. Currently, only the
 * source code painting and copying have been moved to this new reporter class.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"checkstyle:ClassDataAbstractionCoupling", "PMD.LooseCoupling", "PMD.CouplingBetweenObjects"})
public class CoverageReporter {
    private static final NavigableMap<Metric, Difference> EMPTY_DELTA = new TreeMap<>();
    private static final List<Value> EMPTY_VALUES = List.of();

    @SuppressWarnings({"checkstyle:ParameterNumber", "checkstyle:JavaNCSS"})
    CoverageBuildAction publishAction(final String id, final String optionalName, final String icon,
            final Node rootNode,
            final Run<?, ?> build, final FilePath workspace, final TaskListener listener,
            final List<CoverageQualityGate> qualityGates, final String scm, final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention, final ResultHandler notifier,
            final FilteredLog log) throws InterruptedException {
        Optional<CoverageBuildAction> possibleReferenceResult = getReferenceBuildAction(build, id, log);

        CoverageBuildAction action;
        if (possibleReferenceResult.isPresent()) {
            action = computeCoverageBasedOnReferenceBuild(id, optionalName, icon, rootNode, build, workspace,
                    qualityGates, sourceCodeEncoding, sourceCodeRetention, notifier, possibleReferenceResult.get(),
                    scm, listener, log);
        }
        else {
            action = computeActionWithoutHistory(id, optionalName, icon, rootNode, build, workspace, qualityGates,
                    sourceCodeEncoding,
                    sourceCodeRetention, notifier, log);
        }

        build.addAction(action);
        return action;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private CoverageBuildAction computeActionWithoutHistory(
            final String id, final String optionalName, final String icon,
            final Node rootNode, final Run<?, ?> build, final FilePath workspace,
            final List<CoverageQualityGate> qualityGates, final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention, final ResultHandler notifier,
            final FilteredLog log) throws InterruptedException {
        var statistics = new CoverageStatistics(rootNode.aggregateValues(),
                EMPTY_DELTA, EMPTY_VALUES, EMPTY_DELTA, EMPTY_VALUES, EMPTY_DELTA);
        var evaluator = new CoverageQualityGateEvaluator(qualityGates, statistics);
        QualityGateResult qualityGateStatus = evaluator.evaluate(notifier, log);

        paintSourceFiles(build, workspace, sourceCodeEncoding, sourceCodeRetention, id, rootNode,
                rootNode.getAllFileNodes(), log);

        return new CoverageBuildAction(build, id, optionalName, icon, rootNode, qualityGateStatus, log);
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private CoverageBuildAction computeCoverageBasedOnReferenceBuild(
            final String id, final String optionalName, final String icon,
            final Node rootNode, final Run<?, ?> build, final FilePath workspace,
            final List<CoverageQualityGate> qualityGates, final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention, final ResultHandler notifier,
            final CoverageBuildAction referenceAction, final String scm,
            final TaskListener listener, final FilteredLog log) throws InterruptedException {
        log.logInfo("Calculating the code delta...");
        CodeDeltaCalculator codeDeltaCalculator = new CodeDeltaCalculator(build, workspace, listener, scm);
        Optional<Delta> delta = codeDeltaCalculator.calculateCodeDeltaToReference(referenceAction.getOwner(), log);

        Node referenceRoot = referenceAction.getResult();
        delta.ifPresent(value -> createDeltaReports(rootNode, log, referenceRoot, codeDeltaCalculator, value));

        log.logInfo("Calculating coverage deltas...");

        Node modifiedLinesCoverageRoot = rootNode.filterByModifiedLines();

        NavigableMap<Metric, Difference> modifiedLinesDelta;
        List<Value> modifiedFilesValues;
        NavigableMap<Metric, Difference> modifiedFilesDelta;
        if (hasModifiedLinesCoverage(modifiedLinesCoverageRoot)) {
            Node modifiedFilesCoverageRoot = rootNode.filterByModifiedFiles();
            modifiedFilesValues = modifiedFilesCoverageRoot.aggregateValues();
            modifiedFilesDelta = modifiedFilesCoverageRoot.computeDelta(
                    referenceRoot.filterByFileNames(modifiedFilesCoverageRoot.getFiles()));

            modifiedLinesDelta = modifiedLinesCoverageRoot.computeDelta(modifiedFilesCoverageRoot);
        }
        else {
            modifiedLinesDelta = EMPTY_DELTA;
            modifiedFilesValues = EMPTY_VALUES;
            modifiedFilesDelta = EMPTY_DELTA;

            if (rootNode.hasModifiedLines()) {
                log.logInfo("No detected code changes affect the code coverage");
            }
        }

        var overallValues = rootNode.aggregateValues();
        NavigableMap<Metric, Difference> overallDelta = rootNode.computeDelta(referenceRoot);
        var modifiedLinesValues = modifiedLinesCoverageRoot.aggregateValues();

        var statistics = new CoverageStatistics(overallValues, overallDelta,
                modifiedLinesValues, modifiedLinesDelta, modifiedFilesValues, modifiedFilesDelta);
        var evaluator = new CoverageQualityGateEvaluator(qualityGates, statistics);
        QualityGateResult qualityGateResult = evaluator.evaluate(notifier, log);

        var filesToStore = computePaintedFiles(rootNode, sourceCodeRetention, log, modifiedLinesCoverageRoot);
        paintSourceFiles(build, workspace, sourceCodeEncoding, sourceCodeRetention, id, rootNode, filesToStore, log);

        return new CoverageBuildAction(build, id, optionalName, icon, rootNode, qualityGateResult, log,
                referenceAction.getOwner().getExternalizableId(), overallDelta,
                modifiedLinesValues, modifiedLinesDelta,
                modifiedFilesValues, modifiedFilesDelta,
                rootNode.filterByIndirectChanges().aggregateValues());
    }

    private List<FileNode> computePaintedFiles(final Node rootNode, final SourceCodeRetention sourceCodeRetention,
            final FilteredLog log, final Node modifiedLinesCoverageRoot) {
        List<FileNode> filesToStore;
        if (sourceCodeRetention == SourceCodeRetention.MODIFIED) {
            filesToStore = modifiedLinesCoverageRoot.getAllFileNodes();
            log.logInfo("-> Selecting %d modified files for source code painting", filesToStore.size());
        }
        else {
            filesToStore = rootNode.getAllFileNodes();
        }
        return filesToStore;
    }

    @SuppressWarnings("checkstyle:ParameterNumber")
    private void paintSourceFiles(final Run<?, ?> build, final FilePath workspace, final String sourceCodeEncoding,
            final SourceCodeRetention sourceCodeRetention, final String id, final Node rootNode,
            final List<FileNode> filesToStore, final FilteredLog log) throws InterruptedException {
        log.logInfo("Executing source code painting...");
        SourceCodePainter sourceCodePainter = new SourceCodePainter(build, workspace, id);
        sourceCodePainter.processSourceCodePainting(rootNode, filesToStore,
                sourceCodeEncoding, sourceCodeRetention, log);
        log.logInfo("Finished coverage processing - adding the action to the build...");
    }

    private void createDeltaReports(final Node rootNode, final FilteredLog log, final Node referenceRoot,
            final CodeDeltaCalculator codeDeltaCalculator, final Delta delta) {
        FileChangesProcessor fileChangesProcessor = new FileChangesProcessor();

        try {
            log.logInfo("Preprocessing code changes...");
            Set<FileChanges> changes = codeDeltaCalculator.getCoverageRelevantChanges(delta);
            var mappedChanges = codeDeltaCalculator.mapScmChangesToReportPaths(changes, rootNode, log);
            var oldPathMapping = codeDeltaCalculator.createOldPathMapping(rootNode, referenceRoot, mappedChanges, log);

            log.logInfo("Obtaining code changes for files...");
            fileChangesProcessor.attachChangedCodeLines(rootNode, mappedChanges);

            log.logInfo("Obtaining indirect coverage changes...");
            fileChangesProcessor.attachIndirectCoveragesChanges(rootNode, referenceRoot,
                    mappedChanges, oldPathMapping);

            log.logInfo("Obtaining coverage delta for files...");
            fileChangesProcessor.attachFileCoverageDeltas(rootNode, referenceRoot, oldPathMapping);
        }
        catch (IllegalStateException exception) {
            log.logError("An error occurred while processing code and coverage changes:");
            log.logError("-> Message: " + exception.getMessage());
            log.logError("-> Skipping calculating modified lines coverage, modified files coverage"
                    + " and indirect coverage changes");
        }
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
        return ((edu.hm.hafner.coverage.Coverage) value).isSet();
    }

    private Optional<CoverageBuildAction> getReferenceBuildAction(final Run<?, ?> build, final String id,
            final FilteredLog log) {
        log.logInfo("Obtaining result action of reference build");

        ReferenceFinder referenceFinder = new ReferenceFinder();
        Optional<Run<?, ?>> reference = referenceFinder.findReference(build, log);

        if (reference.isPresent()) {
            Run<?, ?> referenceBuild = reference.get();

            log.logInfo("-> Using reference build '%s'", referenceBuild);
            Optional<CoverageBuildAction> possibleResult = getAction(id, reference.get());
            if (possibleResult.isEmpty()) {
                log.logInfo("-> Reference build has no action for ID '%s'", id);
            }
            return possibleResult;
        }
        log.logInfo("-> Found no reference build");

        return Optional.empty();
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
}
