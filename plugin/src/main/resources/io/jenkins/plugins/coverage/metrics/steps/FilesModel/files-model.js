/* global $ */

(function ($) {
    const SELECT_SELECTOR = '#files-table-select';

    // The option 'modified-files' is not backed by a table of its own: it shows the table of all files and filters
    // the hidden 'modified' column (index 1, see FilesTableModel) so that only files with modified lines remain.
    const MODIFIED_FILES = 'modified-files';
    const ALL_FILES_TABLE = 'absolute-coverage';
    const MODIFIED_COLUMN = 1;
    const TABLE_IDS = ['absolute-coverage', 'modified-lines-coverage', 'indirect-coverage'];
    const COLUMNS_SELECT_SELECTOR = '#files-columns-select';

    // Suffixes of the column keys, see FilesTableModel: each metric provides a value column, and if a reference
    // build is available, a delta column and a column that combines value and delta in a single cell.
    const VALUE_SUFFIX = 'Value';
    const DELTA_SUFFIX = 'Delta';
    const COMBINED_SUFFIX = 'Combined';

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

    /**
     * Shows the table that corresponds to the selected option and hides all other tables.
     *
     * @param {String} selection the selected option of the table select box
     */
    function showTable(selection) {
        const tableId = selection === MODIFIED_FILES ? ALL_FILES_TABLE : selection;
        TABLE_IDS.forEach(function (id) {
            const panel = document.querySelector(panelSelector(id));
            if (panel) {
                panel.style.display = id === tableId ? '' : 'none';
            }
        });
        filterModifiedFiles(selection === MODIFIED_FILES);
    }

    /**
     * Shows only the modified files in the table of all files, or removes this filter again. The filter is always set
     * explicitly since DataTables persists the search state of a column (stateSave).
     *
     * @param {Boolean} showOnlyModifiedFiles determines whether to show only the modified files
     */
    function filterModifiedFiles(showOnlyModifiedFiles) {
        $(panelSelector(ALL_FILES_TABLE) + ' table.data-table').each(function () {
            $(this).DataTable()
                .column(MODIFIED_COLUMN)
                .search(showOnlyModifiedFiles ? 'true' : '')
                .draw();
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

    function columnsStorageKey() {
        return 'jenkins-coverage-files-columns'; // a personal preference that is the same for all reports
    }

    /**
     * Determines whether a metric column should be visible in the specified column mode.
     *
     * @param {String} key the data property key of the column
     * @param {String} mode the column mode, one of 'combined', 'values', or 'deltas'
     * @param {Set<String>} metricsWithDelta the metrics that provide a delta column
     * @return {Boolean|undefined} the visibility of the column, or undefined if the column is not a metric column
     */
    function isMetricColumnVisible(key, mode, metricsWithDelta) {
        if (typeof key !== 'string') {
            return undefined;
        }
        if (key.endsWith(COMBINED_SUFFIX)) {
            return mode === 'combined';
        }
        if (key.endsWith(DELTA_SUFFIX)) {
            return mode === 'deltas';
        }
        if (key.endsWith(VALUE_SUFFIX)) {
            if (mode === 'values') {
                return true;
            }
            // metrics without delta are shown with their plain value in the combined mode
            return mode === 'combined' && !metricsWithDelta.has(key.slice(0, -VALUE_SUFFIX.length));
        }
        return undefined;
    }

    function applyColumnMode(mode) {
        $('table.data-table').each(function () {
            const dataTable = $(this).DataTable();
            const keys = dataTable.columns().dataSrc().toArray();
            const metricsWithDelta = new Set(keys
                .filter(key => typeof key === 'string' && key.endsWith(COMBINED_SUFFIX))
                .map(key => key.slice(0, -COMBINED_SUFFIX.length)));

            dataTable.columns().every(function () {
                const visible = isMetricColumnVisible(this.dataSrc(), mode, metricsWithDelta);
                if (visible !== undefined && this.visible() !== visible) {
                    this.visible(visible, false);
                }
            });
            dataTable.columns.adjust();
            if (dataTable.responsive) {
                dataTable.responsive.recalc();
            }
            dataTable.draw(false);
        });
    }

    function initializeColumnsSelect() {
        const select = document.querySelector(COLUMNS_SELECT_SELECTOR);
        if (!select) {
            return; // no reference build: the tables provide value columns only
        }

        const stored = localStorage.getItem(columnsStorageKey());
        if (stored && select.querySelector('option[value="' + stored + '"]')) {
            select.value = stored;
        }

        applyColumnMode(select.value);

        select.addEventListener('change', function () {
            localStorage.setItem(columnsStorageKey(), select.value);
            applyColumnMode(select.value);
        });
    }


    $(document).ready(function () {
        initializeTableSelect();
        initializeColumnsSelect();
    });
})(jQuery3);
