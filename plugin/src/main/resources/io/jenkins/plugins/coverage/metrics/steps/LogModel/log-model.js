/* global $ */

(function ($) {
    const ERRORS_SELECTOR = '#errors';
    const INFO_SELECTOR = '#info';
    const FOOTER_SELECTOR = '.page-footer';
    const MIN_LOG_HEIGHT = 150;
    const BOTTOM_MARGIN = 40;
    let resizeTimeout;

    function getFooterHeight() {
        const footer = document.querySelector(FOOTER_SELECTOR);
        if (!footer) {
            return 0;
        }
        return footer.getBoundingClientRect().height;
    }

    // Sizes the "Error Messages" and "Information Messages" cards so that together they fill the browser
    // viewport (while keeping the footer visible), the same way the Trend and Treemap tabs size their charts
    // (see trend-model.js / treemap-model.js). If only the "Information Messages" card is shown (no errors were
    // recorded), it gets the full available height; if both cards are shown, each one gets half of it.
    function fitLogToViewport() {
        const errors = document.querySelector(ERRORS_SELECTOR);
        const info = document.querySelector(INFO_SELECTOR);
        if (!info) {
            return;
        }

        const bottomBoundary = window.innerHeight - getFooterHeight() - BOTTOM_MARGIN;

        if (!errors) {
            const top = info.getBoundingClientRect().top;
            info.style.height = Math.max(bottomBoundary - top, MIN_LOG_HEIGHT) + 'px';
            return;
        }

        // Reset both cards first so the chrome (title bar) and the gap between them can be measured
        // independently of any height that was computed on a previous call.
        errors.style.height = 'auto';
        info.style.height = 'auto';

        const errorsTop = errors.getBoundingClientRect().top;
        const chromeAndGap = info.getBoundingClientRect().top - errors.getBoundingClientRect().bottom;
        const half = Math.max((bottomBoundary - errorsTop - chromeAndGap) / 2, MIN_LOG_HEIGHT);

        errors.style.height = half + 'px';
        info.style.height = half + 'px';
    }

    $(document).ready(function () {
        fitLogToViewport();

        $(window).on('resize', function () {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(fitLogToViewport, 150);
        });
    });
})(jQuery3);
