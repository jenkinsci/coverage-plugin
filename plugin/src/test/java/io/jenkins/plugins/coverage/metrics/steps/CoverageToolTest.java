package io.jenkins.plugins.coverage.metrics.steps;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import edu.hm.hafner.coverage.CoverageParser.ParsingException;
import edu.hm.hafner.coverage.CoverageParser.ProcessingMode;
import edu.hm.hafner.util.FilteredLog;

import java.io.StringReader;

import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;

import static org.assertj.core.api.Assertions.*;

class CoverageToolTest {
    @ParameterizedTest
    @EnumSource(Parser.class)
    void shouldCreateAllRegisteredParsers(final Parser parser) {
        var coverageParser = parser.createParser(ProcessingMode.FAIL_FAST);

        assertThatExceptionOfType(ParsingException.class).isThrownBy(
                () -> coverageParser.parse(new StringReader(StringUtils.EMPTY), "empty.txt", new FilteredLog()));
    }
}
