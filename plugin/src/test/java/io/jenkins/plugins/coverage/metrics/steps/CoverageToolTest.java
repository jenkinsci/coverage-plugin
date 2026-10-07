package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.*;

import edu.hm.hafner.coverage.CoverageParser.ParsingException;
import edu.hm.hafner.coverage.CoverageParser.ProcessingMode;
import edu.hm.hafner.util.FilteredLog;
import io.jenkins.plugins.coverage.metrics.steps.CoverageTool.Parser;
import java.io.StringReader;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CoverageToolTest {
    @ParameterizedTest
    @EnumSource(Parser.class)
    void shouldCreateAllRegisteredParsers(final Parser parser) {
        var coverageParser = parser.createParser(ProcessingMode.FAIL_FAST);

        assertThatExceptionOfType(ParsingException.class)
                .isThrownBy(() ->
                        coverageParser.parse(new StringReader(StringUtils.EMPTY), "empty.txt", new FilteredLog()));
    }
}
