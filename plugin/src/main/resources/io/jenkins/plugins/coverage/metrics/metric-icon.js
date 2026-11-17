/* global document, window, HTMLSelectElement */

/**
 * Shows the icon of the selected metric next to a metric selection (see taglib coverage/metric-icons.jelly). The icon
 * is updated whenever the user changes the selection. Scripts that fill or change a selection programmatically must
 * call {@code CoverageMetricIcons.update(select)} afterward, since no change event is fired in this case.
 */
(function () {
    'use strict';

    const CONTAINER_SELECTOR = '.coverage-metric-icons[data-select]';
    const ICON_SELECTOR = '.coverage-metric-icon';
    const HIDDEN_CLASS = 'jenkins-hidden';

    function update(select) {
        if (!select || !select.id) {
            return;
        }
        document.querySelectorAll(CONTAINER_SELECTOR).forEach(function (container) {
            if (container.getAttribute('data-select') === select.id) {
                container.querySelectorAll(ICON_SELECTOR).forEach(function (icon) {
                    icon.classList.toggle(HIDDEN_CLASS, icon.getAttribute('data-metric') !== select.value);
                });
            }
        });
    }

    function updateAll() {
        document.querySelectorAll(CONTAINER_SELECTOR).forEach(function (container) {
            update(document.getElementById(container.getAttribute('data-select')));
        });
    }

    document.addEventListener('change', function (event) {
        if (event.target instanceof HTMLSelectElement) {
            update(event.target);
        }
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', updateAll);
    }
    else {
        updateAll();
    }

    window.CoverageMetricIcons = {update: update};
})();
