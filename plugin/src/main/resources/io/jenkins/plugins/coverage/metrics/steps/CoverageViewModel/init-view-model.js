/* global jQuery3, proxy, echartsJenkinsApi, bootstrap5 */

(function () {
    proxy.getJenkinsColorIDs(function (colors) {
        const jenkinsColors = getJenkinsColors(colors.responseObject());
        const colorJson = JSON.stringify(Object.fromEntries(jenkinsColors));
        proxy.setJenkinsColors(colorJson);
        new CoverageChartGenerator(jQuery3, proxy).populateDetailsCharts(jenkinsColors);
    });
})();
