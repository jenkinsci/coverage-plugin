/* global $, proxy */

(function ($) {
    const SELECT_SELECTOR = '#files-table-select';
    const TABLE_IDS = ['absolute-coverage', 'modified-lines-coverage', 'indirect-coverage'];

    // All three tables (as far as they are offered, see the corresponding <j:if> guards in index.jelly) are rendered
    // eagerly. Which one is visible is driven by the select box below.
    function getReportId() {
        const select = document.querySelector(SELECT_SELECTOR);
        const reportId = select ? select.getAttribute('data-report-id') : null;
        return reportId || 'default';
    }

    function tableStorageKey() {
        return 'jenkins-coverage-files-table-' + getReportId();
    }

    function panelSelector(tableId) {
        return '#files-panel-' + tableId;
    }

    function showTable(tableId) {
        TABLE_IDS.forEach(function (id) {
            const panel = document.querySelector(panelSelector(id));
            if (panel) {
                panel.style.display = id === tableId ? '' : 'none';
            }
        });
    }

    /**
     * Initializes a selection listener for a datatable which loads the selected source code.
     *
     * @param {String} tableId The ID of the DataTable
     */
    function initializeSourceCodeSelection(tableId) {
        const inlineTable = $('#' + tableId + '-table-inline');
        if (inlineTable.length === 0) {
            return; // this table is either not offered, or no source code is stored
        }
        const datatable = inlineTable.DataTable();
        const sourceView = $('#' + tableId + '-source-file');
        const noFileSelectedBanner = $('#' + tableId + '-no-selection');
        const noSourceAvailableBanner = $('#' + tableId + '-no-source');

        function showNoSelection() {
            sourceView.hide();
            noSourceAvailableBanner.hide();
            noFileSelectedBanner.show();
        }

        function showNoSourceCode() {
            sourceView.hide();
            noFileSelectedBanner.hide();
            noSourceAvailableBanner.show();
        }

        function showSourceCode() {
            noFileSelectedBanner.hide();
            noSourceAvailableBanner.hide();
            sourceView.show();
        }

        showNoSelection();
        datatable.on('select', function (e, dt, type, indexes) {
            if (type === 'row') {
                showSourceCode();
                sourceView.html('Loading...');
                const rowData = datatable.rows(indexes).data().toArray();
                proxy.getSourceCode(rowData[0].fileHash, tableId + '-table', function (t) {
                    const sourceCode = t.responseObject();
                    if (sourceCode === "n/a") {
                        showNoSourceCode();
                    }
                    else {
                        sourceView.html(sourceCode);
                    }
                });
            }
            else {
                showNoSelection();
            }
        });
        datatable.on('deselect', function () {
            showNoSelection();
        });
    }

    function initializeTableSelect() {
        const select = document.querySelector(SELECT_SELECTOR);
        if (!select) {
            return;
        }

        const stored = localStorage.getItem(tableStorageKey());
        if (stored && select.querySelector('option[value="' + stored + '"]')) {
            select.value = stored;
        }

        showTable(select.value);

        select.addEventListener('change', function () {
            localStorage.setItem(tableStorageKey(), select.value);
            showTable(select.value);
        });
    }

    function initializeChangedFilesToggle() {
        $('input[id ^= "changed"]').on('change', function () {
            const showChanged = $(this).prop('checked');
            $('table.data-table').each(function () {
                const table = $(this).DataTable();
                table.column(1).search(showChanged ? 'true' : '').draw();
            });
        });
    }

    $(document).ready(function () {
        initializeTableSelect();

        TABLE_IDS.forEach(initializeSourceCodeSelection);

        initializeChangedFilesToggle();
    });
})(jQuery3);
