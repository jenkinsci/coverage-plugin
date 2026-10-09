package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.util.FilteredLog;

import java.util.List;

import hudson.model.ModelObject;
import hudson.model.Run;

import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;

/**
 * Server side model that provides the data for the "Info" tab of the coverage details view. Shows the informational
 * and error log messages that have been recorded while parsing and processing the coverage results. Previously,
 * these messages were shown in a separate page (without the coverage sidebar) that was reachable only via a button
 * in the app bar of the "Overview" tab; now they get a sidebar entry of their own, like every other tab, and open
 * as a standalone tab (with the sidebar) as well. The layout of the associated view is defined in the corresponding
 * jelly view 'index.jelly'.
 *
 * @author Ullrich Hafner
 */
public class LogModel implements ModelObject {
    private final String id;
    private final Run<?, ?> owner;
    private final ElementFormatter formatter;
    private final FilteredLog log;

    LogModel(final String id, final Run<?, ?> owner, final ElementFormatter formatter, final FilteredLog log) {
        this.id = id;
        this.owner = owner;
        this.formatter = formatter;
        this.log = log;
    }

    @Override
    public String getDisplayName() {
        return Messages.CoverageInfoModel_displayName();
    }

    public String getId() {
        return id;
    }

    public Run<?, ?> getObject() {
        return owner;
    }

    public RunTab getTab() {
        return new RunTab(owner);
    }

    public ElementFormatter getFormatter() {
        return formatter;
    }

    /**
     * Returns the informational messages that have been recorded while parsing and processing the coverage results.
     *
     * @return the information messages
     */
    public List<String> getInfoMessages() {
        return log.getInfoMessages();
    }

    /**
     * Returns the error messages that have been recorded while parsing and processing the coverage results.
     *
     * @return the error messages
     */
    public List<String> getErrorMessages() {
        return log.getErrorMessages();
    }

    /**
     * Checks whether any error messages have been recorded.
     *
     * @return {@code true} if there is at least one error message, {@code false} otherwise
     */
    public boolean hasErrors() {
        return !getErrorMessages().isEmpty();
    }
}
