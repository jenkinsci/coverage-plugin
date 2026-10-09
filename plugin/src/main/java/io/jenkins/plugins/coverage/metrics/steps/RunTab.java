package io.jenkins.plugins.coverage.metrics.steps;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

import org.kohsuke.stapler.HttpRedirect;
import org.kohsuke.stapler.StaplerResponse2;
import hudson.model.Actionable;
import jenkins.management.Badge;
import jenkins.model.Tab;
import jenkins.model.experimentalflags.BooleanUserExperimentalFlag;

import io.jenkins.plugins.util.QualityGateResult;

/**
 * Defines the coverage tab for a run.
 */
public class RunTab extends Tab {
    /**
     * Creates a new {@link RunTab}.
     *
     * @param object
     *         the run to create the tab for
     */
    public RunTab(final Actionable object) {
        super(object);
    }

    @Override
    public String getIconFileName() {
        if (getActions().isEmpty()) {
            return null;
        }

        return "symbol-footsteps-outline plugin-ionicons-api";
    }

    @Override
    public String getDisplayName() {
        return Messages.RunTab_displayName();
    }

    @Override
    public String getUrlName() {
        return "coverage";
    }

    @Override
    public Badge getBadge() {
        var failed = getActions()
                .stream()
                .map(CoverageBuildAction::getQualityGateResult)
                .filter(Predicate.not(QualityGateResult::isSuccessful))
                .count();

        if (failed == 0) {
            return null;
        }

        return new Badge(String.valueOf(failed), Messages.RunTab_failedQualityGates(failed),
                Badge.Severity.WARNING);
    }

    public List<CoverageBuildAction> getActions() {
        return getObject()
                .getActions(CoverageBuildAction.class);
    }

    /**
     * Generates the widget for the Overview tab.
     *
     * @return the widget.
     */
    public Widget getWidget() {
        return new Widget(getActions());
    }

    /**
     * Renders a dynamic warning action of the Warnings tab.
     *
     * @param name
     *         the name of the warning action to render.
     *
     * @return the warning action.
     */
    public CoverageBuildAction getDynamic(final String name) {
        for (CoverageBuildAction action : getActions()) {
            String urlName = action.getUrlName();
            if (name.equals(urlName)) {
                return action;
            }
        }

        throw new NoSuchElementException("No coverage result found for " + getObject());
    }

    /**
     * Redirects to the overview of the first coverage action if the new UI is enabled. Without this, opening the
     * "Coverage" tab landed on this tab's own root with no id chosen yet, leaving nothing selected in the sidebar.
     *
     * @param response
     *         the response
     *
     * @return the redirect to the overview page of the first coverage action
     * @throws IOException
     *         if an input or output exception occurs.
     */
    @CheckForNull
    public HttpRedirect doIndex(final StaplerResponse2 response) throws IOException {
        Boolean newUiEnabled = BooleanUserExperimentalFlag.
                getFlagValueForCurrentUser("jenkins.model.experimentalflags.NewBuildPageUserExperimentalFlag");

        if (Boolean.TRUE.equals(newUiEnabled)) {
            return new HttpRedirect(getActions().get(0).getUrlName() + CoverageViewModel.OVERVIEW_URL);
        }

        response.sendError(404, "This page requires the new build page UI to be enabled");
        return null;
    }
}
