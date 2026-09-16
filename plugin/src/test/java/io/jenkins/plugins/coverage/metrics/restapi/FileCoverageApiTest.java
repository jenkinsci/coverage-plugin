package io.jenkins.plugins.coverage.metrics.restapi;

import org.junit.jupiter.api.Test;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.PackageNode;
import edu.hm.hafner.coverage.Value;

import java.io.IOException;
import java.io.StringWriter;

import org.kohsuke.stapler.export.ExportConfig;
import org.kohsuke.stapler.export.Flavor;
import org.kohsuke.stapler.export.Model;
import org.kohsuke.stapler.export.ModelBuilder;

import io.jenkins.plugins.coverage.metrics.AbstractModifiedFilesCoverageTest;

import static net.javacrumbs.jsonunit.assertj.JsonAssertions.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Tests {@link FileCoverageApi}.
 */
class FileCoverageApiTest extends AbstractModifiedFilesCoverageTest {
    /**
     * Verifies that only files with coverage relevant changes are reported, and that their whole-file (absolute)
     * coverage values are preserved.
     */
    @Test
    void shouldReportWholeFileCoverageOnlyForModifiedFiles() {
        var files = new FileCoverageApi(createCoverageTree()).getFiles();

        assertThat(files).extracting(FileCoverage::getFullyQualifiedFileName)
                .containsExactly(getPathOfFileWithModifiedLines());
        assertThat(files.get(0).getMetrics()).containsKey("line");
        assertThat(files.get(0).getMetrics().get("line")).matches("\\d+(\\.\\d+)?%");
    }

    /**
     * Verifies that the exported bean is serialized by Stapler into a non-empty {@code files} array with the expected
     * shape.
     */
    @Test
    void shouldExportFilesAsJson() throws IOException {
        var api = new FileCoverageApi(createCoverageTree());

        var json = exportToJson(api);

        assertThatJson(json).node("files").isArray().isNotEmpty();
        assertThatJson(json).node("files[0].fullyQualifiedFileName").isEqualTo(getPathOfFileWithModifiedLines());
        assertThatJson(json).node("files[0].metrics.line").isString();
    }

    /**
     * Verifies that all set coverage metrics are reported keyed by their tag name, sorted lexicographically, and that
     * non-coverage values as well as unset coverage values are skipped.
     */
    @Test
    void shouldReportAllSetCoverageMetrics() {
        var fileNode = new FileNode("Test.java", "path");
        fileNode.addModifiedLines(1);
        fileNode.addCounters(1, 1, 0);
        fileNode.addValue(new CoverageBuilder().withMetric(Metric.LINE).withCovered(88).withMissed(12).build());
        fileNode.addValue(new CoverageBuilder().withMetric(Metric.BRANCH).withCovered(9).withMissed(1).build());
        fileNode.addValue(new CoverageBuilder().withMetric(Metric.INSTRUCTION).withCovered(80).withMissed(20).build());
        fileNode.addValue(new Value(Metric.LOC, 1000));
        fileNode.addValue(new Value(Metric.CYCLOMATIC_COMPLEXITY, 150));
        fileNode.addValue(Coverage.nullObject(Metric.MCDC_PAIR));
        var parentNode = new PackageNode("package");
        parentNode.addChild(fileNode);

        var files = new FileCoverageApi(parentNode).getFiles();

        assertThat(files).hasSize(1);
        var metrics = files.get(0).getMetrics();
        assertThat(metrics.keySet()).containsExactly("branch", "instruction", "line");
        assertThat(metrics).doesNotContainKeys("loc", "cyclomatic-complexity", "mcdc-pair");
        assertThat(metrics.values()).allSatisfy(value -> assertThat(value).matches("\\d+(\\.\\d+)?%"));
    }

    /**
     * Verifies that files with modified lines but no covered modified lines are not reported at all.
     */
    @Test
    void shouldReportNothingWhenNoFileHasCoveredModifiedLines() {
        var fileNode = new FileNode("Test.java", "path");
        fileNode.addModifiedLines(1);
        var parentNode = new PackageNode("package");
        parentNode.addChild(fileNode);

        assertThat(new FileCoverageApi(parentNode).getFiles()).isEmpty();
    }

    private String exportToJson(final FileCoverageApi api) throws IOException {
        Model<FileCoverageApi> model = new ModelBuilder().get(FileCoverageApi.class);
        try (var writer = new StringWriter()) {
            model.writeTo(api, Flavor.JSON.createDataWriter(api, writer, new ExportConfig()));
            return writer.toString();
        }
    }
}
