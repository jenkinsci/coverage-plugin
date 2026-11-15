package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.util.FilteredLog;
import hudson.model.TaskListener;
import io.jenkins.plugins.util.LogHandler;
import java.util.List;

/**
 * Writes the messages of the coverage recorder to the console log of a build. There are two different handlers
 * available: a verbose handler that prints all messages, and a quiet handler that prints only the errors and the
 * results of the quality gate evaluation.
 *
 * @author Ullrich Hafner
 */
abstract class CoverageLogHandler {
    private static final String LOGGER_NAME = "Coverage";

    private final LogHandler logHandler;

    /**
     * Creates a new log handler that writes to the console log of the specified listener.
     *
     * @param listener
     *         the task listener that will print all log messages
     * @param quiet
     *         determines whether informational messages should be suppressed
     *
     * @return the log handler
     */
    static CoverageLogHandler create(final TaskListener listener, final boolean quiet) {
        var logHandler = new LogHandler(listener, LOGGER_NAME);

        return quiet ? new QuietLogHandler(logHandler) : new VerboseLogHandler(logHandler);
    }

    CoverageLogHandler(final LogHandler logHandler) {
        this.logHandler = logHandler;
    }

    LogHandler getLogHandler() {
        return logHandler;
    }

    /**
     * Logs all messages of the specified log that have not been logged yet.
     *
     * @param log
     *         the log with the collected messages
     */
    abstract void log(FilteredLog log);

    /**
     * Logs the specified informational message.
     *
     * @param format
     *         a format string
     * @param args
     *         arguments referenced by the format specifiers in the format string
     */
    abstract void log(String format, Object... args);

    /**
     * Prints all info and error messages.
     */
    private static class VerboseLogHandler extends CoverageLogHandler {
        VerboseLogHandler(final LogHandler logHandler) {
            super(logHandler);
        }

        @Override
        void log(final FilteredLog log) {
            getLogHandler().log(log);
        }

        @Override
        void log(final String format, final Object... args) {
            getLogHandler().log(format, args);
        }
    }

    /**
     * Prints all error messages. Info messages are skipped, as long as they do not report a failure of the step or
     * the results of the quality gate evaluation.
     */
    private static class QuietLogHandler extends CoverageLogHandler {
        /** Messages of the quality gate evaluation, see {@link io.jenkins.plugins.util.QualityGateEvaluator}. */
        private static final List<String> IMPORTANT_MESSAGES = List.of(
                CoverageRecorder.FAIL_ON_ERROR_MESSAGE,
                CoverageRecorder.IGNORE_ERRORS_MESSAGE,
                "-> Some quality gates have been missed",
                "-> All quality gates have been passed",
                "-> Details for each quality gate:",
                "   - [");

        private int infoPosition;

        QuietLogHandler(final LogHandler logHandler) {
            super(logHandler);
        }

        @Override
        void log(final FilteredLog log) {
            getLogHandler().logErrorMessages(log.getErrorMessages());

            var infoMessages = log.getInfoMessages();
            infoMessages.subList(infoPosition, infoMessages.size()).stream()
                    .filter(QuietLogHandler::isImportant)
                    .forEach(message -> getLogHandler().log("%s", message));
            infoPosition = infoMessages.size();
        }

        @Override
        void log(final String format, final Object... args) {
            // informational messages are suppressed in quiet mode
        }

        private static boolean isImportant(final String message) {
            return IMPORTANT_MESSAGES.stream().anyMatch(message::startsWith);
        }
    }
}
