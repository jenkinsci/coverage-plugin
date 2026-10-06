/* global $, CoverageColorScale */

// Behavior for the coverage dashboard (currently only the Overview tab, see OverviewModel/index.jelly): the
// configurable green/red thresholds in the app-bar, the gauges themselves, and the card-width adjustments. Kept
// under RunTab (rather than OverviewModel's own resources) so a future dashboard tab can reuse it. The color
// scale itself (palette, gradient blending, per-report threshold storage) lives in the shared
// io.jenkins.plugins.coverage.metrics.color-scale adjunct so other views (e.g. the build-summary widget) can
// reuse the exact same thresholds and coloring -- see CoverageColorScale.
(function ($) {
    const DASHBOARD_SELECTOR = '.coverage-dashboard';
    const RING_SELECTOR = '.coverage-ring';
    const GREEN_INPUT_SELECTOR = '#overview-green-threshold';
    const RED_INPUT_SELECTOR = '#overview-red-threshold';
    const METRICS_CONTAINER_SELECTOR = '.coverage-dashboard__metrics';
    const METRIC_CARD_SELECTOR = '.coverage-dashboard__metric-card';
    const RING_WIDGET_SELECTOR = '.coverage-ring-widget';

    const TRACK_COLOR = '#e5e7eb';

    function currentReportId() {
        const dashboard = document.querySelector(DASHBOARD_SELECTOR);
        return dashboard ? dashboard.getAttribute('data-report-id') : null;
    }

    function readThresholds() {
        const green = parseFloat(document.querySelector(GREEN_INPUT_SELECTOR).value);
        const red = parseFloat(document.querySelector(RED_INPUT_SELECTOR).value);
        return {
            green: isNaN(green) ? CoverageColorScale.DEFAULT_GREEN : green,
            red: isNaN(red) ? CoverageColorScale.DEFAULT_RED : red
        };
    }

    function persistThresholds(thresholds) {
        CoverageColorScale.persistThresholds(currentReportId(), thresholds);
    }

    function applyThresholds(thresholds) {
        document.querySelector(GREEN_INPUT_SELECTOR).value = thresholds.green;
        document.querySelector(RED_INPUT_SELECTOR).value = thresholds.red;
    }

    // A conic-gradient whose track stop sits exactly at 360deg (i.e., a value that rounds up to 100%) has a
    // zero-width segment right where the gradient wraps back to 0deg. Browsers can render a visible hairline
    // seam at that exact boundary, so a value this close to full is instead painted as a solid circle -- there
    // is no gap to show anyway.
    const FULL_CIRCLE_THRESHOLD = 359.9;

    // Renders every gauge (one per metric/baseline pair that has a value, see index.jelly) as a ring: a
    // conic-gradient arc sized by the percentage, colored according to the configured thresholds. The label text
    // stays a plain, fixed color -- it is not tied to the gauge's status color.
    function renderRings(thresholds) {
        document.querySelectorAll(RING_SELECTOR).forEach(function (ring) {
            const value = parseFloat(ring.getAttribute('data-value'));
            const arc = ring.querySelector('.coverage-ring__arc');
            if (!arc || isNaN(value)) {
                return;
            }
            const angle = Math.max(0, Math.min(360, value * 3.6));
            const color = CoverageColorScale.colorFor(value, thresholds);
            arc.style.background = angle >= FULL_CIRCLE_THRESHOLD
                ? color
                : 'conic-gradient(from 0deg, ' + color + ' 0deg ' + angle + 'deg, ' + TRACK_COLOR + ' ' + angle + 'deg 360deg)';
        });
    }

    // A card whose only content is a single gauge (i.e., only one baseline has a value for that metric) gets a
    // width driven purely by its own title text, so "Instruction Coverage" ends up visibly wider than "Line
    // Coverage". That looks inconsistent when several such single-gauge cards sit side by side, so they are all
    // resized to the widest one among them. Cards with more than one gauge (several baselines) are left alone --
    // they should keep sizing to their own, typically larger, content.
    function equalizeSingleRingCardWidths(container) {
        const cards = Array.from(container.querySelectorAll(':scope > ' + METRIC_CARD_SELECTOR));
        const singleRingCards = cards.filter(function (card) {
            return card.querySelectorAll(RING_WIDGET_SELECTOR).length === 1;
        });

        // Reset to the natural (fit-content) width first, so re-runs start from a clean slate instead of
        // compounding a previous adjustment.
        singleRingCards.forEach(function (card) {
            card.style.width = '';
        });

        if (singleRingCards.length < 2) {
            return;
        }

        const maxWidth = Math.max.apply(Math, singleRingCards.map(function (card) {
            return card.getBoundingClientRect().width;
        }));
        singleRingCards.forEach(function (card) {
            card.style.width = maxWidth + 'px';
        });
    }

    function equalizeMetricCardWidths() {
        document.querySelectorAll(METRICS_CONTAINER_SELECTOR).forEach(equalizeSingleRingCardWidths);
    }

    function render() {
        renderRings(readThresholds());
        equalizeMetricCardWidths();
    }

    function initializeThresholdInputs() {
        const greenInput = document.querySelector(GREEN_INPUT_SELECTOR);
        const redInput = document.querySelector(RED_INPUT_SELECTOR);
        if (!greenInput || !redInput) {
            return;
        }

        const persisted = CoverageColorScale.readStoredThresholds(currentReportId());
        applyThresholds(persisted || {green: CoverageColorScale.DEFAULT_GREEN, red: CoverageColorScale.DEFAULT_RED});

        function onThresholdChanged() {
            const thresholds = readThresholds();
            persistThresholds(thresholds);
            render();
        }

        greenInput.addEventListener('change', onThresholdChanged);
        redInput.addEventListener('change', onThresholdChanged);
    }

    $(document).ready(function () {
        initializeThresholdInputs();
        render();
    });
})(jQuery3);
