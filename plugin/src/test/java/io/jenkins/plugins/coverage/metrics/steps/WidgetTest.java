package io.jenkins.plugins.coverage.metrics.steps;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.util.FilteredLog;

import java.util.List;

import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.model.Baseline;
import io.jenkins.plugins.util.QualityGateResult;
import io.jenkins.plugins.util.QualityGateStatus;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link Widget}.
 *
 * @author Ullrich Hafner
 */
class WidgetTest extends AbstractCoverageTest {
    @Test
    void shouldShowNoQualityGatesMessageWhenNoGateIsActive() {
        var widget = createWidget(createAction("coverage", new QualityGateResult()));

        assertThat(widget.getMessage()).isEqualTo(Messages.Widget_noQualityGates());
        assertThat(widget.getIconColor()).isEqualTo("jenkins-!-success-color");
    }

    @Test
    void shouldShowPassedMessageWhenAllActiveGatesPass() {
        var widget = createWidget(createAction("coverage", new QualityGateResult(QualityGateStatus.PASSED)));

        assertThat(widget.getMessage()).isEqualTo(Messages.Widget_passedQualityGates());
        assertThat(widget.getIconColor()).isEqualTo("jenkins-!-success-color");
    }

    @Test
    void shouldShowSingularMessageForOneFailedGate() {
        var widget = createWidget(createAction("coverage", new QualityGateResult(QualityGateStatus.ERROR)));

        assertThat(widget.getMessage()).isEqualTo(Messages.Widget_failedQualityGate());
        assertThat(widget.getIconColor()).isEqualTo("jenkins-!-warning-color");
    }

    @Test
    void shouldShowPluralMessageForSeveralFailedGates() {
        var widget = createWidget(
                createAction("coverage", new QualityGateResult(QualityGateStatus.ERROR)),
                createAction("mutation", new QualityGateResult(QualityGateStatus.FAILED)));

        assertThat(widget.getMessage()).isEqualTo(Messages.Widget_failedQualityGates(2));
        assertThat(widget.getIconColor()).isEqualTo("jenkins-!-warning-color");
    }

    @Test
    void shouldCountOnlyFailedGatesEvenWhenOthersAreInactiveOrPassing() {
        var widget = createWidget(
                createAction("coverage", new QualityGateResult()),
                createAction("mutation", new QualityGateResult(QualityGateStatus.PASSED)),
                createAction("modified-lines", new QualityGateResult(QualityGateStatus.ERROR)));

        assertThat(widget.getMessage()).isEqualTo(Messages.Widget_failedQualityGate());
    }

    @Test
    void shouldReturnEmptyOptionalAndNoBadgesWhenThereAreNoResults() {
        var widget = createWidget();

        assertThat(widget.getSingleResult()).isEmpty();
        assertThat(widget.getResults()).isEmpty();
        assertThat(widget.getSingleResultMetrics()).isEmpty();
    }

    @Test
    void shouldReturnThePresentResultWhenThereIsExactlyOne() {
        var action = createAction("coverage", new QualityGateResult());
        var widget = createWidget(action);

        assertThat(widget.getSingleResult()).contains(action);
    }

    @Test
    void shouldReturnEmptyOptionalAndNoBadgesWhenThereAreSeveralResults() {
        var widget = createWidget(
                createAction("coverage", new QualityGateResult()),
                createAction("mutation", new QualityGateResult()));

        assertThat(widget.getSingleResult()).isEmpty();
        assertThat(widget.getSingleResultMetrics()).isEmpty();
        assertThat(widget.getResults()).hasSize(2);
    }

    @Test
    void shouldBuildCardMetricBadgesSortedByMetricForTheSingleResult() {
        var action = createAction("coverage", new QualityGateResult());
        var widget = createWidget(action);

        var badges = widget.getSingleResultMetrics();

        // only the card metrics (see OverviewModel#isCardMetric) become badges, in natural metric order
        assertThat(badges).extracting(Widget.MetricBadge::getLabel)
                .containsExactly(Metric.LINE.getLabel(), Metric.BRANCH.getLabel(), Metric.INSTRUCTION.getLabel());

        var lineCoverage = (Coverage) action.getValueForMetric(Baseline.PROJECT, Metric.LINE).orElseThrow();
        var lineBadge = badges.get(0);
        assertThat(lineBadge.getReportId()).isEqualTo("coverage");
        assertThat(lineBadge.getValue()).isEqualTo(action.getFormatter().format(lineCoverage));
        assertThat(lineBadge.getPercentage()).isEqualTo(lineCoverage.getCoveredPercentage().toDouble());
        assertThat(lineBadge.getStyle()).contains("background-image: linear-gradient(90deg,");
        assertThat(lineBadge.getTooltip()).contains(Metric.LINE.getDisplayName());
    }

    @Test
    void shouldReturnTheFootstepsSymbol() {
        assertThat(createWidget().getSymbol()).isEqualTo("symbol-footsteps-outline plugin-ionicons-api");
    }

    private Widget createWidget(final CoverageBuildAction... actions) {
        return new Widget(List.of(actions));
    }

    private CoverageBuildAction createAction(final String id,
            final QualityGateResult qualityGateResult) {
        var node = readJacocoResult(JACOCO_CODING_STYLE_FILE);
        return new CoverageBuildAction(mock(Run.class), id, "", "", node, qualityGateResult,
                new FilteredLog("Errors"), "-",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), false);
    }
}
