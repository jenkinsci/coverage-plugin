/* global $, proxy, echartsJenkinsApi, bootstrap5, culori */

(function ($) {
    const CHART_SELECTOR = '#coverage-trend';
    const FOOTER_SELECTOR = '.page-footer';
    const MIN_CHART_HEIGHT = 300;
    const BOTTOM_MARGIN = 40;
    let resizeTimeout;

    const AREA_CHART_SELECTOR = '#area-chart';
    const ZERO_AXIS_SELECTOR = '#zero-axis';
    const BUILDS_SELECTOR = '#builds';
    const GROUP_SELECTOR = '#metric-group';
    const SOFTWARE_GROUP = 'software';

    const DEFAULT_ZERO_BASED_Y_AXIS = false;
    const DEFAULT_USE_LINES = false;
    const DEFAULT_NUMBER_OF_BUILDS = 50;

    // The same chart (and this same script) is reused for several report types (e.g. "coverage", "pit").
    // The report id (read from the chart's data-report-id attribute, set in index.jelly from ${it.id}) is
    // appended to every storage key so that each report type keeps its own settings independently.
    function getReportId() {
        const element = document.querySelector(CHART_SELECTOR);
        const reportId = element ? element.getAttribute('data-report-id') : null;
        return reportId || 'default';
    }

    function storageKey(baseKey) {
        return baseKey + '-' + getReportId();
    }

    function getFooterHeight() {
        const footer = document.querySelector(FOOTER_SELECTOR);
        if (!footer) {
            return 0;
        }
        return footer.getBoundingClientRect().height;
    }

    function fitChartToViewport(selector) {
        const element = document.querySelector(selector);
        if (!element) {
            return;
        }
        const top = element.getBoundingClientRect().top;
        const availableHeight = window.innerHeight - top - getFooterHeight() - BOTTOM_MARGIN;
        element.style.height = Math.max(availableHeight, MIN_CHART_HEIGHT) + 'px';
    }

    function resizeChartOf(selector) {
        fitChartToViewport(selector);
        if (document.querySelector(selector)) {
            $(selector)[0].echart.resize();
        }
    }

    function readStoredBoolean(baseKey, defaultValue) {
        const stored = localStorage.getItem(storageKey(baseKey));
        if (stored === null) {
            return defaultValue;
        }
        return stored === 'true';
    }

    function readStoredNumber(baseKey, defaultValue) {
        const stored = localStorage.getItem(storageKey(baseKey));
        if (stored === null) {
            return defaultValue;
        }
        const parsed = parseInt(stored, 10);
        return isNaN(parsed) ? defaultValue : parsed;
    }

    // Coverage (percentages) and software metrics (absolute values) are shown in separate charts that have
    // different series, so each group remembers its own legend selection.
    function getSelectedGroup() {
        return $(GROUP_SELECTOR).val() === SOFTWARE_GROUP ? SOFTWARE_GROUP : 'coverage';
    }

    function legendStorageKey() {
        return storageKey('jenkins-coverage-trend-legend-selected-' + getSelectedGroup());
    }

    function readStoredLegendSelection() {
        const stored = localStorage.getItem(legendStorageKey());
        if (!stored) {
            return null;
        }
        try {
            return JSON.parse(stored);
        }
        catch (e) {
            return null;
        }
    }

    function persistLegendSelection(selected) {
        localStorage.setItem(legendStorageKey(), JSON.stringify(selected));
    }

    function getChartInstance() {
        const element = document.querySelector(CHART_SELECTOR);
        return element ? element.echart : undefined;
    }

    function applyStoredLegendSelection() {
        const chart = getChartInstance();
        if (!chart) {
            return;
        }

        const storedSelection = readStoredLegendSelection();
        if (storedSelection) {
            chart.setOption({legend: {selected: storedSelection}});
        }

        chart.off('legendselectchanged');
        chart.on('legendselectchanged', function (params) {
            persistLegendSelection(params.selected);
        });
    }

    function initializeControls() {
        const useAreaChart = readStoredBoolean('jenkins-coverage-trend-area-chart', !DEFAULT_USE_LINES);
        const zeroBasedYAxis = readStoredBoolean('jenkins-coverage-trend-zero-axis', DEFAULT_ZERO_BASED_Y_AXIS);
        const numberOfBuilds = readStoredNumber('jenkins-coverage-trend-builds', DEFAULT_NUMBER_OF_BUILDS);

        $(AREA_CHART_SELECTOR).prop('checked', useAreaChart);
        $(ZERO_AXIS_SELECTOR).prop('checked', zeroBasedYAxis);
        $(BUILDS_SELECTOR).val(numberOfBuilds);

        // only restore the stored group if the report still offers it (otherwise keep the pre-selected one)
        const storedGroup = localStorage.getItem(storageKey('jenkins-coverage-trend-group'));
        if (storedGroup && $(GROUP_SELECTOR + ' option[value="' + storedGroup + '"]').length > 0) {
            $(GROUP_SELECTOR).val(storedGroup);
        }
    }

    function readConfiguration() {
        const useAreaChart = $(AREA_CHART_SELECTOR).is(':checked');
        const zeroBasedYAxis = $(ZERO_AXIS_SELECTOR).is(':checked');
        const parsedBuilds = parseInt($(BUILDS_SELECTOR).val(), 10);
        const numberOfBuilds = isNaN(parsedBuilds) ? DEFAULT_NUMBER_OF_BUILDS : parsedBuilds;

        return {
            zeroBasedYAxis: zeroBasedYAxis,
            useLines: !useAreaChart,
            numberOfBuilds: numberOfBuilds,
            softwareMetrics: getSelectedGroup() === SOFTWARE_GROUP
        };
    }

    function persistConfiguration(configuration) {
        localStorage.setItem(storageKey('jenkins-coverage-trend-area-chart'), String(!configuration.useLines));
        localStorage.setItem(storageKey('jenkins-coverage-trend-zero-axis'), String(configuration.zeroBasedYAxis));
        localStorage.setItem(storageKey('jenkins-coverage-trend-builds'), String(configuration.numberOfBuilds));
        localStorage.setItem(storageKey('jenkins-coverage-trend-group'), getSelectedGroup());
    }

    function renderCoverageTrendChart() {
        fitChartToViewport(CHART_SELECTOR);
        const configuration = readConfiguration();
        persistConfiguration(configuration);
        proxy.getTrendChart(JSON.stringify(configuration), function (t) {
            const model = t.responseJSON;
            if (configuration.softwareMetrics) {
                // The server serializes the unset maximum as 0: drop it so that ECharts adapts the maximum
                // to the series that are currently visible in the legend.
                model.rangeMax = null;
            }
            echartsJenkinsApi.renderConfigurableZoomableTrendChart('coverage-trend', model);

            applyStoredLegendSelection();
            resizeChartOf(CHART_SELECTOR);
        });
    }

    $(document).ready(function () {
        initializeControls();
        renderCoverageTrendChart();

        $(AREA_CHART_SELECTOR + ', ' + ZERO_AXIS_SELECTOR + ', ' + GROUP_SELECTOR).on('change', function () {
            renderCoverageTrendChart();
        });
        $(BUILDS_SELECTOR).on('change input', function () {
            renderCoverageTrendChart();
        });

        $(window).on('resize', function () {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(function () {
                resizeChartOf(CHART_SELECTOR);
            }, 150);
        });
    });
})(jQuery3);
