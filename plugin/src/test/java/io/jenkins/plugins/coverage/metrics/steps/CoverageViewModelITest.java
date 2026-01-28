package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.parser.JacocoParser;
import edu.hm.hafner.util.FilteredLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;

import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.AbstractCoverageITest;
import io.jenkins.plugins.coverage.metrics.AbstractCoverageTest;
import io.jenkins.plugins.util.QualityGateResult;

import static io.jenkins.plugins.coverage.metrics.steps.CoverageViewModel.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the class {@link CoverageViewModel} that require a running Jenkins instance.
 * These tests verify permission-protected functionality like source code viewing.
 *
 * @author Akash Manna
 */
class CoverageViewModelITest extends AbstractCoverageITest {
    @Test
    void shouldReturnEmptySourceViewForExistingLinkButMissingSourceFile() {
        var model = createModelFromCodingStyleReport();

        String hash = String.valueOf("PathUtil.java".hashCode());
        assertThat(model.getSourceCode(hash, ABSOLUTE_COVERAGE_TABLE_ID)).isEqualTo("N/A");
        assertThat(model.getSourceCode(hash, MODIFIED_LINES_COVERAGE_TABLE_ID)).isEqualTo("N/A");
        assertThat(model.getSourceCode(hash, INDIRECT_COVERAGE_TABLE_ID)).isEqualTo("N/A");
    }

    private CoverageViewModel createModelFromCodingStyleReport() {
        return createModel(readJacocoResult("jacoco-codingstyle.xml"));
    }

    private Node readJacocoResult(final String fileName) {
        FilteredLog log = new FilteredLog("Errors");
        try (InputStream stream = CoverageViewModelITest.class.getResourceAsStream(fileName);
                InputStreamReader reader = stream == null ? null : new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            if (reader == null) {
                throw new AssertionError("Test resource not found: " + fileName);
            }
            var node = new JacocoParser().parse(reader, fileName, log);
            node.splitPackages();
            return node;
        }
        catch (IOException e) {
            throw new AssertionError("Failed to read test resource: " + fileName, e);
        }
    }

    private CoverageViewModel createModel(final Node node) {
        return new CoverageViewModel(mock(Run.class), "id", StringUtils.EMPTY,
                node, AbstractCoverageTest.createStatistics(), new QualityGateResult(), "-", new FilteredLog("Errors"),
                Function.identity(), Function.identity());
    }
}
