/* global CoverageColorScale */

// Recolors the build-summary widget's single-result metric badges (see RunTab/widget.jelly) using the same
// green/red thresholds the user has configured -- and had persisted to local storage -- on that report's Overview
// tab. The badges are rendered server-side using the plugin's default thresholds, because local storage is only
// available in the browser; this brings them in line with what the user actually sees once they open the
// Overview tab for the same report.
(function () {
    const BADGE_SELECTOR = '.coverage-widget__metric [data-report-id]';
    const BADGE_COLOR_ALPHA = 0.8;

    function recolor() {
        document.querySelectorAll(BADGE_SELECTOR).forEach(function (badge) {
            const thresholds = CoverageColorScale.readStoredThresholds(badge.getAttribute('data-report-id'));
            if (!thresholds) {
                return; // no user override for this report -- keep the server-rendered default coloring
            }

            const value = parseFloat(badge.getAttribute('data-value'));
            const fill = badge.getAttribute('data-fill-percentage');
            if (isNaN(value) || !fill) {
                return;
            }

            const color = CoverageColorScale.colorFor(value, thresholds, BADGE_COLOR_ALPHA);
            badge.style.backgroundImage =
                'linear-gradient(90deg, ' + color + ' ' + fill + ', transparent ' + fill + ')';
        });
    }

    document.addEventListener('DOMContentLoaded', recolor);
})();
