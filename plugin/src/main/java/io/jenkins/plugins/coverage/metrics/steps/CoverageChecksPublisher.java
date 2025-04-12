package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Mutation;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.util.LineRange;
import edu.hm.hafner.util.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import hudson.model.TaskListener;

import io.jenkins.plugins.checks.api.ChecksAnnotation;
import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationBuilder;
import io.jenkins.plugins.checks.api.ChecksAnnotation.ChecksAnnotationLevel;
import io.jenkins.plugins.checks.api.ChecksConclusion;
import io.jenkins.plugins.checks.api.ChecksDetails;
import io.jenkins.plugins.checks.api.ChecksDetails.ChecksDetailsBuilder;
import io.jenkins.plugins.checks.api.ChecksOutput.ChecksOutputBuilder;
import io.jenkins.plugins.checks.api.ChecksPublisherFactory;
import io.jenkins.plugins.checks.api.ChecksStatus;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.coverage.metrics.steps.CoverageRecorder.ChecksAnnotationScope;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.QualityGateStatus;

/**
 * Publishes coverage as Checks to SCM platforms.
 *
 * @author Florian Orendi
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity", "PMD.CouplingBetweenObjects"})
class CoverageChecksPublisher {
    private static final ElementFormatter FORMATTER = new ElementFormatter();
    private static final int TITLE_HEADER_LEVEL = 4;
    private static final char NEW_LINE = '\n';
    private static final String COLUMN = "|";
    private static final String GAP = " ";

    private final CoverageBuildAction action;
    private final Node rootNode;
    private final JenkinsFacade jenkinsFacade;
    private final String checksName;
    private final ChecksAnnotationScope annotationScope;

    CoverageChecksPublisher(final CoverageBuildAction action, final Node rootNode, final String checksName,
            final ChecksAnnotationScope annotationScope) {
        this(action, rootNode, checksName, annotationScope, new JenkinsFacade());
    }

    @VisibleForTesting
    CoverageChecksPublisher(final CoverageBuildAction action, final Node rootNode, final String checksName,
            final ChecksAnnotationScope annotationScope, final JenkinsFacade jenkinsFacade) {
        this.rootNode = rootNode;
        this.jenkinsFacade = jenkinsFacade;
        this.action = action;
        this.checksName = checksName;
        this.annotationScope = annotationScope;
    }

    private ChecksFormatter getFormatter() {
        if (rootNode.getValue(Metric.FUNCTION_CALL).isPresent()
                || rootNode.getValue(Metric.MCDC_PAIR).isPresent()) {
            return new VectorCastFormatter();
        }
        return new ChecksFormatter();
    }

    /**
     * Publishes the coverage report as Checks to SCM platforms.
     *
     * @param listener
     *         The task listener
     */
    void publishCoverageReport(final TaskListener listener) {
        ChecksPublisherFactory.fromRun(action.getOwner(), listener).publish(extractChecksDetails());
    }

    @VisibleForTesting
    ChecksDetails extractChecksDetails() {
        var output = new ChecksOutputBuilder()
                .withTitle(getChecksTitle())
                .withSummary(getSummary())
                .withText(getProjectMetricsSummary())
                .withAnnotations(getAnnotations())
                .build();

        return new ChecksDetailsBuilder()
                .withName(checksName)
                .withStatus(ChecksStatus.COMPLETED)
                .withConclusion(getCheckConclusion(action.getQualityGateResult().getOverallStatus()))
                .withDetailsURL(getBaseUrl())
                .withOutput(output)
                .build();
    }

    private String getChecksTitle() {
        return getFormatter().getTitleMetrics().stream()
                .map(this::format)
                .flatMap(Optional::stream)
                .collect(Collectors.joining(", "));
    }

    private Optional<String> format(final Metric metric) {
        var baseline = selectBaseline();
        return action.getValueForMetric(baseline, metric)
                .map(value -> formatValue(baseline, metric, value));
    }

    private Baseline selectBaseline() {
        if (action.hasBaselineResult(Baseline.MODIFIED_LINES)) {
            return Baseline.MODIFIED_LINES;
        }
        return Baseline.PROJECT;
    }

    private String formatValue(final Baseline baseline, final Metric metric, final Value value) {
        return "%s: %s%s".formatted(
                FORMATTER.getDisplayName(metric), FORMATTER.format(value), getDeltaDetails(baseline, metric));
    }

    private String getDeltaDetails(final Baseline baseline, final Metric metric) {
        if (action.hasDelta(baseline, metric)) {
            return " (%s)".formatted(action.formatDelta(baseline, metric));
        }
        return StringUtils.EMPTY;
    }

    private String getSummary() {
        return getAnnotationSummary()
                + getOverallCoverageSummary()
                + getQualityGatesSummary();
    }

    private String getAnnotationSummary() {
        if (rootNode.hasModifiedLines()) {
            var filteredRoot = rootNode.filterByModifiedLines();
            var modifiedFiles = filteredRoot.getAllFileNodes();

            var summary = new StringBuilder("#### Summary for modified lines\n");

            createTotalLinesSummary(modifiedFiles, summary);
            createLineCoverageSummary(modifiedFiles, summary);
            createBranchCoverageSummary(filteredRoot, modifiedFiles, summary);
            createMutationCoverageSummary(filteredRoot, modifiedFiles, summary);

            return summary.toString();
        }
        return StringUtils.EMPTY;
    }

    private void createTotalLinesSummary(final List<FileNode> modifiedFiles, final StringBuilder summary) {
        var total = countLines(modifiedFiles, FileNode::getModifiedLines);
        if (total == 1) {
            summary.append("- 1 line has been modified");
        }
        else {
            summary.append("- %d lines have been modified".formatted(total));
        }
        summary.append(NEW_LINE);
    }

    private int countLines(final List<FileNode> modifiedFiles, final Function<FileNode, Collection<?>> linesGetter) {
        return modifiedFiles.stream().map(linesGetter).mapToInt(Collection::size).sum();
    }

    private void createLineCoverageSummary(final List<FileNode> modifiedFiles, final StringBuilder summary) {
        var missed = countLines(modifiedFiles, FileNode::getMissedLines);
        if (missed == 0) {
            summary.append("- all lines are covered");
        }
        else if (missed == 1) {
            summary.append("- 1 line is not covered");
        }
        else {
            summary.append("- %d lines are not covered".formatted(missed));
        }
        summary.append(NEW_LINE);
    }

    private void createBranchCoverageSummary(final Node filteredRoot, final List<FileNode> modifiedFiles,
            final StringBuilder summary) {
        if (filteredRoot.containsMetric(Metric.BRANCH)) {
            var partiallyCovered = modifiedFiles.stream()
                    .map(FileNode::getPartiallyCoveredLines)
                    .map(Map::size)
                    .count();
            if (partiallyCovered == 1) {
                summary.append("- 1 line is covered only partially");
            }
            else {
                summary.append("- %d lines are covered only partially".formatted(partiallyCovered));
            }
            summary.append(NEW_LINE);
        }
    }

    private void createMutationCoverageSummary(final Node filteredRoot, final List<FileNode> modifiedFiles,
            final StringBuilder summary) {
        if (filteredRoot.containsMetric(Metric.MUTATION)) {
            var survived = modifiedFiles.stream()
                    .map(FileNode::getSurvivedMutationsPerLine)
                    .map(Map::entrySet)
                    .flatMap(Collection::stream)
                    .map(Entry::getValue)
                    .count();
            var mutations = countLines(modifiedFiles, FileNode::getMutations);
            if (survived == 0) {
                if (mutations == 1) {
                    summary.append("- 1 mutation has been killed");
                }
                else {
                    summary.append("- all %d mutations have been killed".formatted(mutations));
                }
            }
            else if (survived == 1) {
                summary.append("- 1 mutation survived (of %d)".formatted(mutations));
            }
            else {
                summary.append("- %d mutations survived (of %d)".formatted(survived, mutations));
            }
            summary.append(NEW_LINE);
        }
    }

    private List<ChecksAnnotation> getAnnotations() {
        if (annotationScope == ChecksAnnotationScope.SKIP) {
            return List.of();
        }

        var annotations = new ArrayList<ChecksAnnotation>();
        var filteredByScope = filterAnnotations();
        var hasMutationCoverage = filteredByScope.getValue(Metric.MUTATION).isPresent();
        for (var fileNode : filteredByScope.getAllFileNodes()) {
            if (hasMutationCoverage) {
                annotations.addAll(getSurvivedMutations(fileNode));
            }
            else {
                annotations.addAll(getMissingLines(fileNode));
                annotations.addAll(getPartiallyCoveredLines(fileNode));
            }
        }
        return annotations;
    }

    private Node filterAnnotations() {
        if (annotationScope == ChecksAnnotationScope.ALL_LINES) {
            return rootNode;
        }
        else {
            return rootNode.filterByModifiedLines();
        }
    }

    private Collection<? extends ChecksAnnotation> getMissingLines(final FileNode fileNode) {
        var builder = createAnnotationBuilder(fileNode);
        return fileNode.getMissedLineRanges().stream()
                .map(range -> rangeToAnnotation(range, builder))
                .collect(Collectors.toList());
    }

    private ChecksAnnotation rangeToAnnotation(final LineRange range, final ChecksAnnotationBuilder builder) {
        if (range.getStart() == range.getEnd()) {
            builder.withTitle("Not covered line")
                    .withMessage("Line %d is not covered by tests".formatted(range.getStart()));
        }
        else {
            builder.withTitle("Not covered lines").withMessage(
                    "Lines %d-%d are not covered by tests".formatted(range.getStart(), range.getEnd()));
        }
        return builder
                .withStartLine(range.getStart())
                .withEndLine(range.getEnd())
                .build();
    }

    private Collection<? extends ChecksAnnotation> getSurvivedMutations(final FileNode fileNode) {
        var builder = createAnnotationBuilder(fileNode).withTitle("Mutation survived");

        return fileNode.getSurvivedMutationsPerLine().entrySet().stream()
                .filter(entry -> fileNode.getCoveredOfLine(entry.getKey()) > 0)
                .map(entry -> builder.withMessage(createMutationMessage(entry.getKey(), entry.getValue()))
                        .withStartLine(entry.getKey())
                        .withEndLine(entry.getKey())
                        .withRawDetails(createMutationDetails(entry.getValue()))
                        .build())
                .collect(Collectors.toList());
    }

    private String createMutationDetails(final List<Mutation> mutations) {
        return mutations.stream()
                .map(mutation -> "- %s (%s)".formatted(mutation.getDescription(), mutation.getMutator()))
                .collect(Collectors.joining("\n", "Survived mutations:\n", ""));
    }

    private String createMutationMessage(final int line, final List<Mutation> survived) {
        if (survived.size() == 1) {
            return "One mutation survived in line %d (%s)".formatted(line, formatMutator(survived));
        }
        return "%d mutations survived in line %d".formatted(survived.size(), line);
    }

    private String formatMutator(final List<Mutation> survived) {
        return survived.get(0).getMutator().replaceAll(".*\\.", "");
    }

    private Collection<? extends ChecksAnnotation> getPartiallyCoveredLines(final FileNode fileNode) {
        var builder = createAnnotationBuilder(fileNode).withTitle("Partially covered line");

        return fileNode.getPartiallyCoveredLines().entrySet().stream()
                .map(entry -> builder.withMessage(createBranchMessage(entry.getKey(), entry.getValue()))
                        .withStartLine(entry.getKey())
                        .withEndLine(entry.getKey())
                        .build())
                .collect(Collectors.toList());
    }

    private String createBranchMessage(final int line, final int missed) {
        if (missed == 1) {
            return "Line %d is only partially covered, one branch is missing".formatted(line);
        }
        return "Line %d is only partially covered, %d branches are missing".formatted(line, missed);
    }

    private ChecksAnnotationBuilder createAnnotationBuilder(final FileNode fileNode) {
        return new ChecksAnnotationBuilder()
                .withPath(fileNode.getRelativePath())
                .withAnnotationLevel(ChecksAnnotationLevel.WARNING);
    }

    private String getBaseUrl() {
        return jenkinsFacade.getAbsoluteUrl(action.getOwner().getUrl(), action.getUrlName());
    }

    private List<Baseline> getBaselines() {
        return List.of(Baseline.PROJECT, Baseline.MODIFIED_FILES, Baseline.MODIFIED_LINES, Baseline.INDIRECT);
    }

    private String getOverallCoverageSummary() {
        if (rootNode.hasModifiedLines()) {
            return createDeltaBaselinesOverview();
        }
        else {
            return createProjectOverview();
        }
    }

    private String createDeltaBaselinesOverview() {
        var description = new StringBuilder(getSectionHeader(TITLE_HEADER_LEVEL, "Overview by baseline"));

        for (Baseline baseline : getBaselines()) {
            if (action.hasBaselineResult(baseline)) {
                description.append(getBulletListItem(1,
                        formatText(TextFormat.BOLD,
                                getUrlText(action.getTitle(baseline), getBaseUrl() + baseline.getUrl()))));
                for (Value value : getValues(baseline)) {
                    var display = FORMATTER.formatDetailedValueWithMetric(value);
                    if (action.hasDelta(baseline, value.getMetric())) {
                        display += " - Delta: %s".formatted(action.formatDelta(baseline, value.getMetric()));
                    }
                    description.append(getBulletListItem(TITLE_HEADER_LEVEL, display));
                }
            }
        }
        description.append(NEW_LINE);
        return description.toString();
    }

    private List<Value> getValues(final Baseline baseline) {
        return action.getAllValues(baseline).stream()
                .filter(value -> getFormatter().getOverviewMetrics().contains(value.getMetric()))
                .collect(Collectors.toList());
    }

    private String createProjectOverview() {
        var description = new StringBuilder(getSectionHeader(TITLE_HEADER_LEVEL, "Project Overview"));
        description.append("No changes detected, that affect the code coverage.\n");

        for (Value value : getValues(Baseline.PROJECT)) {
            description.append(getBulletListItem(1, FORMATTER.formatDetailedValueWithMetric(value)));
        }

        description.append(NEW_LINE);
        return description.toString();
    }

    /**
     * Checks overview regarding the quality gate status.
     *
     * @return the markdown string representing the status summary
     */
    private String getQualityGatesSummary() {
        var summary = getSectionHeader(TITLE_HEADER_LEVEL, "Quality Gates Summary");
        var qualityGateResult = action.getQualityGateResult();
        if (qualityGateResult.isInactive()) {
            return summary + "No active quality gates.";
        }
        return summary
                + "Overall result: " + qualityGateResult.getOverallStatus().getDescription() + "\n"
                + qualityGateResult.getMessages().stream()
                .map(s -> s.replaceAll("-> ", ""))
                .map(s -> s.replaceAll("[\\[\\]]", ""))
                .collect(asSeparateLines());
    }

    private Collector<CharSequence, ?, String> asSeparateLines() {
        return Collectors.joining("\n- ", "- ", "\n");
    }

    private String getProjectMetricsSummary() {
        var builder = new StringBuilder(getSectionHeader(TITLE_HEADER_LEVEL, "Project coverage details"));
        builder.append(COLUMN);
        builder.append(COLUMN);

        builder.append(getMetricStream()
                .map(FORMATTER::getDisplayName)
                .collect(asColumn()));
        builder.append(COLUMN);
        builder.append(":---:");
        builder.append(COLUMN);
        builder.append(getMetricStream()
                .map(i -> ":---:")
                .collect(asColumn()));
        for (Baseline baseline : action.getBaselines()) {
            if (action.hasBaselineResult(baseline)) {
                builder.append("%s **%s**|".formatted(Icon.FEET.markdown,
                        FORMATTER.getDisplayName(baseline)));
                builder.append(getMetricStream()
                        .map(metric -> action.formatValue(baseline, metric))
                        .collect(asColumn()));

                var deltaBaseline = action.getDeltaBaseline(baseline);
                if (deltaBaseline != baseline) {
                    builder.append("%s **%s**|".formatted(Icon.CHART_UPWARDS_TREND.markdown,
                            FORMATTER.getDisplayName(deltaBaseline)));
                    builder.append(getMetricStream()
                            .map(metric -> getFormatDelta(baseline, metric))
                            .collect(asColumn()));
                }
            }
        }

        return builder.toString();
    }

    private String getFormatDelta(final Baseline baseline, final Metric metric) {
        var delta = action.formatDelta(baseline, metric);
        return delta + getTrendIcon(delta);
    }

    private Stream<Metric> getMetricStream() {
        return Metric.getCoverageMetrics().stream()
                .skip(1)
                .filter(m -> rootNode.getValue(m).isPresent());
    }

    private Collector<CharSequence, ?, String> asColumn() {
        return Collectors.joining(COLUMN, "", "\n");
    }

    private String formatText(final TextFormat format, final String text) {
        return switch (format) {
            case BOLD -> "**" + text + "**";
            case CURSIVE -> "_" + text + "_";
        };
    }

    private String getTrendIcon(final String trend) {
        if (!StringUtils.containsAny(trend, "123456789") || trend.startsWith("n/a")) {
            return StringUtils.EMPTY;
        }
        return GAP + (trend.startsWith("+") ? Icon.ARROW_UP.markdown : Icon.ARROW_DOWN.markdown);
    }

    private String getBulletListItem(final int level, final String text) {
        int whitespaces = (level - 1) * TITLE_HEADER_LEVEL;
        return String.join("", Collections.nCopies(whitespaces, GAP)) + "* " + text + "\n";
    }

    private String getUrlText(final String text, final String url) {
        return "[%s](%s)".formatted(text, url);
    }

    private String getSectionHeader(final int level, final String text) {
        return String.join("", Collections.nCopies(level, "#")) + GAP + text + "\n\n";
    }

    private ChecksConclusion getCheckConclusion(final QualityGateStatus status) {
        return switch (status) {
            case INACTIVE, PASSED -> ChecksConclusion.SUCCESS;
            case FAILED, ERROR, WARNING, NOTE -> ChecksConclusion.FAILURE;
        };
    }

    private enum Icon {
        FEET(":feet:"),
        WHITE_CHECK_MARK(":white_check_mark:"),
        CHART_UPWARDS_TREND(":chart_with_upwards_trend:"),
        ARROW_UP(":arrow_up:"),
        ARROW_RIGHT(":arrow_right:"),
        ARROW_DOWN(":arrow_down:");

        private final String markdown;

        Icon(final String markdown) {
            this.markdown = markdown;
        }
    }

    private enum TextFormat {
        BOLD,
        CURSIVE
    }

    /**
     * Determines the metrics that should be shown in the title, overview, and tables.
     * Metrics without a valid value in the coverage tree will be skipped.
     */
    private static class ChecksFormatter {
        NavigableSet<Metric> getTitleMetrics() {
            return new TreeSet<>(
                    Set.of(Metric.LINE, Metric.BRANCH, Metric.MUTATION));
        }

        NavigableSet<Metric> getOverviewMetrics() {
            return new TreeSet<>(
                    Set.of(Metric.LINE, Metric.LOC, Metric.BRANCH, Metric.CYCLOMATIC_COMPLEXITY,
                            Metric.MUTATION, Metric.TEST_STRENGTH, Metric.TESTS,
                            Metric.MCDC_PAIR, Metric.FUNCTION_CALL));
        }
    }

    private static class VectorCastFormatter extends ChecksFormatter {
        @Override
        NavigableSet<Metric> getOverviewMetrics() {
            var valueMetrics = super.getOverviewMetrics();
            valueMetrics.add(Metric.METHOD);
            return valueMetrics;
        }
    }
}
