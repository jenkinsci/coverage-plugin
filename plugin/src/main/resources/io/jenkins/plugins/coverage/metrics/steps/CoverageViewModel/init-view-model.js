/* global jQuery3, proxy, echartsJenkinsApi, bootstrap5 */

(function () {
    function fillDialog(trendConfiguration, jsonConfiguration) {
        const metrics = jsonConfiguration['metrics'];
        if (metrics) {
            Object.entries(metrics).forEach(([metric, isChecked]) => {
                trendConfiguration.find(`input[type="checkbox"][name="${metric}"]`).prop('checked', isChecked);
            });
        }
        const useLines = jsonConfiguration['useLines'];
        if (useLines) {
            trendConfiguration.find('input[type="checkbox"][name="lines"]').prop('checked', useLines);
        }
    }

    function saveDialog(trendConfiguration) {
        const metrics = {};

        trendConfiguration.find('input[type="checkbox"][id*="-history-metric"]').each(function () {
            metrics[jQuery3(this).attr('name')] = jQuery3(this).prop('checked');
        });
        return {
            'metrics': metrics,
            'useLines': trendConfiguration.find('input[name=lines]').prop('checked')
        };
    }

    echartsJenkinsApi.configureChart('coverage-history', fillDialog, saveDialog);
    echartsJenkinsApi.configureChart('metrics-history', fillDialog, saveDialog);

    proxy.getJenkinsColorIDs(function (colors) {
        const jenkinsColors = getJenkinsColors(colors.responseObject());
        const colorJson = JSON.stringify(Object.fromEntries(jenkinsColors));
        proxy.setJenkinsColors(colorJson);
        new CoverageChartGenerator(jQuery3, proxy).populateDetailsCharts(jenkinsColors);
    });
})();
