/* global $, proxy, echarts, echartsJenkinsApi */

(function ($) {
    const CHART_SELECTOR = '#coverage-scatter';
    const X_SELECTOR = '#scatter-x-metric';
    const Y_SELECTOR = '#scatter-y-metric';
    const SIZE_SELECTOR = '#scatter-size-metric';
    const CONSTANT_SIZE = '';
    const MIN_SYMBOL_SIZE = 6;
    const MAX_SYMBOL_SIZE = 30;
    const DEFAULT_SYMBOL_SIZE = 9;
    const SIZE_PERCENTILE = 0.95;
    const FOOTER_SELECTOR = '.page-footer';
    const MIN_CHART_HEIGHT = 300;
    const BOTTOM_MARGIN = 40;
    let resizeTimeout;
    let dataSet = null; // all metrics of all files, see ScatterModel#getScatterData

    function getReportId() {
        const element = document.querySelector(CHART_SELECTOR);
        const reportId = element ? element.getAttribute('data-report-id') : null;
        return reportId || 'default';
    }

    function storageKey(baseKey) {
        return 'jenkins-coverage-scatter-' + baseKey + '-' + getReportId();
    }

    function readStored(baseKey) {
        try {
            return localStorage.getItem(storageKey(baseKey));
        }
        catch (e) {
            return null;
        }
    }

    function store(baseKey, value) {
        try {
            localStorage.setItem(storageKey(baseKey), String(value));
        }
        catch (e) {
            // ignore: storage is only a convenience
        }
    }

    function fitChartToViewport() {
        const element = document.querySelector(CHART_SELECTOR);
        if (!element) {
            return;
        }
        const footer = document.querySelector(FOOTER_SELECTOR);
        const footerHeight = footer ? footer.getBoundingClientRect().height : 0;
        const top = element.getBoundingClientRect().top;
        const availableHeight = window.innerHeight - top - footerHeight - BOTTOM_MARGIN;
        element.style.height = Math.max(availableHeight, MIN_CHART_HEIGHT) + 'px';
    }

    function resizeChart() {
        const element = document.querySelector(CHART_SELECTOR);
        if (element && element.echart) {
            element.echart.resize();
        }
    }

    function findMetric(id) {
        return dataSet.metrics.find(function (metric) {
            return metric.id === id;
        });
    }

    function fillSelect(select, storedKey, defaultId) {
        const labels = {coverage: select.dataset.coverageLabel, software: select.dataset.softwareLabel};
        const previous = readStored(storedKey);
        select.replaceChildren();
        ['coverage', 'software'].forEach(function (group) {
            const metrics = dataSet.metrics.filter(function (metric) {
                return metric.group === group;
            });
            if (metrics.length === 0) {
                return;
            }
            const optgroup = document.createElement('optgroup');
            optgroup.label = labels[group];
            metrics.forEach(function (metric) {
                const option = document.createElement('option');
                option.value = metric.id;
                option.textContent = metric.name;
                optgroup.appendChild(option);
            });
            select.appendChild(optgroup);
        });
        select.value = previous && findMetric(previous) ? previous : defaultId;
    }

    // Default: the first percentage on X and the first absolute metric on Y (e.g. Line Coverage vs. Complexity).
    function populateSelects() {
        const metrics = dataSet.metrics;
        if (metrics.length === 0) {
            return;
        }
        const first = metrics[0];
        const other = metrics.find(function (metric) {
            return metric.group !== first.group;
        }) || metrics[1] || first;
        fillSelect(document.querySelector(X_SELECTOR), 'x', first.id);
        fillSelect(document.querySelector(Y_SELECTOR), 'y', other.id);
        fillSizeSelect();
    }

    // Only absolute metrics make sense as size. Default: the number of statements/lines, if available.
    function fillSizeSelect() {
        const select = document.querySelector(SIZE_SELECTOR);
        select.replaceChildren();
        const constant = document.createElement('option');
        constant.value = CONSTANT_SIZE;
        constant.textContent = select.dataset.constantLabel;
        select.appendChild(constant);
        const candidates = dataSet.metrics.filter(function (metric) {
            return metric.group === 'software';
        });
        candidates.forEach(function (metric) {
            const option = document.createElement('option');
            option.value = metric.id;
            option.textContent = metric.name;
            select.appendChild(option);
        });
        const preferred = candidates.find(function (metric) {
            return metric.id === 'ncss';
        }) || candidates.find(function (metric) {
            return metric.id === 'loc';
        });
        const previous = readStored('size');
        if (previous !== null && (previous === CONSTANT_SIZE || findMetric(previous))) {
            select.value = previous;
        }
        else {
            select.value = preferred ? preferred.id : CONSTANT_SIZE;
        }
    }

    // Maps the values to a symbol size. The area (not the radius) is proportional to the value, large outliers are
    // capped at a percentile so that they do not squash all other points.
    function createSizeFunction(sizeMetric, indices) {
        if (!sizeMetric) {
            return function () {
                return DEFAULT_SYMBOL_SIZE;
            };
        }
        const values = indices.map(function (index) {
            return sizeMetric.values[index];
        }).filter(function (value) {
            return value !== undefined && value > 0;
        }).sort(function (a, b) {
            return a - b;
        });
        if (values.length === 0) {
            return function () {
                return DEFAULT_SYMBOL_SIZE;
            };
        }
        const max = values[Math.min(values.length - 1, Math.floor(values.length * SIZE_PERCENTILE))];
        return function (index) {
            const value = sizeMetric.values[index];
            if (value === undefined || value <= 0) {
                return MIN_SYMBOL_SIZE;
            }
            const ratio = Math.sqrt(Math.min(value, max) / max);
            return MIN_SYMBOL_SIZE + ratio * (MAX_SYMBOL_SIZE - MIN_SYMBOL_SIZE);
        };
    }

    function createAxis(metric, isX, textColor) {
        return {
            type: 'value',
            scale: true, // do not force zero: the points should use the whole area
            name: metric.name + (metric.unit ? ' (' + metric.unit + ')' : ''),
            nameLocation: 'middle',
            nameGap: isX ? 30 : 48,
            nameTextStyle: {color: textColor},
            axisLabel: {
                color: textColor,
                formatter: function (value) {
                    return value + metric.unit;
                }
            },
            splitLine: {lineStyle: {opacity: 0.25}}
        };
    }

    function formatValue(metric, value) {
        return value + metric.unit;
    }

    function createOption(xMetric, yMetric, sizeMetric) {
        const textColor = echartsJenkinsApi.getTextColor();

        // one point per file, files that do not provide both metrics are skipped
        const included = [];
        dataSet.names.forEach(function (name, index) {
            if (xMetric.values[index] !== undefined && yMetric.values[index] !== undefined) {
                included.push(index);
            }
        });
        const sizeOf = createSizeFunction(sizeMetric, included);
        const points = included.map(function (index) {
            return {
                name: dataSet.names[index],
                path: dataSet.paths[index],
                hash: dataSet.hashes[index],
                value: [xMetric.values[index], yMetric.values[index]],
                size: sizeMetric ? sizeMetric.values[index] : undefined,
                symbolSize: sizeOf(index)
            };
        });

        return {
            animation: false,
            tooltip: {
                trigger: 'item',
                formatter: function (params) {
                    return '<strong>' + echarts.format.encodeHTML(params.name) + '</strong><br/>'
                        + echarts.format.encodeHTML(params.data.path) + '<br/>'
                        + echarts.format.encodeHTML(xMetric.name) + ': ' + formatValue(xMetric, params.value[0]) + '<br/>'
                        + echarts.format.encodeHTML(yMetric.name) + ': ' + formatValue(yMetric, params.value[1])
                        + (sizeMetric && params.data.size !== undefined
                            ? '<br/>' + echarts.format.encodeHTML(sizeMetric.name) + ': ' + params.data.size : '');
                }
            },
            // the axis names are not part of containLabel, so the grid needs room for them
            grid: {left: 38, right: 30, top: 30, bottom: 38, containLabel: true},
            xAxis: createAxis(xMetric, true, textColor),
            yAxis: createAxis(yMetric, false, textColor),
            series: [{
                name: yMetric.name + ' / ' + xMetric.name,
                type: 'scatter',
                data: points,
                large: points.length > 2000,
                itemStyle: {color: '#1f78b4', opacity: 0.65, borderColor: textColor, borderWidth: 0.5},
                emphasis: {itemStyle: {opacity: 1, borderWidth: 2}}
            }]
        };
    }

    function renderChart() {
        const element = document.querySelector(CHART_SELECTOR);
        if (!element || !dataSet || dataSet.metrics.length === 0) {
            return;
        }
        const xMetric = findMetric($(X_SELECTOR).val());
        const yMetric = findMetric($(Y_SELECTOR).val());
        if (!xMetric || !yMetric) {
            return;
        }
        store('x', xMetric.id);
        store('y', yMetric.id);
        const sizeMetric = findMetric($(SIZE_SELECTOR).val());
        store('size', sizeMetric ? sizeMetric.id : CONSTANT_SIZE);

        fitChartToViewport();
        const chart = element.echart || echarts.init(element);
        element.echart = chart; // NOPMD
        chart.setOption(createOption(xMetric, yMetric, sizeMetric), true);
        chart.off('click');
        chart.on('click', function (params) {
            // open the source code view of the clicked file
            window.location.href = element.getAttribute('data-base-url') + params.data.hash;
        });
        chart.resize();
    }

    function loadData() {
        proxy.getScatterData(function (t) {
            dataSet = JSON.parse(t.responseObject());
            populateSelects();
            renderChart();
        });
    }

    $(document).ready(function () {
        loadData();

        $(X_SELECTOR + ', ' + Y_SELECTOR + ', ' + SIZE_SELECTOR).on('change', renderChart);

        $(window).on('resize', function () {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(function () {
                fitChartToViewport();
                resizeChart();
            }, 150);
        });
    });
})(jQuery3);
