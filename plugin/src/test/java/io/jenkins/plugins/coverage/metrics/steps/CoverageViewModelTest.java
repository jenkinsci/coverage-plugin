package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.ModuleNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Function;

import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTableModel.CoverageRow;
import io.jenkins.plugins.util.QualityGateResult;

import static io.jenkins.plugins.coverage.metrics.steps.CoverageViewModel.*;
import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the class {@link CoverageViewModel}.
 *
 * @author Ullrich Hafner
 * @author Florian Orendi
 */
@SuppressWarnings("PMD.TooManyStaticImports")
class CoverageViewModelTest extends AbstractCoverageTest {
    @Test
    void shouldReturnEmptySourceViewForExistingLinkButMissingSourceFile() {
        var model = createModelFromCodingStyleReport();

        String hash = String.valueOf("PathUtil.java".hashCode());
        assertThat(model.getSourceCode(hash, ABSOLUTE_COVERAGE_TABLE_ID)).isEqualTo("n/a");
        assertThat(model.getSourceCode(hash, MODIFIED_LINES_COVERAGE_TABLE_ID)).isEqualTo("n/a");
        assertThat(model.getSourceCode(hash, INDIRECT_COVERAGE_TABLE_ID)).isEqualTo("n/a");
    }

    @Test
    @SuppressWarnings("PMD.ConfusingArgumentToVarargsMethod")
    void shouldReportOverview() {
        var model = createModelFromCodingStyleReport();

        var overview = model.getOverview();

        var expectedMetrics = new String[]{"Package", "File", "Class", "Method", "Line", "Branch", "Instruction"};
        assertThat(overview.getMetrics()).containsExactly(expectedMetrics);

        var expectedCovered = List.of(4, 7, 15, 97, 294, 109, 1260);
        assertThat(overview.getCovered()).containsExactlyElementsOf(expectedCovered);
        ensureValidPercentages(overview.getCoveredPercentages());

        var expectedMissed = List.of(0, 1, 1, 5, 29, 7, 90);
        assertThat(overview.getMissed()).containsExactlyElementsOf(expectedMissed);
        ensureValidPercentages(overview.getMissedPercentages());

        assertThatJson(overview).node("metrics").isArray().containsExactly(expectedMetrics);
        assertThatJson(overview).node("covered").isArray().containsExactlyElementsOf(expectedCovered);
        assertThatJson(overview).node("missed").isArray().containsExactlyElementsOf(expectedMissed);

        assertThat(model.getTableModel(ABSOLUTE_COVERAGE_TABLE_ID).getRows()).anySatisfy(
                row -> assertThat(row).isInstanceOfSatisfying(CoverageRow.class,
                        coverageRow -> assertThat(coverageRow.getFileName().getDisplay())
                                .contains("title=\"edu/hm/hafner/util/",
                                        "data-bs-toggle=\"tooltip\" data-bs-placement=\"top\""))
        );
    }

    private static void ensureValidPercentages(final List<Double> percentages) {
        assertThat(percentages).allSatisfy(d ->
                assertThat(d).isLessThanOrEqualTo(100.0).isGreaterThanOrEqualTo(0.0));
    }

    @Test
    void shouldProvideIndirectCoverageChanges() {
        var node = createIndirectCoverageChangesNode();

        var model = createModel(node);

        assertThat(model.hasIndirectCoverageChanges()).isTrue();
    }

    private Node createIndirectCoverageChangesNode() {
        var root = new ModuleNode("root");
        for (int file = 0; file < 5; file++) {
            var fileNode = new FileNode("File-" + file, "path");

            for (int line = 0; line < 2; line++) {
                fileNode.addCounters(10 + line, 1, 1);
                fileNode.addIndirectCoverageChange(10 + line, 2);
            }
            root.addChild(fileNode);
        }
        return root;
    }

    @Test
    void shouldProvideRightTableModelById() {
        var model = createModelFromCodingStyleReport();
        assertThat(model.getTableModel(MODIFIED_LINES_COVERAGE_TABLE_ID)).isInstanceOf(
                ModifiedLinesCoverageTableModel.class);
        assertThat(model.getTableModel(INDIRECT_COVERAGE_TABLE_ID)).isInstanceOf(IndirectCoverageChangesTable.class);
        assertThat(model.getTableModel(ABSOLUTE_COVERAGE_TABLE_ID)).isInstanceOf(CoverageTableModel.class);

        assertThatExceptionOfType(NoSuchElementException.class)
                .isThrownBy(() -> model.getTableModel("wrong-id"));
    }

    private CoverageViewModel createModelFromCodingStyleReport() {
        return createModel(readJacocoResult("jacoco-codingstyle.xml"));
    }

    private CoverageViewModel createModel(final Node node) {
        return new CoverageViewModel(mock(Run.class), "id", StringUtils.EMPTY,
                node, createStatistics(), new QualityGateResult(), "-", new FilteredLog("Errors"),
                Function.identity(), Function.identity());
    }
}
