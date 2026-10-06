/*
 * Hover-highlight for contiguous coverage blocks in the source code view (table.source).
 *
 * This used to be implemented purely in CSS, using :has() combined with the general sibling
 * combinator (~) and :hover, e.g.:
 *
 *   table.source tr:has(~ tr.coverNone:hover) ~ tr:not(.coverNone) + tr.coverNone:after { ... }
 *
 * That forces the browser to re-check, on every hover change, whether *any* later row in the
 * table matches -- an O(n^2) cost in the number of rows (roughly 30 such selectors per coverage
 * status). Barely measurable at ~30 lines, it became noticeably laggy on source files with a few
 * hundred lines or more.
 *
 * This module replaces the :has()/~ selectors with an O(n) scan (re-run whenever the table's rows
 * change, e.g. when the Files tab loads a different file's source into the same table via AJAX)
 * that groups directly adjacent rows sharing the same coverage status (coverFull / coverNone /
 * coverPart -- see CoverageSourcePrinter) into blocks, and a single delegated mouseover/mouseleave
 * listener that toggles a "block-hover" class on just the rows of the currently hovered block,
 * using a precomputed row -> block lookup (O(1) per hover). The visuals (overlay tint) stay in
 * view-model.css, driven by plain class selectors. The block label (see showLabel/hideLabel below)
 * is handled separately, since it needs to stay visible regardless of horizontal scroll position.
 */
