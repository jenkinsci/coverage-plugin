// Shared color-scale logic for coverage gauges and badges rendered by different views -- currently the Overview
// tab's ring gauges (see RunTab/dashboard-metrics.js) and the build-summary widget's metric badges (see
// RunTab/widget.jelly / widget-badges.js). Kept at this package level (rather than under RunTab or OverviewModel)
// so any view for any report can reuse the same continuous gradient and the same per-report threshold
// persistence, without duplicating either.
(function (window) {
    const DEFAULT_GREEN = 90;
    const DEFAULT_RED = 70;

    // The exact coverage color palette used throughout the rest of the plugin (see CoverageColorPalette.java,
    // which follows the Jenkins Design Library), from worst to best. Each entry's "stop" is its percentage on the
    // plugin's own fixed 0-95 scale, normalized to [0, 1] -- used below only as the *shape* of the gradient (where
    // the color changes fastest, e.g. much finer-grained near the top), not as fixed percentages, so the same
    // smooth red-to-green transition can be stretched to fit between the user's own configurable thresholds.
    const PALETTE = [
        {stop: 0 / 95, rgb: [230, 0, 31]}, // INSUFFICIENT
        {stop: 50 / 95, rgb: [255, 77, 101]}, // VERY_BAD
        {stop: 60 / 95, rgb: [254, 130, 10]}, // BAD
        {stop: 70 / 95, rgb: [254, 182, 112]}, // INADEQUATE
        {stop: 80 / 95, rgb: [255, 204, 0]}, // AVERAGE
        {stop: 85 / 95, rgb: [255, 224, 102]}, // GOOD
        {stop: 90 / 95, rgb: [75, 223, 124]}, // VERY_GOOD
        {stop: 1, rgb: [30, 166, 75]} // EXCELLENT
    ];

    function lerp(a, b, t) {
        return a + (b - a) * t;
    }

    function rgbToCss(rgb, alpha) {
        const r = Math.round(rgb[0]);
        const g = Math.round(rgb[1]);
        const b = Math.round(rgb[2]);
        return (typeof alpha === 'number')
            ? 'rgba(' + r + ', ' + g + ', ' + b + ', ' + alpha + ')'
            : 'rgb(' + r + ', ' + g + ', ' + b + ')';
    }

    // Maps a coverage value to a color by stretching the fixed palette above (see PALETTE) so that its first stop
    // lands exactly on the red threshold and its last stop lands exactly on the green threshold -- both
    // configurable by the user. The result is a continuous gradient through the same hues used elsewhere in the
    // plugin, instead of 3 flat colors with a hard cutoff, so nearby values (e.g. 89% vs. 94%) are visually
    // distinguishable; values at or beyond a threshold are still shown as pure red/green. An optional alpha
    // (0-1) renders the color translucent, e.g. for a badge background that still needs to show the
    // fill-percentage split against a transparent half.
    function colorFor(value, thresholds, alpha) {
        const first = PALETTE[0].rgb;
        const last = PALETTE[PALETTE.length - 1].rgb;

        if (thresholds.green <= thresholds.red) {
            // Degenerate configuration (equal or inverted thresholds) -- fall back to a hard cutoff so the caller
            // still gets something sensible instead of dividing by zero.
            return rgbToCss(value >= thresholds.green ? last : first, alpha);
        }

        const t = (value - thresholds.red) / (thresholds.green - thresholds.red);
        if (t <= 0) {
            return rgbToCss(first, alpha);
        }
        if (t >= 1) {
            return rgbToCss(last, alpha);
        }

        for (let i = 0; i < PALETTE.length - 1; i++) {
            const lower = PALETTE[i];
            const upper = PALETTE[i + 1];
            if (t >= lower.stop && t <= upper.stop) {
                const localT = (t - lower.stop) / (upper.stop - lower.stop);
                return rgbToCss([
                    lerp(lower.rgb[0], upper.rgb[0], localT),
                    lerp(lower.rgb[1], upper.rgb[1], localT),
                    lerp(lower.rgb[2], upper.rgb[2], localT)
                ], alpha);
            }
        }
        return rgbToCss(last, alpha); // unreachable
    }

    // The green/red thresholds are configurable by the user (see the Overview tab's app-bar inputs) and are
    // persisted per report in local storage -- shared by every view that renders gauges/badges for the same
    // report, so a threshold change made on the Overview tab is picked up everywhere else too.
    function thresholdStorageKey(reportId) {
        return 'jenkins-coverage-overview-thresholds-' + (reportId || 'default');
    }

    function readStoredThresholds(reportId) {
        const stored = localStorage.getItem(thresholdStorageKey(reportId));
        if (!stored) {
            return null;
        }
        try {
            const parsed = JSON.parse(stored);
            if (typeof parsed.green === 'number' && typeof parsed.red === 'number') {
                return parsed;
            }
        }
        catch (e) {
            // ignore malformed entries and fall back to the defaults
        }
        return null;
    }

    function persistThresholds(reportId, thresholds) {
        localStorage.setItem(thresholdStorageKey(reportId), JSON.stringify(thresholds));
    }

    window.CoverageColorScale = {
        DEFAULT_GREEN: DEFAULT_GREEN,
        DEFAULT_RED: DEFAULT_RED,
        colorFor: colorFor,
        thresholdStorageKey: thresholdStorageKey,
        readStoredThresholds: readStoredThresholds,
        persistThresholds: persistThresholds
    };
})(window);
