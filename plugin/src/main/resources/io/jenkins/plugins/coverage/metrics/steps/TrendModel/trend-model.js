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

    function readStoredLegendSelection() {
        const stored = localStorage.getItem(storageKey('jenkins-coverage-trend-legend-selected'));
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
        localStorage.setItem(storageKey('jenkins-coverage-trend-legend-selected'), JSON.stringify(selected));
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
    }

    function readConfiguration() {
        const useAreaChart = $(AREA_CHART_SELECTOR).is(':checked');
        const zeroBasedYAxis = $(ZERO_AXIS_SELECTOR).is(':checked');
        const parsedBuilds = parseInt($(BUILDS_SELECTOR).val(), 10);
        const numberOfBuilds = isNaN(parsedBuilds) ? DEFAULT_NUMBER_OF_BUILDS : parsedBuilds;

        return {
            zeroBasedYAxis: zeroBasedYAxis,
            useLines: !useAreaChart,
            numberOfBuilds: numberOfBuilds
        };
    }

    function persistConfiguration(configuration) {
        localStorage.setItem(storageKey('jenkins-coverage-trend-area-chart'), String(!configuration.useLines));
        localStorage.setItem(storageKey('jenkins-coverage-trend-zero-axis'), String(configuration.zeroBasedYAxis));
        localStorage.setItem(storageKey('jenkins-coverage-trend-builds'), String(configuration.numberOfBuilds));
    }

    function renderCoverageTrendChart() {
        fitChartToViewport(CHART_SELECTOR);
        const configuration = readConfiguration();
        persistConfiguration(configuration);
        proxy.getTrendChart(JSON.stringify(configuration), function (t) {
            echartsJenkinsApi.renderConfigurableZoomableTrendChart('coverage-trend', t.responseJSON);

            applyStoredLegendSelection();
            resizeChartOf(CHART_SELECTOR);
        });
    }

    $(document).ready(function () {
        initializeControls();
        renderCoverageTrendChart();

        $(AREA_CHART_SELECTOR + ', ' + ZERO_AXIS_SELECTOR).on('change', function () {
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