(function () {
    'use strict';

    const TABLE_SELECTOR = 'table.source';
    const STATUS_CLASSES = ['coverFull', 'coverNone', 'coverPart'];
    const LABELS = {
        coverNone: 'Uncovered code',
        coverFull: 'Covered code',
        coverPart: 'Partially covered code'
    };
    const MUTATION_LABELS = {
        coverNone: 'Mutations survived',
        coverFull: 'All mutations killed',
        coverPart: 'Some mutations survived'
    };
    const LABEL_STATUS_CLASS = {
        coverNone: 'block-label-none',
        coverFull: 'block-label-full',
        coverPart: 'block-label-part'
    };
    const HOVER_CLASS = 'block-hover';
    const BLOCK_FIRST_CLASS = 'block-first';
    const FLOATING_LABEL_CLASS = 'coverage-block-label--overflow';

    // Nearest ancestor that actually establishes a horizontal scrollport (the div wrapping the
    // dedicated Source code view sets this; the per-file preview embedded in the file coverage
    // tables (coverage-table.jelly) doesn't have one at all -- see showLabel below).
    function getScrollParent(element) {
        let node = element.parentElement;
        while (node && node !== document.body) {
            const overflowX = getComputedStyle(node).overflowX;
            if (overflowX === 'auto' || overflowX === 'scroll') {
                return node;
            }
            node = node.parentElement;
        }
        return null;
    }

    function statusOf(row) {
        for (let i = 0; i < STATUS_CLASSES.length; i++) {
            if (row.classList.contains(STATUS_CLASSES[i])) {
                return STATUS_CLASSES[i];
            }
        }
        return null;
    }

    // O(n) pass over the table's current rows: groups directly adjacent rows that share the same
    // coverage status into a block, tags each block's first row (for the label) with its text, and
    // builds a row -> block-rows map for O(1) lookup on hover.
    function indexBlocks(table) {
        const isMutationTable = table.querySelector('tr.mutation') !== null;
        const labels = isMutationTable ? MUTATION_LABELS : LABELS;
        const rowToBlockRows = new Map();

        let previousStatus = null;
        let currentBlockRows = null;

        table.querySelectorAll('tr').forEach(function (row) {
            const status = statusOf(row);
            if (status === null) {
                previousStatus = null;
                currentBlockRows = null;
                return;
            }
            if (status !== previousStatus) {
                currentBlockRows = [];
                row.classList.add(BLOCK_FIRST_CLASS);
                row.setAttribute('data-block-label', labels[status]);
            }
            currentBlockRows.push(row);
            rowToBlockRows.set(row, currentBlockRows);
            previousStatus = status;
        });

        return rowToBlockRows;
    }

    function setHover(rows, active) {
        rows.forEach(function (row) {
            row.classList.toggle(HOVER_CLASS, active);
        });
    }

    // The line-number column's width (and so the left offset the hits column must stick to) varies
    // with the file's line count (more digits for larger files). Measured after each (re-)render
    // rather than hard-coded, and exposed as a CSS custom property so view-model.css can use it for
    // position: sticky -- see the "Keep the line-number and hits columns visible" comment there.
    function updateStickyOffset(table) {
        const line = table.querySelector('td.line');
        if (line) {
            table.style.setProperty('--source-line-col-width', line.getBoundingClientRect().width + 'px');
        }
    }

    function initTable(table) {
        let rowToBlockRows = indexBlocks(table);
        let hoveredRows = null;
        updateStickyOffset(table);
        const scrollParent = getScrollParent(table);

        // The block label ("Uncovered code" etc.) is a real <td>, appended to the hovered block's
        // first row on hover and detached again afterwards -- see showLabel/hideLabel, and the two
        // coverage-block-label CSS variants there, for why it needs to be an actual table cell (not
        // a pseudo-element) and why it switches between position:absolute and position:sticky
        // depending on whether the table currently scrolls. Reused across hovers rather than
        // recreated each time.
        const label = document.createElement('td');
        label.className = 'coverage-block-label';

        function showLabel(row) {
            const text = row.getAttribute('data-block-label');
            const status = statusOf(row);
            label.remove();
            if (!text || !status) {
                return;
            }
            label.textContent = text;
            // Appending this cell to only the hovered row can nudge the whole table a few pixels
            // wider than before (an extra column, even an empty one elsewhere, still counts towards
            // the table's auto-layout width). Invisible on a table that already scrolls -- but on
            // one that exactly fits its container, it flips a previously-absent horizontal
            // scrollbar on and off on every hover, a visible jump. So: only ask for the sticky,
            // pinned-to-the-viewport-edge variant (coverage-block-label--overflow, see the CSS) when
            // the table actually needs to scroll right now; otherwise fall back to the plain variant,
            // which uses position:absolute specifically because -- unlike sticky/relative/static --
            // that removes the cell from the table's layout flow entirely, so it can't affect column
            // widths no matter how many times it's added and removed.
            const usesFloatingLabel = !!scrollParent && scrollParent.scrollWidth > scrollParent.clientWidth + 1;
            label.className = 'coverage-block-label ' + LABEL_STATUS_CLASS[status]
                + (usesFloatingLabel ? ' ' + FLOATING_LABEL_CLASS : '');
            row.appendChild(label);
        }

        function hideLabel() {
            label.remove();
        }

        // The Files tab loads a different file's source into this same <table> element via AJAX
        // (see CoverageViewModel/view-model.js#initializeSourceCodeSelection), replacing all of
        // its rows. Re-index whenever that happens, so hovering the newly loaded rows still works,
        // and re-measure the sticky offset since a different file can have a different line count.
        //
        // showLabel/hideLabel move the very <td> this observer watches for (they add/remove it
        // under the observed subtree), so every hover would otherwise immediately trigger this
        // same callback and undo itself -- resetting hoveredRows and re-hiding the label before it
        // ever painted, while leaving the "block-hover" tint classes it had just set behind (this
        // callback doesn't clear those, it only forgets about them, so they never stopped being
        // stuck). Mutations caused only by our own label element are therefore ignored here: either
        // its addedNodes/removedNodes are exactly label (attaching/detaching it to/from a row), or
        // its target is label itself (label.textContent changing its own text-node child) -- the
        // latter matters even while label is detached: verified live that a childList mutation on a
        // node just removed from the observed subtree, earlier in the same synchronous batch, is
        // still delivered to the observer, so relying on label being detached first is not enough
        // on its own.
        new MutationObserver(function (mutations) {
            const onlyLabel = mutations.every(function (mutation) {
                if (mutation.target === label) {
                    return true;
                }
                return [].concat(
                    Array.prototype.slice.call(mutation.addedNodes),
                    Array.prototype.slice.call(mutation.removedNodes)
                ).every(function (node) {
                    return node === label;
                });
            });
            if (onlyLabel) {
                return;
            }
            hoveredRows = null;
            hideLabel();
            rowToBlockRows = indexBlocks(table);
            updateStickyOffset(table);
        }).observe(table, {childList: true, subtree: true});

        table.addEventListener('mouseover', function (event) {
            const row = event.target.closest('tr');
            const blockRows = row ? rowToBlockRows.get(row) : undefined;
            if (blockRows === hoveredRows) {
                return;
            }
            if (hoveredRows) {
                setHover(hoveredRows, false);
            }
            hoveredRows = blockRows || null;
            if (hoveredRows) {
                setHover(hoveredRows, true);
                showLabel(hoveredRows[0]);
            }
            else {
                hideLabel();
            }
        });

        table.addEventListener('mouseleave', function () {
            if (hoveredRows) {
                setHover(hoveredRows, false);
                hoveredRows = null;
            }
            hideLabel();
        });
    }

    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll(TABLE_SELECTOR).forEach(initTable);
    });
})();
