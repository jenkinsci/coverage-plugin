(function () {
    const RESPONSE_SELECTOR = '#remote-api-response';
    const FOOTER_SELECTOR = '.page-footer';
    const MIN_HEIGHT = 150;
    const BOTTOM_MARGIN = 40;
    let resizeTimeout;

    function getFooterHeight() {
        const footer = document.querySelector(FOOTER_SELECTOR);
        if (!footer) {
            return 0;
        }
        return footer.getBoundingClientRect().height;
    }

    // Sizes the response so that it fills the browser viewport (while keeping the footer visible), the same way
    // the log page sizes its cards (see log-model.js).
    function fitToViewport(response) {
        const bottomBoundary = window.innerHeight - getFooterHeight() - BOTTOM_MARGIN;
        const top = response.getBoundingClientRect().top;
        response.style.height = Math.max(bottomBoundary - top, MIN_HEIGHT) + 'px';
    }

    // Calls the remote API in the browser (with the session of the current user) and shows the formatted JSON.
    // The text is set via textContent so that the values of the response are never interpreted as HTML.
    function loadResponse(response) {
        fetch(response.dataset.url, {credentials: 'same-origin', headers: {'Accept': 'application/json'}})
            .then(function (result) {
                if (!result.ok) {
                    throw new Error(result.status + ' ' + result.statusText);
                }
                return result.json();
            })
            .then(function (json) {
                response.textContent = JSON.stringify(json, null, 2);
            })
            .catch(function (error) {
                response.textContent = response.dataset.error + ' ' + error.message;
            });
    }

    function initialize() {
        const response = document.querySelector(RESPONSE_SELECTOR);
        if (!response) {
            return;
        }

        fitToViewport(response);
        loadResponse(response);

        window.addEventListener('resize', function () {
            clearTimeout(resizeTimeout);
            resizeTimeout = setTimeout(function () {
                fitToViewport(response);
            }, 150);
        });
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', initialize);
    }
    else {
        initialize();
    }
})();
