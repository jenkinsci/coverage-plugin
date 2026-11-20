package io.jenkins.plugins.coverage.metrics.steps;

import edu.hm.hafner.util.VisibleForTesting;
import hudson.model.Actionable;
import hudson.model.Run;
import io.jenkins.plugins.coverage.metrics.steps.CoverageViewModel.UsePropertyFacade;
import io.jenkins.plugins.util.QualityGateResult;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import jenkins.management.Badge;
import jenkins.model.Tab;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.verb.GET;

/**
 * Defines the coverage tab for a run.
 */
public class RunTab extends Tab {
    static final String URL = "coverage";

    private final UsePropertyFacade usePropertyFacade;

    /**
     * Creates a new {@link RunTab}.
     *
     * @param object
     *         the run to create the tab for
     */
    public RunTab(final Actionable object) {
        this(object, CoverageViewModel::isRunTabActive);
    }

    /**
     * Creates a new {@link RunTab} with a custom facade for the experimental flag of the new build page.
     *
     * @param object
     *         the run to create the tab for
     * @param usePropertyFacade
     *         the facade that determines whether the run tab is enabled
     */
    @VisibleForTesting
    RunTab(final Actionable object, final UsePropertyFacade usePropertyFacade) {
        super(object);

        this.usePropertyFacade = usePropertyFacade;
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
        return URL;
    }

    @Override
    public Badge getBadge() {
        var failed = getActions().stream()
                .map(CoverageBuildAction::getQualityGateResult)
                .filter(Predicate.not(QualityGateResult::isSuccessful))
                .count();

        if (failed == 0) {
            return null;
        }

        return new Badge(String.valueOf(failed), Messages.RunTab_failedQualityGates(failed), Badge.Severity.WARNING);
    }

    public List<CoverageBuildAction> getActions() {
        return getObject().getActions(CoverageBuildAction.class);
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
     * "Coverage" tab landed on this tab's own root with no id chosen yet, leaving nothing selected in the sidebar. The
     * redirect target is derived from the URL of the run (and not from the request), and it is resolved via the
     * context path, so it is independent of whether the request URL ends with a slash.
     *
     * @return the redirect to the overview page of the first coverage action, or a 404 error if the new build page
     *         is disabled or if there are no coverage results
     */
    @GET
    public HttpResponse doIndex() {
        var actions = getActions();
        if (usePropertyFacade.isRunTabEnabled() && getObject() instanceof Run<?, ?> run && !actions.isEmpty()) {
            return HttpResponses.redirectViaContextPath(
                    run.getUrl() + actions.get(0).getUrlName() + "/" + CoverageViewModel.OVERVIEW_URL);
        }

        return HttpResponses.errorWithoutStack(
                HttpServletResponse.SC_NOT_FOUND, "This page requires the new build page UI to be enabled");
    }
}
