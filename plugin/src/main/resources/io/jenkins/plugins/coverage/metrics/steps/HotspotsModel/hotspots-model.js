/* global $, proxy, echarts, echartsJenkinsApi, CoverageColorScale */

(function ($) {
    const CHART_SELECTOR = '#coverage-histogram';
    const SELECT_SELECTOR = '#histogram-metric';
    const BUCKET_WIDTH = 10;
    const PREFERRED_METRIC = 'line';
    let resizeTimeout;
    let dataSet = null; // the distribution of all metrics, see HotspotsModel#getHistogramData

    function getReportId() {
        const element = document.querySelector(CHART_SELECTOR);
        const reportId = element ? element.getAttribute('data-report-id') : null;
        return reportId || 'default';
    }

    function storageKey() {
        return 'jenkins-coverage-hotspots-metric-' + getReportId();
    }

    function readStoredMetric() {
        try {
            return localStorage.getItem(storageKey());
        }
        catch (e) {
            return null;
        }
    }

    function storeMetric(id) {
        try {
            localStorage.setItem(storageKey(), id);
        }
        catch (e) {
            // ignore: storage is only a convenience
        }
    }

    function findMetric(id) {
        return dataSet.metrics.find(function (metric) {
            return metric.id === id;
        });
    }

    function populateSelect() {
        const select = document.querySelector(SELECT_SELECTOR);
        select.replaceChildren();
        dataSet.metrics.forEach(function (metric) {
            const option = document.createElement('option');
            option.value = metric.id;
            option.textContent = metric.name;
            select.appendChild(option);
        });
        const stored = readStoredMetric();
        if (stored && findMetric(stored)) {
            select.value = stored;
        }
        else if (findMetric(PREFERRED_METRIC)) {
            select.value = PREFERRED_METRIC;
        }
    }

    // The bars use the same green/red thresholds as the gauges of the Overview tab. A bucket is colored by its lower
    // bound, i.e., the bucket "90-99%" is green if the green threshold is 90.
    function readThresholds() {
        return CoverageColorScale.readStoredThresholds(getReportId())
            || {green: CoverageColorScale.DEFAULT_GREEN, red: CoverageColorScale.DEFAULT_RED};
    }

    function createOption(metric, element) {
        const textColor = echartsJenkinsApi.getTextColor();
        const thresholds = readThresholds();
        const filesLabel = element.getAttribute('data-files-label');
        return {
            animation: false,
            tooltip: {
                trigger: 'axis',
                axisPointer: {type: 'shadow'},
                formatter: function (params) {
                    return '<strong>' + echarts.format.encodeHTML(metric.name) + '</strong><br/>'
                        + echarts.format.encodeHTML(params[0].name) + ': ' + params[0].value
                        + ' ' + echarts.format.encodeHTML(filesLabel);
                }
            },
            grid: {left: 20, right: 20, top: 30, bottom: 10, containLabel: true},
            xAxis: {
                type: 'category',
                data: dataSet.labels,
                axisLabel: {color: textColor}
            },
            yAxis: {
                type: 'value',
                minInterval: 1,
                name: filesLabel,
                nameTextStyle: {color: textColor, align: 'left'},
                axisLabel: {color: textColor},
                splitLine: {lineStyle: {opacity: 0.25}}
            },
            series: [{
                type: 'bar',
                data: metric.counts.map(function (count, index) {
                    return {
                        value: count,
                        itemStyle: {color: CoverageColorScale.colorFor(Math.min(100, index * BUCKET_WIDTH), thresholds)}
                    };
                }),
                label: {show: true, position: 'top', color: textColor}
            }]
        };
    }

    function renderChart() {
        const element = document.querySelector(CHART_SELECTOR);
        if (!element || !dataSet) {
            return;
        }
        const metric = findMetric($(SELECT_SELECTOR).val());
        if (!metric) {
            element.textContent = element.getAttribute('data-empty-label');
            return;
        }
        storeMetric(metric.id);
        const chart = element.echart || echarts.init(element);
        element.echart = chart; // NOPMD
        chart.setOption(createOption(metric, element), true);
        chart.resize();
    }

    function loadData() {
        proxy.getHistogramData(function (t) {
            dataSet = JSON.parse(t.responseObject());
            populateSelect();
            renderChart();
        });
    }

    // The header of a sortable ranking switches between the two server-side rankings (most missed items, lowest
    // coverage): both are rendered, only the selected one is visible.
    function initSorting() {
        document.querySelectorAll('.coverage-hotspots__grid[data-sortable]').forEach(function (grid) {
            const buttons = grid.querySelectorAll('.coverage-hotspots__sort');
            buttons.forEach(function (button) {
                button.addEventListener('click', function () {
                    const sort = button.getAttribute('data-sort');
                    buttons.forEach(function (other) {
                        other.classList.toggle('coverage-hotspots__sort--active', other === button);
                    });
                    grid.querySelectorAll('.coverage-hotspots__rows').forEach(function (rows) {
                        rows.classList.toggle('coverage-hotspots__rows--inactive', rows.getAttribute('data-rows') !== sort);
                    });
                });
            });
        });
    }

    $(document).ready(function () {
        initSorting();
        loadData();

        $(SELECT_SELECTOR).on('change', renderChart);

        $(window).on('resize', function () {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(function () {
                const element = document.querySelector(CHART_SELECTOR);
                if (element && element.echart) {
                    element.echart.resize();
                }
            }, 150);
        });
    });
})(jQuery3);
