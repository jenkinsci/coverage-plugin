/* global $ */

(function ($) {
    const DASHBOARD_SELECTOR = '.coverage-dashboard';
    const RING_SELECTOR = '.coverage-ring';
    const TREND_SELECTOR = '[data-trend]';
    const GREEN_INPUT_SELECTOR = '#overview-green-threshold';
    const RED_INPUT_SELECTOR = '#overview-red-threshold';
    const METRICS_CONTAINER_SELECTOR = '.coverage-dashboard__metrics';
    const METRIC_CARD_SELECTOR = '.coverage-dashboard__metric-card';
    const RING_WIDGET_SELECTOR = '.coverage-ring-widget';
    const STRUCTURE_SELECTOR = '.coverage-dashboard__structure';

    const GREEN_COLOR = '#12b76a';
    const YELLOW_COLOR = '#f79009';
    const RED_COLOR = '#f04438';
    const TRACK_COLOR = '#e5e7eb';

    const DEFAULT_GREEN = 90;
    const DEFAULT_RED = 70;

    // The green/red thresholds are configurable by the user (see the app-bar inputs in index.jelly) and are
    // persisted per report, the same way the Treemap tab persists its thresholds (see treemap-model.js).
    function thresholdStorageKey() {
        const dashboard = document.querySelector(DASHBOARD_SELECTOR);
        const reportId = dashboard ? dashboard.getAttribute('data-report-id') : null;
        return 'jenkins-coverage-overview-thresholds-' + (reportId || 'default');
    }

    function readThresholds() {
        const green = parseFloat(document.querySelector(GREEN_INPUT_SELECTOR).value);
        const red = parseFloat(document.querySelector(RED_INPUT_SELECTOR).value);
        return {
            green: isNaN(green) ? DEFAULT_GREEN : green,
            red: isNaN(red) ? DEFAULT_RED : red
        };
    }

    function persistThresholds(thresholds) {
        localStorage.setItem(thresholdStorageKey(), JSON.stringify(thresholds));
    }

    function readPersistedThresholds() {
        const stored = localStorage.getItem(thresholdStorageKey());
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

    function applyThresholds(thresholds) {
        document.querySelector(GREEN_INPUT_SELECTOR).value = thresholds.green;
        document.querySelector(RED_INPUT_SELECTOR).value = thresholds.red;
    }

    function colorFor(value, thresholds) {
        if (value >= thresholds.green) {
            return GREEN_COLOR;
        }
        if (value <= thresholds.red) {
            return RED_COLOR;
        }
        return YELLOW_COLOR;
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
            const color = colorFor(value, thresholds);
            arc.style.background = angle >= FULL_CIRCLE_THRESHOLD
                ? color
                : 'conic-gradient(from 0deg, ' + color + ' 0deg ' + angle + 'deg, ' + TRACK_COLOR + ' ' + angle + 'deg 360deg)';
        });
    }

    // Colors every delta (both the per-ring deltas and the "Further metrics" table deltas) green/red depending on
    // whether the trend is positive or negative; a value close to zero keeps the default text color.
    function renderTrends() {
        document.querySelectorAll(TREND_SELECTOR).forEach(function (element) {
            const trend = parseFloat(element.getAttribute('data-trend'));
            if (isNaN(trend) || trend === 0) {
                element.style.color = '';
            }
            else if (trend > 0) {
                element.style.color = 'var(--green)';
            }
            else {
                element.style.color = 'var(--red)';
            }
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

        // Reset to the natural (fit-content) width first, so re-runs (e.g. after expanding the collapsed
        // "Structure metrics" section) start from a clean slate instead of compounding a previous adjustment.
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

    // The "Structure metrics" section starts collapsed (see the <details> element in index.jelly), so its cards
    // have no layout yet and cannot be measured; re-run the equalization once it is actually opened.
    function initializeStructureSection() {
        document.querySelectorAll(STRUCTURE_SELECTOR).forEach(function (details) {
            details.addEventListener('toggle', function () {
                if (details.open) {
                    equalizeSingleRingCardWidths(details.querySelector(METRICS_CONTAINER_SELECTOR));
                }
            });
        });
    }

    function render() {
        renderRings(readThresholds());
        renderTrends();
        equalizeMetricCardWidths();
    }

    function initializeThresholdInputs() {
        const greenInput = document.querySelector(GREEN_INPUT_SELECTOR);
        const redInput = document.querySelector(RED_INPUT_SELECTOR);
        if (!greenInput || !redInput) {
            return;
        }

        const persisted = readPersistedThresholds();
        applyThresholds(persisted || {green: DEFAULT_GREEN, red: DEFAULT_RED});

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
        initializeStructureSection();
        render();
    });
})(jQuery3);
