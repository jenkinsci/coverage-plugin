package io.jenkins.plugins.coverage.metrics.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import edu.hm.hafner.util.FilteredLog;
import hudson.model.TaskListener;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests the class {@link CoverageLogHandler}.
 *
 * @author Ullrich Hafner
 */
class CoverageLogHandlerTest {
    private static final String INFO_PREFIX = "[Coverage] ";
    private static final String ERROR_PREFIX = "[Coverage] [-ERROR-] ";

    private static final String ERRORS_TITLE = "Errors while recording code coverage:";
    private static final String INFO_MESSAGE = "Successfully parsed file";
    private static final String ERROR_MESSAGE = "No files found for pattern";
    private static final String QUALITY_GATE_ENTRY =
            "   - [Whole project - Line Coverage]: ≪Unstable≫ - (Actual value: 95.39%, Quality gate: 100.00)";

    /**
     * Verifies that all info and error messages are printed if the quiet mode is disabled.
     */
    @Test
    void shouldLogAllMessagesInVerboseMode() {
        var console = new ByteArrayOutputStream();
        var handler = CoverageLogHandler.create(createListener(console), false);

        var log = new FilteredLog(ERRORS_TITLE);
        log.logInfo(INFO_MESSAGE);
        log.logError(ERROR_MESSAGE);
        log.logInfo(QUALITY_GATE_ENTRY);
        handler.log(log);
        handler.log("Skipping execution since overall result is '%s'", "FAILURE");

        assertThat(asString(console))
                .contains(
                        INFO_PREFIX + INFO_MESSAGE,
                        ERROR_PREFIX + ERRORS_TITLE,
                        ERROR_PREFIX + ERROR_MESSAGE,
                        INFO_PREFIX + QUALITY_GATE_ENTRY,
                        INFO_PREFIX + "Skipping execution since overall result is 'FAILURE'");
    }

    /**
     * Verifies that errors are still printed in quiet mode, whereas informational messages are skipped.
     */
    @Test
    void shouldLogOnlyErrorsInQuietMode() {
        var console = new ByteArrayOutputStream();
        var handler = CoverageLogHandler.create(createListener(console), true);

        var log = new FilteredLog(ERRORS_TITLE);
        log.logInfo(INFO_MESSAGE);
        log.logError(ERROR_MESSAGE);
        handler.log(log);
        handler.log("Skipping execution since overall result is '%s'", "FAILURE");

        assertThat(asString(console))
                .contains(ERROR_PREFIX + ERRORS_TITLE, ERROR_PREFIX + ERROR_MESSAGE)
                .doesNotContain(INFO_MESSAGE, "Skipping execution");
    }

    /**
     * Verifies that a failure of the step and the results of the quality gates are still printed in quiet mode.
     *
     * @param message
     *         the message that must not be suppressed
     */
    @ParameterizedTest(name = "[{index}] Important message: {0}")
    @ValueSource(
            strings = {
                CoverageRecorder.FAIL_ON_ERROR_MESSAGE,
                CoverageRecorder.IGNORE_ERRORS_MESSAGE,
                "-> Some quality gates have been missed: overall result is UNSTABLE",
                "-> All quality gates have been passed",
                "-> Details for each quality gate:",
                QUALITY_GATE_ENTRY
            })
    void shouldKeepImportantMessagesInQuietMode(final String message) {
        var console = new ByteArrayOutputStream();
        var handler = CoverageLogHandler.create(createListener(console), true);

        var log = new FilteredLog(ERRORS_TITLE);
        log.logInfo(INFO_MESSAGE);
        log.logInfo(message);
        handler.log(log);

        assertThat(asString(console)).isEqualToIgnoringNewLines(INFO_PREFIX + message);
    }

    /**
     * Verifies that subsequent calls print only those messages that have not been printed before.
     *
     * @param quiet
     *         determines whether the quiet mode is enabled
     */
    @ParameterizedTest(name = "[{index}] Quiet mode: {0}")
    @ValueSource(booleans = {true, false})
    void shouldLogMessagesOnlyOnce(final boolean quiet) {
        var console = new ByteArrayOutputStream();
        var handler = CoverageLogHandler.create(createListener(console), quiet);

        var log = new FilteredLog(ERRORS_TITLE);
        log.logError(ERROR_MESSAGE);
        log.logInfo(QUALITY_GATE_ENTRY);
        handler.log(log);
        handler.log(log);

        log.logError("Second error");
        log.logInfo("-> All quality gates have been passed");
        handler.log(log);
        handler.log(log);

        assertThat(asString(console))
                .containsOnlyOnce(ERROR_PREFIX + ERRORS_TITLE)
                .containsOnlyOnce(ERROR_PREFIX + ERROR_MESSAGE)
                .containsOnlyOnce(ERROR_PREFIX + "Second error")
                .containsOnlyOnce(INFO_PREFIX + QUALITY_GATE_ENTRY)
                .containsOnlyOnce(INFO_PREFIX + "-> All quality gates have been passed");
    }

    private TaskListener createListener(final ByteArrayOutputStream console) {
        TaskListener listener = mock(TaskListener.class);
        when(listener.getLogger()).thenReturn(new PrintStream(console, true, StandardCharsets.UTF_8));
        return listener;
    }

    private String asString(final ByteArrayOutputStream console) {
        return console.toString(StandardCharsets.UTF_8);
    }
}
