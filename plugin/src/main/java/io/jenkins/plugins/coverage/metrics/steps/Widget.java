package io.jenkins.plugins.coverage.metrics.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import io.jenkins.plugins.util.QualityGateResult;

/**
 * Model for the coverage widget.
 */
public class Widget {
    private final String message;
    private final List<CoverageBuildAction> results;

    /**
     * Creates a new widget.
     *
     * @param results
     *         the list of results to display in the widget
     */
    public Widget(final List<CoverageBuildAction> results) {
        this.results = new ArrayList<>(results);

        var failed = results.stream()
                .map(CoverageBuildAction::getQualityGateResult)
                .filter(Predicate.not(QualityGateResult::isSuccessful))
                .count();

        if (failed > 0) {
            message = Messages.Widget_failedQualityGates(failed);
        }
        else {
            var active = results.stream()
                    .map(CoverageBuildAction::getQualityGateResult)
                    .filter(Predicate.not(QualityGateResult::isInactive))
                    .count();

            if (active > 0) {
                message = Messages.Widget_passedQualityGates();
            }
            else {
                message = Messages.Widget_noQualityGates();
            }
        }
    }

    public String getSymbol() {
        return "symbol-footsteps-outline plugin-ionicons-api";
    }

    public String getMessage() {
        return message;
    }

    public List<CoverageBuildAction> getResults() {
        return results;
    }
}
