/* global $, proxy, echarts */

(function ($) {
    const CHART_ID = 'hierarchy-tree';
    const CHART_SELECTOR = '#' + CHART_ID;
    const SELECT_SELECTOR = '#hierarchy-metric-select';
    const GREEN_INPUT_SELECTOR = '#hierarchy-green-threshold';
    const RED_INPUT_SELECTOR = '#hierarchy-red-threshold';
    const FOOTER_SELECTOR = '.page-footer';
    const MIN_CHART_HEIGHT = 300;
    const BOTTOM_MARGIN = 40;
    let resizeTimeout;

    // The chart is reused for every metric the user picks in the selector. The report id (read from the chart's
    // data-report-id attribute, set in index.jelly from ${it.id}) is used to remember the last selected metric, and
    // the last configured green/red thresholds per metric, independently for each report type (e.g. "coverage",
    // "pit") and, since the thresholds are only meaningful in the value range of one particular metric, per metric.
    function getReportId() {
        const element = document.querySelector(CHART_SELECTOR);
        const reportId = element ? element.getAttribute('data-report-id') : null;
        return reportId || 'default';
    }

    function metricStorageKey() {
        return 'jenkins-coverage-hierarchy-metric-' + getReportId();
    }

    function thresholdStorageKey(metricTagName) {
        return 'jenkins-coverage-hierarchy-thresholds-' + getReportId() + '-' + metricTagName;
    }

    function getFooterHeight() {
        const footer = document.querySelector(FOOTER_SELECTOR);
        if (!footer) {
            return 0;
        }
        return footer.getBoundingClientRect().height;
    }

    function fitChartToViewport() {
        const element = document.querySelector(CHART_SELECTOR);
        if (!element) {
            return;
        }
        const top = element.getBoundingClientRect().top;
        const availableHeight = window.innerHeight - top - getFooterHeight() - BOTTOM_MARGIN;
        element.style.height = Math.max(availableHeight, MIN_CHART_HEIGHT) + 'px';
    }

    function resizeChart() {
        if (document.querySelector(CHART_SELECTOR)) {
            $(CHART_SELECTOR)[0].echart.resize();
        }
    }

    function getLevelOption() {
        return [
            {
                itemStyle: {
                    borderWidth: 0,
                    gapWidth: 5
                },
                upperLabel: {
                    show: false
                }
            },
            {
                itemStyle: {
                    gapWidth: 3
                }
            },
            {
                itemStyle: {
                    gapWidth: 1
                }
            },
            {
                itemStyle: {
                    gapWidth: 1
                }
            },
            {
                itemStyle: {
                    gapWidth: 1
                }
            },
            {
                itemStyle: {
                    gapWidth: 1
                }
            },
            {
                itemStyle: {
                    gapWidth: 1
                }
            },
            {
                itemStyle: {
                    gapWidth: 1
                }
            },
            {
                itemStyle: {
                    gapWidth: 1
                }
            },
            {
                itemStyle: {
                    gapWidth: 1
                }
            }
        ];
    }

    function createHierarchyTreeMap(coverageTree, coverageMetric) {
        const treeChartDiv = $(CHART_SELECTOR);
        const treeChart = treeChartDiv[0].echart || echarts.init(treeChartDiv[0]);
        treeChartDiv[0].echart = treeChart;

        const formatUtil = echarts.format;

        const option = {
            tooltip: {
                formatter: function (info) {
                    const treePathInfo = info.treePathInfo;
                    const treePath = [];
                    for (let i = 2; i < treePathInfo.length; i++) {
                        treePath.push(treePathInfo[i].name);
                    }
                    const values = info.value;
                    const total = values[0];
                    const tooltip = values[1];

                    const title = '<div class="jenkins-tooltip healthReportDetails jenkins-tooltip--table-wrapper">' + formatUtil.encodeHTML(treePath.join('.')) + '</div>';
                    if (total === 0) {
                        return [title, coverageMetric + ': n/a'].join('');
                    }
                    return [title, tooltip].join('');
                }
            },
            series: [
                {
                    name: coverageMetric,
                    type: 'treemap',
                    breadcrumb: {
                        itemStyle: {
                            color: '#A4A4A4'
                        },
                        emphasis: {
                            itemStyle: {
                                opacity: 0.6
                            }
                        }
                    },
                    width: '100%',
                    height: '100%',
                    top: 'top',
                    label: {
                        show: true,
                        formatter: '{b}'
                    },
                    upperLabel: {
                        show: true,
                        height: 30
                    },
                    itemStyle: {
                        shadowColor: '#000',
                        shadowBlur: 3
                    },
                    levels: getLevelOption(),
                    data: [coverageTree]
                }
            ]
        };
        treeChart.setOption(option, {notMerge: true});
        treeChart.resize();
    }

    function getSelectedOption() {
        const select = document.querySelector(SELECT_SELECTOR);
        return select ? select.options[select.selectedIndex] : null;
    }

    function readThresholds() {
        const green = parseFloat(document.querySelector(GREEN_INPUT_SELECTOR).value);
        const red = parseFloat(document.querySelector(RED_INPUT_SELECTOR).value);
        return {
            green: isNaN(green) ? 0 : green,
            red: isNaN(red) ? 0 : red
        };
    }

    function persistThresholds(metricTagName, thresholds) {
        localStorage.setItem(thresholdStorageKey(metricTagName), JSON.stringify(thresholds));
    }

    function readPersistedThresholds(metricTagName) {
        const stored = localStorage.getItem(thresholdStorageKey(metricTagName));
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

    function renderChart() {
        const option = getSelectedOption();
        if (!option) {
            return;
        }
        const metricTagName = option.value;
        const thresholds = readThresholds();

        fitChartToViewport();

        proxy.getThresholdCoverageTree(metricTagName, thresholds.green, thresholds.red, function (t) {
            createHierarchyTreeMap(t.responseObject(), option.textContent.trim());
            fitChartToViewport();
            resizeChart();
        });
    }

    function applyThresholds(metricTagName, thresholds) {
        document.querySelector(GREEN_INPUT_SELECTOR).value = thresholds.green;
        document.querySelector(RED_INPUT_SELECTOR).value = thresholds.red;
    }

    // Loads the thresholds for the currently selected metric: either the values the user has previously configured
    // for this metric (persisted per report and metric), or - the first time a metric is shown - the current
    // minimum/maximum value of that metric across all files, assigned to the green/red field according to the
    // metric's tendency (e.g. for a "smaller is better" metric such as Complexity, the minimum is the green/best
    // value and the maximum is the red/worst value).
    function loadThresholdsAndRender() {
        const option = getSelectedOption();
        if (!option) {
            return;
        }
        const metricTagName = option.value;

        const persisted = readPersistedThresholds(metricTagName);
        if (persisted) {
            applyThresholds(metricTagName, persisted);
            renderChart();
            return;
        }

        proxy.getMetricValueRange(metricTagName, function (t) {
            const range = t.responseObject();
            const isAscending = option.getAttribute('data-order') === 'LARGER_IS_BETTER';
            const defaults = isAscending
                ? {green: range[1], red: range[0]}
                : {green: range[0], red: range[1]};

            applyThresholds(metricTagName, defaults);
            persistThresholds(metricTagName, defaults);
            renderChart();
        });
    }

    function onThresholdInputChanged() {
        const option = getSelectedOption();
        if (!option) {
            return;
        }
        persistThresholds(option.value, readThresholds());
        renderChart();
    }

    function initializeMetricSelect() {
        const select = document.querySelector(SELECT_SELECTOR);
        if (!select) {
            return;
        }

        const stored = localStorage.getItem(metricStorageKey());
        if (stored && select.querySelector('option[value="' + stored + '"]')) {
            select.value = stored;
        }

        select.addEventListener('change', function () {
            localStorage.setItem(metricStorageKey(), select.value);
            loadThresholdsAndRender();
        });
    }

    function initializeThresholdInputs() {
        document.querySelector(GREEN_INPUT_SELECTOR).addEventListener('change', onThresholdInputChanged);
        document.querySelector(RED_INPUT_SELECTOR).addEventListener('change', onThresholdInputChanged);
    }

    // Loads the current Jenkins theme colors and hands their hex values to the server (setJenkinsColors), so
    // that the server-side red/yellow/green interpolation (see getThresholdCoverageTree) follows the currently
    // active Jenkins theme, exactly like the other coverage charts do.
    function getJenkinsColors(colorIds) { // NOPMD
        // TODO: also handle HSL colors and parse them to hex in order to use dark mode colors
        const colorHexMapping = new Map;
        colorIds.forEach(function (jenkinsId) {
            const colorHex = getComputedStyle(document.body).getPropertyValue(jenkinsId);
            if (colorHex.match(/^#[a-fA-F0-9]{6}$/) !== null) {
                colorHexMapping.set(jenkinsId, colorHex);
            }
        });
        return colorHexMapping;
    }

    function initializeJenkinsColorsAndRender() {
        proxy.getJenkinsColorIDs(function (colors) {
            const jenkinsColors = getJenkinsColors(colors.responseObject());
            const colorJson = JSON.stringify(Object.fromEntries(jenkinsColors));
            proxy.setJenkinsColors(colorJson);

            loadThresholdsAndRender();
        });
    }

    $(document).ready(function () {
        initializeMetricSelect();
        initializeThresholdInputs();

        initializeJenkinsColorsAndRender();

        window.addEventListener('resize', function () {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(function () {
                fitChartToViewport();
                resizeChart();
            }, 150);
        });
    });
})(jQuery3);
