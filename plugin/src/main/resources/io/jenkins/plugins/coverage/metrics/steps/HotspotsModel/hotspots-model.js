/* global $, proxy, echarts, echartsJenkinsApi, CoverageColorScale */

(function ($) {
    const CHART_SELECTOR = '#coverage-histogram';
    const SELECT_SELECTOR = '#histogram-metric';
    const AGGREGATION_SELECTOR = '#histogram-aggregation';
    const AGGREGATION_CONTAINER_SELECTOR = '#histogram-aggregation-container';
    const CARD_SELECTOR = '.coverage-hotspots__card[data-metric]';
    const BUCKET_WIDTH = 10;
    const PREFERRED_METRIC = 'line';
    const PREFERRED_AGGREGATION = 'overall';
    // The software metrics are colored by the position of the bucket: from green (best bucket) to red (worst bucket).
    const POSITION_THRESHOLDS = {green: 100, red: 0};
    let resizeTimeout;
    let dataSet = null; // the distributions of all metrics, see HotspotsModel#getHistogramData

    function getReportId() {
        const element = document.querySelector(CHART_SELECTOR);
        const reportId = element ? element.getAttribute('data-report-id') : null;
        return reportId || 'default';
    }

    function storageKey(property) {
        return 'jenkins-coverage-hotspots-' + property + '-' + getReportId();
    }

    function readStored(property) {
        try {
            return localStorage.getItem(storageKey(property));
        }
        catch (e) {
            return null;
        }
    }

    function store(property, id) {
        try {
            localStorage.setItem(storageKey(property), id);
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

    function findHistogram(metric, id) {
        return metric.histograms.find(function (histogram) {
            return histogram.id === id;
        });
    }

    function createOptionElement(value, text) {
        const option = document.createElement('option');
        option.value = value;
        option.textContent = text;
        return option;
    }

    // The metrics are grouped: coverage metrics first, then the software metrics.
    function populateSelect() {
        const element = document.querySelector(CHART_SELECTOR);
        const select = document.querySelector(SELECT_SELECTOR);
        select.replaceChildren();
        [
            {coverage: true, label: element.getAttribute('data-coverage-label')},
            {coverage: false, label: element.getAttribute('data-metrics-label')}
        ].forEach(function (group) {
            const metrics = dataSet.metrics.filter(function (metric) {
                return metric.coverage === group.coverage;
            });
            if (metrics.length > 0) {
                const optionGroup = document.createElement('optgroup');
                optionGroup.label = group.label;
                metrics.forEach(function (metric) {
                    optionGroup.appendChild(createOptionElement(metric.id, metric.name));
                });
                select.appendChild(optionGroup);
            }
        });
        const stored = readStored('metric');
        if (stored && findMetric(stored)) {
            select.value = stored;
        }
        else if (findMetric(PREFERRED_METRIC)) {
            select.value = PREFERRED_METRIC;
        }
    }

    // The names of the aggregations depend on the metric (the worst value is the maximum or the minimum), so the
    // options are recreated whenever the metric changes.
    function populateAggregations(metric) {
        const container = document.querySelector(AGGREGATION_CONTAINER_SELECTOR);
        const select = document.querySelector(AGGREGATION_SELECTOR);
        select.replaceChildren();
        metric.histograms.forEach(function (histogram) {
            select.appendChild(createOptionElement(histogram.id, histogram.name));
        });
        const stored = readStored('aggregation');
        select.value = stored && findHistogram(metric, stored) ? stored : PREFERRED_AGGREGATION;
        container.style.display = metric.histograms.length > 1 ? 'flex' : 'none';
    }

    function getSelectedHistogram(metric) {
        return findHistogram(metric, $(AGGREGATION_SELECTOR).val()) || metric.histograms[0];
    }

    // The bars of the coverage use the same green/red thresholds as the gauges of the Overview tab. A bucket is
    // colored by its lower bound, i.e., the bucket "90-99%" is green if the green threshold is 90.
    function readThresholds() {
        return CoverageColorScale.readStoredThresholds(getReportId())
            || {green: CoverageColorScale.DEFAULT_GREEN, red: CoverageColorScale.DEFAULT_RED};
    }

    function createColorProvider(metric, bucketCount) {
        if (metric.coverage) {
            const thresholds = readThresholds();
            return function (index) {
                return CoverageColorScale.colorFor(Math.min(100, index * BUCKET_WIDTH), thresholds);
            };
        }
        return function (index) {
            const position = bucketCount > 1 ? index / (bucketCount - 1) : 1;
            const quality = metric.smallerIsBetter ? 1 - position : position;
            return CoverageColorScale.colorFor(quality * 100, POSITION_THRESHOLDS);
        };
    }

    function createOption(metric, histogram, element) {
        const textColor = echartsJenkinsApi.getTextColor();
        const colorFor = createColorProvider(metric, histogram.counts.length);
        const filesLabel = element.getAttribute('data-files-label');
        const title = metric.histograms.length > 1 ? metric.name + ' (' + histogram.name + ')' : metric.name;
        return {
            animation: false,
            tooltip: {
                trigger: 'axis',
                axisPointer: {type: 'shadow'},
                formatter: function (params) {
                    return '<strong>' + echarts.format.encodeHTML(title) + '</strong><br/>'
                        + echarts.format.encodeHTML(params[0].name) + ': ' + params[0].value
                        + ' ' + echarts.format.encodeHTML(filesLabel);
                }
            },
            grid: {left: 20, right: 20, top: 30, bottom: 10, containLabel: true},
            xAxis: {
                type: 'category',
                data: histogram.labels,
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
                data: histogram.counts.map(function (count, index) {
                    return {
                        value: count,
                        itemStyle: {color: colorFor(index)}
                    };
                }),
                label: {show: true, position: 'top', color: textColor}
            }]
        };
    }

    // Only the ranking of the selected metric is visible; all other cards (e.g., the risk ranking) stay visible.
    function showRankings(metricId) {
        document.querySelectorAll(CARD_SELECTOR).forEach(function (card) {
            card.hidden = card.getAttribute('data-metric') !== metricId;
        });
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
        store('metric', metric.id);
        showRankings(metric.id);

        const histogram = getSelectedHistogram(metric);
        if (metric.histograms.length > 1) {
            store('aggregation', histogram.id);
        }
        const chart = element.echart || echarts.init(element);
        element.echart = chart; // NOPMD
        chart.setOption(createOption(metric, histogram, element), true);
        chart.resize();
    }

    function selectMetric() {
        const metric = findMetric($(SELECT_SELECTOR).val());
        if (metric) {
            populateAggregations(metric);
        }
        renderChart();
    }

    function loadData() {
        proxy.getHistogramData(function (t) {
            dataSet = JSON.parse(t.responseObject());
            populateSelect();
            selectMetric();
        });
    }

    // The header of a sortable ranking switches between the two server-side rankings (e.g., most missed items and
    // lowest coverage): both are rendered, only the selected one is visible.
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

        $(SELECT_SELECTOR).on('change', selectMetric);
        $(AGGREGATION_SELECTOR).on('change', renderChart);

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
