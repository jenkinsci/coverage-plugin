package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.Issue;

import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.util.FilteredLog;

import java.util.List;
import java.util.function.Function;

import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractModifiedFilesCoverageTest;
import io.jenkins.plugins.datatables.TableColumn;
import io.jenkins.plugins.util.QualityGateResult;

import static io.jenkins.plugins.coverage.metrics.steps.CoverageViewModel.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests the delta columns of {@link CoverageTableModel} and its subclasses.
 *
 * @author Akash Manna
 */
class CoverageTableModelITest extends AbstractModifiedFilesCoverageTest {
    private static final String LINE_DELTA_COLUMN = "lineCoverageDelta";
    private static final String BRANCH_DELTA_COLUMN = "branchCoverageDelta";
    private static final List<String> TABLES_WITH_DELTA_COLUMNS
            = List.of(ABSOLUTE_COVERAGE_TABLE_ID, MODIFIED_LINES_COVERAGE_TABLE_ID);

    @Test
    void shouldShowDeltaColumnsForAFreshlyComputedTree() {
        assertThatDeltaColumnsAreShown(createCoverageTree());
    }

    /**
     * The coverage tree is cached using a weak reference only, i.e., it will be restored from disk after a restart of
     * Jenkins or as soon as the cached tree has been garbage collected. Even then the delta columns of the file tables
     * must still be shown.
     */
    @Test @Issue("https://github.com/jenkinsci/coverage-plugin/issues/643")
    void shouldShowDeltaColumnsForATreeThatHasBeenRestoredFromDisk() {
        assertThatDeltaColumnsAreShown(saveAndRestore(createCoverageTree()));
    }

    private Node saveAndRestore(final Node tree) {
        var saved = createTempFile();

        var xmlStream = new CoverageXmlStream();
        xmlStream.write(saved, tree);

        return xmlStream.read(saved);
    }

    private void assertThatDeltaColumnsAreShown(final Node tree) {
        var model = createViewModel(tree);

        for (String tableId : TABLES_WITH_DELTA_COLUMNS) {
            var definitions = model.getTableModel(tableId).getColumns().stream()
                    .map(TableColumn::getDefinition)
                    .toList();

            assertThat(definitions)
                    .as("Delta columns of table '%s'", tableId)
                    .anyMatch(definition -> definition.contains(LINE_DELTA_COLUMN))
                    .anyMatch(definition -> definition.contains(BRANCH_DELTA_COLUMN));
        }
    }

    private CoverageViewModel createViewModel(final Node node) {
        return new CoverageViewModel(mock(Run.class), "id", StringUtils.EMPTY,
                node, createStatistics(), new QualityGateResult(), "-", new FilteredLog("Errors"),
                Function.identity(), Function.identity());
    }
}
