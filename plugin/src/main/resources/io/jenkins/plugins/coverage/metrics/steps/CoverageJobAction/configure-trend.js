/* global jQuery3, proxy, echartsJenkinsApi, bootstrap5 */
window.addEventListener("DOMContentLoaded", () => {
    const dataHolders = document.querySelectorAll(".coverage-trend-data-holder");

    dataHolders.forEach(dataHolder => {
        function fillCoverage(trendConfiguration, jsonConfiguration) {
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

        function saveCoverage(trendConfiguration) {
            const metrics = {};

            trendConfiguration.find('input[type="checkbox"][id^="coverage-"]').each(function () {
                metrics[jQuery3(this).attr('name')] = jQuery3(this).prop('checked');
            });
            return {
                'metrics': metrics,
                'useLines': trendConfiguration.find('input[name=lines]').prop('checked')
            };
        }

        const url = dataHolder.getAttribute("data-url");
        echartsJenkinsApi.configureTrend('coverage-' + url, fillCoverage, saveCoverage);
    });
});
