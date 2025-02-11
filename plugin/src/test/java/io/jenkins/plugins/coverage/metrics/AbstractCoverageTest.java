package io.jenkins.plugins.coverage.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.junitpioneer.jupiter.DefaultLocale;

import edu.hm.hafner.coverage.Coverage.CoverageBuilder;
import edu.hm.hafner.coverage.CoverageParser;
import edu.hm.hafner.coverage.Difference;
import edu.hm.hafner.coverage.Metric;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;
import edu.hm.hafner.coverage.parser.JacocoParser;
import edu.hm.hafner.coverage.parser.VectorCastParser;
import edu.hm.hafner.util.FilteredLog;
import edu.hm.hafner.util.ResourceTest;
import edu.hm.hafner.util.SecureXmlParserFactory.ParsingException;

import io.jenkins.plugins.coverage.metrics.model.CoverageStatistics;

/**
 * Base class for coverage tests that work on real coverage reports.
 *
 * @author Ullrich Hafner
 */
@DefaultLocale("en")
@SuppressWarnings("checkstyle:JavadocVariable")
public abstract class AbstractCoverageTest extends ResourceTest {
    public static final String JACOCO_ANALYSIS_MODEL_FILE = "jacoco-analysis-model.xml";
    public static final int JACOCO_ANALYSIS_MODEL_COVERED = 5531;
    public static final int JACOCO_ANALYSIS_MODEL_MISSED = 267;
    public static final int JACOCO_ANALYSIS_MODEL_TOTAL
            = JACOCO_ANALYSIS_MODEL_COVERED + JACOCO_ANALYSIS_MODEL_MISSED;

    public static final String JACOCO_CODING_STYLE_FILE = "jacoco-codingstyle.xml";
    public static final int JACOCO_CODING_STYLE_COVERED = 294;
    public static final int JACOCO_CODING_STYLE_MISSED = 29;
    public static final int JACOCO_CODING_STYLE_TOTAL
            = JACOCO_CODING_STYLE_COVERED + JACOCO_CODING_STYLE_MISSED;
    private final FilteredLog log = new FilteredLog("Errors");

    /**
     * Reads and parses a JaCoCo coverage report.
     *
     * @param fileName
     *         the name of the coverage report file
     *
     * @return the parsed coverage tree
     */
    protected Node readJacocoResult(final String fileName) {
        return readResult(fileName, new JacocoParser());
    }

    protected Node readVectorCastResult(final String fileName) {
        return readResult(fileName, new VectorCastParser(CoverageParser.ProcessingMode.FAIL_FAST));
    }

    /**
     * Reads and parses a JaCoCo coverage report.
     *
     * @param fileName
     *         the name of the coverage report file
     * @param parser
     *         the parser to use
     *
     * @return the parsed coverage tree
     */
    protected Node readResult(final String fileName, final CoverageParser parser) {
        try {
            var node = parser.parse(Files.newBufferedReader(getResourceAsFile(fileName)), fileName, log);
            node.splitPackages();
            return node;
        }
        catch (ParsingException | IOException exception) {
            throw new AssertionError(exception);
        }
    }

    protected FilteredLog getLog() {
        return log;
    }

    /**
     * Creates coverage statistics that can be used in test cases.
     *
     * @return the coverage statistics
     */
    public static CoverageStatistics createStatistics() {
        return new CoverageStatistics(fillValues(), fillDeltas(),
                fillValues(), fillDeltas(),
                fillValues(), fillDeltas());
    }

    /**
     * Creates coverage statistics that can be used in test cases.
     *
     * @return the coverage statistics
     */
    public static CoverageStatistics createOnlyProjectStatistics() {
        return new CoverageStatistics(fillValues(),
                List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static List<Value> fillValues() {
        var builder = new CoverageBuilder();
        return List.of(
                builder.withMetric(Metric.FILE).withCovered(3).withMissed(1).build(),
                builder.withMetric(Metric.LINE).withCovered(2).withMissed(2).build(),
                builder.withMetric(Metric.BRANCH).withCovered(9).withMissed(1).build(),
                new Value(Metric.CYCLOMATIC_COMPLEXITY, 150),
                new Value(Metric.NPATH_COMPLEXITY, 15),
                new Value(Metric.LOC, 1000)
        );
    }

    private static List<Difference> fillDeltas() {
        return List.of(new Difference(Metric.FILE, -10),
                new Difference(Metric.LINE, 5),
                new Difference(Metric.CYCLOMATIC_COMPLEXITY, -10),
                new Difference(Metric.LOC, 5));
    }
}
