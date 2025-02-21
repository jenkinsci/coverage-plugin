package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.echarts.ChartModelConfiguration;
import edu.hm.hafner.echarts.JacksonFacade;
import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.VisibleForTesting;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kohsuke.stapler.StaplerProxy;
import hudson.Functions;
import hudson.model.Run;
import hudson.util.XStream2;

import io.jenkins.plugins.coverage.metrics.charts.TrendChart;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;
import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;
import io.jenkins.plugins.coverage.metrics.steps.CoverageXmlStream.MetricFractionMapConverter;
import io.jenkins.plugins.echarts.GenericBuildActionIterator.BuildActionIterable;
import io.jenkins.plugins.forensics.reference.ReferenceBuild;
import io.jenkins.plugins.util.AbstractXmlStream;
import io.jenkins.plugins.util.BuildAction;
import io.jenkins.plugins.util.JenkinsFacade;
import io.jenkins.plugins.util.JobAction;
import io.jenkins.plugins.util.QualityGateResult;

import static hudson.model.Run.*;

/**
 * Controls the life cycle of the coverage results in a job. This action persists the results of a build and displays a
 * summary on the build page. The actual visualization of the results is defined in the matching {@code summary.jelly}
 * file. This action also provides access to the coverage details: these are rendered using a new view instance.
 *
 * @author Ullrich Hafner
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity", "PMD.CouplingBetweenObjects", "checkstyle:ClassDataAbstractionCoupling", "checkstyle:ClassFanOutComplexity"})
public final class CoverageBuildAction extends BuildAction<Node> implements StaplerProxy {
    @Serial
    private static final long serialVersionUID = -6023811049340671399L;

    private static final ElementFormatter FORMATTER = new ElementFormatter();
    private static final String NO_REFERENCE_BUILD = "-";
    private static final List<Difference> NO_VALUES = List.of();
    private static final int MAX_METRICS_COUNT_IN_SUMMARY = 5;

    private final String id;
    private final String name;

    private final String referenceBuildId;

    private final QualityGateResult qualityGateResult;

    private final String icon;
    private final FilteredLog log;

    /** The aggregated values of the result for the root of the tree. */
    private final List<? extends Value> projectValues;

    /** The delta of this build's coverages with respect to the reference build. */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR", justification = "Not used anymore")
    private transient NavigableMap<Metric, Difference> difference;
    private /* almost final */ List<Difference> differences; // since 2.0.0

    /** The coverages filtered by modified lines of the associated change request. */
    private final List<? extends Value> modifiedLinesCoverage;

    /** The coverage delta of the associated change request with respect to the reference build. */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR", justification = "Not used anymore")
    private transient NavigableMap<Metric, Difference> modifiedLinesCoverageDifference;
    private /* almost final */ List<Difference> modifiedLinesDifferences; // since 2.0.0

    /** The coverage of the modified lines. */
    private final List<? extends Value> modifiedFilesCoverage;

    /** The coverage delta of the modified lines. */
    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    @SuppressFBWarnings(value = "NP_NONNULL_FIELD_NOT_INITIALIZED_IN_CONSTRUCTOR", justification = "Not used anymore")
    private transient NavigableMap<Metric, Difference> modifiedFilesCoverageDifference;
    private /* almost final */ List<Difference> modifiedFilesDifferences; // since 2.0.0

    /** The indirect coverage changes of the associated change request with respect to the reference build. */
    private final List<? extends Value> indirectCoverageChanges;

    static {
        CoverageXmlStream.registerConverters(XSTREAM2);

        registerValueListConverters(XSTREAM2);
    }

    static void registerValueListConverters(final XStream2 xstream) {
        registerMapConverter("difference", xstream);
        registerMapConverter("modifiedLinesCoverageDifference", xstream);
        registerMapConverter("modifiedFilesCoverageDifference", xstream);
    }

    private static void registerMapConverter(final String difference, final XStream2 xstream) {
        xstream.registerLocalConverter(CoverageBuildAction.class, difference, new MetricFractionMapConverter());
    }

    /**
     * Creates a new instance of {@link CoverageBuildAction}.
     *
     * @param owner
     *         the associated build that created the statistics
     * @param id
     *         ID (URL) of the results
     * @param optionalName
     *         optional name that overrides the default name of the results
     * @param icon
     *         name of the icon that should be used in actions and views
     * @param result
     *         the coverage tree as a result to persist with this action
     * @param qualityGateResult
     *         status of the quality gates
     * @param log
     *         the logging statements of the recording step
     */
    public CoverageBuildAction(final Run<?, ?> owner, final String id, final String optionalName, final String icon,
            final Node result, final QualityGateResult qualityGateResult, final FilteredLog log) {
        this(owner, id, optionalName, icon, result, qualityGateResult, log,
                NO_REFERENCE_BUILD, NO_VALUES, NO_VALUES, NO_VALUES, NO_VALUES, NO_VALUES, NO_VALUES);
    }

    /**
     * Creates a new instance of {@link CoverageBuildAction}.
     *
     * @param owner
     *         the associated build that created the statistics
     * @param id
     *         ID (URL) of the results
     * @param optionalName
     *         optional name that overrides the default name of the results
     * @param icon
     *         name of the icon that should be used in actions and views
     * @param result
     *         the coverage tree as a result to persist with this action
     * @param qualityGateResult
     *         status of the quality gates
     * @param log
     *         the logging statements of the recording step
     * @param referenceBuildId
     *         the ID of the reference build
     * @param delta
     *         delta of this build's coverages with respect to the reference build
     * @param modifiedLinesCoverage
     *         the coverages filtered by modified lines of the associated change request
     * @param modifiedLinesCoverageDifference
     *         difference between the project coverage and the modified lines coverage of the current build
     * @param modifiedFilesCoverage
     *         the coverages filtered by changed files of the associated change request
     * @param modifiedFilesCoverageDifference
     *         difference between the project coverage and the modified files coverage of the current build
     * @param indirectCoverageChanges
     *         the indirect coverage changes of the associated change request with respect to the reference build
     */
    @SuppressWarnings("checkstyle:ParameterNumber")
    public CoverageBuildAction(final Run<?, ?> owner, final String id, final String optionalName, final String icon,
            final Node result, final QualityGateResult qualityGateResult, final FilteredLog log,
            final String referenceBuildId,
            final List<? extends Difference> delta,
            final List<? extends Value> modifiedLinesCoverage,
            final List<? extends Difference> modifiedLinesCoverageDifference,
            final List<? extends Value> modifiedFilesCoverage,
            final List<? extends Difference> modifiedFilesCoverageDifference,
            final List<? extends Value> indirectCoverageChanges) {
        this(owner, id, optionalName, icon, result, qualityGateResult, log, referenceBuildId, delta,
                modifiedLinesCoverage,
                modifiedLinesCoverageDifference, modifiedFilesCoverage, modifiedFilesCoverageDifference,
                indirectCoverageChanges,
                true);
    }

    @VisibleForTesting
    @SuppressWarnings("checkstyle:ParameterNumber")
    CoverageBuildAction(final Run<?, ?> owner, final String id, final String name, final String icon,
            final Node result, final QualityGateResult qualityGateResult, final FilteredLog log,
            final String referenceBuildId,
            final List<? extends Difference> differences,
            final List<? extends Value> modifiedLinesCoverage,
            final List<? extends Difference> modifiedLinesDifferences,
            final List<? extends Value> modifiedFilesCoverage,
            final List<? extends Difference> modifiedFilesDifferences,
            final List<? extends Value> indirectCoverageChanges,
            final boolean canSerialize) {
        super(owner, result, false);

        this.id = id;
        this.name = name;
        this.icon = icon;
        this.log = log;

        projectValues = result.aggregateValues();
        this.qualityGateResult = qualityGateResult;
        this.referenceBuildId = referenceBuildId;

        this.differences = copy(differences);
        this.modifiedLinesCoverage = copy(modifiedLinesCoverage);
        this.modifiedLinesDifferences = copy(modifiedLinesDifferences);
        this.modifiedFilesCoverage = copy(modifiedFilesCoverage);
        this.modifiedFilesDifferences = copy(modifiedFilesDifferences);
        this.indirectCoverageChanges = copy(indirectCoverageChanges);

        if (canSerialize) {
            createXmlStream().write(owner.getRootDir().toPath().resolve(getBuildResultBaseName()), result);
        }
    }

    private <T> List<T> copy(final List<? extends T> list) {
        return new ArrayList<>(list); // do not use immutable collections to simplify serialization
    }

    @Serial
    @Override
    protected Object readResolve() {
        super.readResolve();

        if (difference == null) {
            difference = new TreeMap<>();
        }
        if (modifiedLinesCoverageDifference == null) {
            modifiedLinesCoverageDifference = new TreeMap<>();
        }
        if (modifiedFilesCoverageDifference == null) {
            modifiedFilesCoverageDifference = new TreeMap<>();
        }
        if (differences == null) { // before 2.0
            differences = difference.values().stream().toList();
        }
        if (modifiedFilesDifferences == null) { // before 2.0
            modifiedFilesDifferences = modifiedFilesCoverageDifference.values().stream().toList();
        }
        if (modifiedLinesDifferences == null) { // before 2.0
            modifiedLinesDifferences = modifiedLinesCoverageDifference.values().stream().toList();
        }

        return this;
    }

    /**
     * Returns the actual name of the tool. If no user-defined name is given, then the default name is returned.
     *
     * @return the name
     */
    private String getActualName() {
        return StringUtils.defaultIfBlank(name, Messages.Coverage_Link_Name());
    }

    public FilteredLog getLog() {
        return log;
    }

    public QualityGateResult getQualityGateResult() {
        return qualityGateResult;
    }

    public ElementFormatter getFormatter() {
        return FORMATTER;
    }

    public CoverageStatistics getStatistics() {
        return new CoverageStatistics(projectValues, differences, modifiedLinesCoverage, modifiedLinesDifferences,
                modifiedFilesCoverage, modifiedFilesDifferences);
    }

    /**
     * Returns the supported baselines.
     *
     * @return all supported baselines
     */
    @SuppressWarnings("unused") // Called by jelly view
    public List<Baseline> getBaselines() {
        return List.of(Baseline.PROJECT, Baseline.MODIFIED_FILES, Baseline.MODIFIED_LINES, Baseline.INDIRECT);
    }

    /**
     * Returns whether a delta metric for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     *
     * @return {@code true} if a delta is available for the specified metric, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasBaselineResult(final Baseline baseline) {
        return !getValues(baseline).isEmpty();
    }

    /**
     * Returns the associate delta baseline for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the delta baseline for
     *
     * @return the delta baseline
     * @throws NoSuchElementException
     *         if this baseline does not provide a delta baseline
     */
    @SuppressWarnings("unused") // Called by jelly view
    public Baseline getDeltaBaseline(final Baseline baseline) {
        if (baseline == Baseline.PROJECT) {
            return Baseline.PROJECT_DELTA;
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return Baseline.MODIFIED_LINES_DELTA;
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return Baseline.MODIFIED_FILES_DELTA;
        }
        if (baseline == Baseline.INDIRECT) {
            return Baseline.INDIRECT;
        }
        throw new NoSuchElementException("No delta baseline for this baseline: " + baseline);
    }

    /**
     * Returns the title text for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the title for
     *
     * @return the title
     */
    public String getTitle(final Baseline baseline) {
        if (hasDelta(baseline)) {
            return getDeltaBaseline(baseline).getTitle();
        }
        else {
            return baseline.getTitle();
        }
    }

    /**
     * Returns all available values for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the values for
     *
     * @return the available values
     * @throws NoSuchElementException
     *         if this baseline does not provide values
     */
    // Called by jelly view
    public List<Value> getAllValues(final Baseline baseline) {
        return getValueStream(baseline).collect(Collectors.toList());
    }

    /**
     * Returns all available deltas for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the deltas for
     *
     * @return the available values
     * @throws NoSuchElementException
     *         if this baseline does not provide deltas
     */
    public List<Difference> getAllDeltas(final Baseline baseline) {
        if (baseline == Baseline.PROJECT_DELTA) {
            return differences;
        }
        else if (baseline == Baseline.MODIFIED_LINES_DELTA) {
            return modifiedLinesDifferences;
        }
        else if (baseline == Baseline.MODIFIED_FILES_DELTA) {
            return modifiedFilesDifferences;
        }
        throw new NoSuchElementException("No delta baseline: " + baseline);
    }

    /**
     * Returns all important values for the specified baseline.
     *
     * @param baseline
     *         the baseline to get the values for
     *
     * @return the available values
     * @throws NoSuchElementException
     *         if this baseline does not provide values
     */
    // Called by jelly view
    public List<Value> getValues(final Baseline baseline) {
        return filterImportantMetrics(getValueStream(baseline));
    }

    /**
     * Returns whether this action represents results with coverage metrics. Otherwise, this action represents software
     * metrics.
     *
     * @return {@code true} if this action represents coverage metrics, {@code false} if this action represents software
     *         metrics
     */
    public boolean hasCoverage() {
        return getAllValues(Baseline.PROJECT).stream().map(Value::getMetric).anyMatch(Metric::isCoverage);
    }

    /**
     * Returns the value for the specified metric, if available.
     *
     * @param baseline
     *         the baseline to get the value for
     * @param metric
     *         the metric to get the value for
     *
     * @return the optional value
     */
    public Optional<Value> getValueForMetric(final Baseline baseline, final Metric metric) {
        return getAllValues(baseline).stream()
                .filter(value -> value.getMetric() == metric)
                .findFirst();
    }

    private List<Value> filterImportantMetrics(final Stream<? extends Value> values) {
        return values.filter(v -> getMetricsForSummary().contains(v.getMetric()))
                .limit(MAX_METRICS_COUNT_IN_SUMMARY)
                .collect(Collectors.toList());
    }

    private Stream<? extends Value> getValueStream(final Baseline baseline) {
        Stream<? extends Value> stream;
        if (baseline == Baseline.PROJECT) {
            stream = projectValues.stream();
        }
        else if (baseline == Baseline.MODIFIED_LINES) {
            stream = modifiedLinesCoverage.stream();
        }
        else if (baseline == Baseline.MODIFIED_FILES) {
            stream = modifiedFilesCoverage.stream();
        }
        else if (baseline == Baseline.INDIRECT) {
            stream = indirectCoverageChanges.stream();
        }
        else {
            throw new NoSuchElementException("No such baseline: " + baseline);
        }
        return stream.sorted();
    }

    /**
     * Returns whether a delta metric for the specified baseline exists.
     *
     * @param baseline
     *         the baseline to use
     *
     * @return {@code true} if a delta is available for the specified baseline, {@code false} otherwise
     */
    @SuppressWarnings("unused") // Called by jelly view
    public boolean hasDelta(final Baseline baseline) {
        return baseline == Baseline.PROJECT || baseline == Baseline.MODIFIED_LINES
                || baseline == Baseline.MODIFIED_FILES;
    }

    /**
     * Returns whether a delta metric for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a delta is available for the specified metric, {@code false} otherwise
     */
    public boolean hasDelta(final Baseline baseline, final Metric metric) {
        if (baseline == Baseline.PROJECT) {
            return containsMetric(metric, differences);
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return isLineOrBranchCoverage(metric) && containsMetric(metric, modifiedLinesDifferences);
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return isLineOrBranchCoverage(metric) && containsMetric(metric, modifiedFilesDifferences);
        }
        if (baseline == Baseline.INDIRECT) {
            return false;
        }
        throw new NoSuchElementException("No such baseline: " + baseline);
    }

    private boolean isLineOrBranchCoverage(final Metric metric) {
        return Set.of(Metric.BRANCH, Metric.LINE).contains(metric);
    }

    private boolean containsMetric(final Metric metric, final List<Difference> values) {
        return values.stream().map(Difference::getMetric).anyMatch(metric::equals);
    }

    /**
     * Returns whether a delta metric for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a delta is available for the specified metric, {@code false} otherwise
     */
    public Optional<Value> getDelta(final Baseline baseline, final Metric metric) {
        if (baseline == Baseline.PROJECT) {
            return findDelta(metric, differences);
        }
        if (baseline == Baseline.MODIFIED_LINES) {
            return findDelta(metric, modifiedLinesDifferences);
        }
        if (baseline == Baseline.MODIFIED_FILES) {
            return findDelta(metric, modifiedFilesDifferences);
        }
        return Optional.empty();
    }

    private Optional<Value> findDelta(final Metric metric, final List<Difference> values) {
        return values.stream().filter(d -> d.getMetric() == metric).findAny().map(Value.class::cast);
    }

    /**
     * Returns whether a value for the specified metric exists.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return {@code true} if a value is available for the specified metric, {@code false} otherwise
     */
    public boolean hasValue(final Baseline baseline, final Metric metric) {
        return getAllValues(baseline).stream()
                .anyMatch(v -> v.getMetric() == metric);
    }

    /**
     * Returns a formatted and localized String representation of the value for the specified metric (with respect to
     * the given baseline).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the delta for
     *
     * @return the formatted value
     */
    public String formatValue(final Baseline baseline, final Metric metric) {
        var value = getValueForMetric(baseline, metric);
        return value.isPresent() ? FORMATTER.formatValue(value.get()) : Messages.Coverage_Not_Available();
    }

    /**
     * Returns a formatted and localized String representation of the delta for the specified metric (with respect to
     * the given baseline).
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to get the delta for
     *
     * @return the delta metric
     */
    @SuppressWarnings({"unused", "OptionalGetWithoutIsPresent"}) // Called by jelly view
    public String formatDelta(final Baseline baseline, final Metric metric) {
        var currentLocale = Functions.getCurrentLocale();
        if (baseline == Baseline.PROJECT && hasDelta(baseline, metric)) {
            return FORMATTER.formatDelta(metric, findDelta(metric, differences).get(), currentLocale);
        }
        if (baseline == Baseline.MODIFIED_LINES && hasDelta(baseline, metric)) {
            return FORMATTER.formatDelta(metric, findDelta(metric, modifiedLinesDifferences).get(), currentLocale);
        }
        if (baseline == Baseline.MODIFIED_FILES && hasDelta(baseline, metric)) {
            return FORMATTER.formatDelta(metric, findDelta(metric, modifiedFilesDifferences).get(), currentLocale);
        }
        return Messages.Coverage_Not_Available();
    }

    /**
     * Returns whether the trend of the values for the specific metric is positive or negative.
     *
     * @param baseline
     *         the baseline to use
     * @param metric
     *         the metric to check
     *
     * @return a positive value if the trend is positive, a negative value if the trend is negative, or {@code 0} if there is no significant change in the trend
     */
    @SuppressWarnings("unused") // Called by jelly view
    public double getTrend(final Baseline baseline, final Metric metric) {
        var delta = getDelta(baseline, metric);
        if (delta.isPresent()) {
            double deltaValue = delta.get().asDouble();
            if (-0.001 < deltaValue && deltaValue < 0.001) {
                // for var(--text-color)
                return 0;
            }
            else {
                // for var(--red or --green)
                return deltaValue;
            }
        }
        return 0; // default to zero
    }

    /**
     * Returns the visible metrics for the project summary.
     *
     * @return the metrics to be shown in the project summary
     */
    @VisibleForTesting
    NavigableSet<Metric> getMetricsForSummary() {
        // TODO: establish a useful order
        return new TreeSet<>(
                Set.of(
                        // code coverage
                        Metric.LINE, Metric.BRANCH,
                        // mutation coverage
                        Metric.MUTATION, Metric.TEST_STRENGTH,
                        // software metrics
                        Metric.LOC, Metric.NCSS, Metric.TESTS,
                        Metric.CYCLOMATIC_COMPLEXITY, Metric.COGNITIVE_COMPLEXITY, Metric.NPATH_COMPLEXITY,
                        Metric.MCDC_PAIR, Metric.FUNCTION_CALL));
    }

    /**
     * Returns the possible reference build that has been used to compute the coverage delta.
     *
     * @return the reference build, if available
     */
    public Optional<Run<?, ?>> getReferenceBuild() {
        if (NO_REFERENCE_BUILD.equals(referenceBuildId)) {
            return Optional.empty();
        }
        return new JenkinsFacade().getBuild(referenceBuildId);
    }

    /**
     * Renders the reference build as an HTML link.
     *
     * @return the reference build
     * @see #getReferenceBuild()
     */
    @SuppressWarnings("unused") // Called by jelly view
    public String getReferenceBuildLink() {
        return ReferenceBuild.getReferenceBuildLink(referenceBuildId);
    }

    @Override
    protected AbstractXmlStream<Node> createXmlStream() {
        return new CoverageXmlStream();
    }

    @Override
    protected JobAction<? extends BuildAction<Node>> createProjectAction() {
        return new CoverageJobAction(getOwner().getParent(), getUrlName(), name, icon);
    }

    @Override
    protected String getBuildResultBaseName() {
        return String.format("%s.xml", id);
    }

    @Override
    public CoverageViewModel getTarget() {
        return new CoverageViewModel(getOwner(), getUrlName(), name, getResult(),
                getStatistics(), getQualityGateResult(), getReferenceBuildLink(), log,
                this::createChartModel, this::checkForCoverageData);
    }

    private String createChartModel(final String configuration, final boolean metrics) {
        // TODO: add without optional
        var iterable = new BuildActionIterable<>(CoverageBuildAction.class, Optional.of(this),
                action -> getUrlName().equals(action.getUrlName()), CoverageBuildAction::getStatistics);
        return new JacksonFacade().toJson(TrendChart.createTrendChart(metrics)
                .create(iterable, ChartModelConfiguration.fromJson(configuration)));
    }

    private boolean checkForCoverageData() {
        var iterator = new BuildActionIterable<>(CoverageBuildAction.class, Optional.of(this),
                action -> getUrlName().equals(action.getUrlName()), CoverageBuildAction::getStatistics).iterator();
        if (iterator.hasNext()) {
            var lastResult = iterator.next().getResult();
            return lastResult.getValue(Baseline.PROJECT, Metric.MODULE)
                    .or(() -> lastResult.getValue(Baseline.PROJECT, Metric.PACKAGE))
                    .or(() -> lastResult.getValue(Baseline.PROJECT, Metric.FILE))
                    .or(() -> lastResult.getValue(Baseline.PROJECT, Metric.CLASS))
                    .or(() -> lastResult.getValue(Baseline.PROJECT, Metric.METHOD))
                    .map(value -> value instanceof Coverage && ((Coverage) value).isSet())
                    .orElse(false);
        }
        return false;
    }

    @NonNull
    @Override
    public String getIconFileName() {
        return icon;
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return getActualName();
    }

    @NonNull
    @Override
    public String getUrlName() {
        return id;
    }

    @Override
    public String toString() {
        return String.format("%s (%s): %s", getDisplayName(), getUrlName(), projectValues);
    }
}
