/**
 * Client side behavior of the source code on the details page of a file in the coverage tab: filters the markers of
 * the source code view by the baseline that is selected in the app bar (see CoverageMarkerFactory for the data of the
 * markers).
 * <ul>
 *   <li>project: the coverage of all lines of the file (and the mutations that have not been killed)</li>
 *   <li>modified: the coverage of the modified lines only</li>
 *   <li>indirect: the indirect coverage changes only</li>
 * </ul>
 * Hidden markers are marked with the attribute 'hidden', afterwards the event 'prism-markers-changed' notifies the
 * source code view. The parameter 'baseline' of the URL selects the initial baseline (e.g., '?baseline=modified').
 */
(function () {
  const BASELINE_SELECTOR = "#coverage-source-baseline";

  function getBaseline() {
    const select = document.querySelector(BASELINE_SELECTOR);
    return select ? select.value : "project";
  }

  function isVisible(marker, baseline) {
    const data = marker.dataset;
    if (baseline === "indirect") {
      return data.markerBaseline === "indirect";
    }
    if (data.markerBaseline !== "project") {
      return false;
    }
    return baseline !== "modified" || data.markerModified === "true";
  }

  function filterMarkers() {
    const baseline = getBaseline();
    document.querySelectorAll(".prism-source .prism-marker").forEach((marker) => {
      marker.hidden = !isVisible(marker, baseline);
    });
    document.dispatchEvent(new CustomEvent("prism-markers-changed"));
  }

  function initialize() {
    const select = document.querySelector(BASELINE_SELECTOR);
    if (select) {
      const parameter = new URLSearchParams(window.location.search).get("baseline");
      if (Array.from(select.options).some((option) => option.value === parameter)) {
        select.value = parameter;
      }
      select.addEventListener("change", filterMarkers);
    }
    filterMarkers();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initialize);
  } else {
    initialize();
  }
})();
