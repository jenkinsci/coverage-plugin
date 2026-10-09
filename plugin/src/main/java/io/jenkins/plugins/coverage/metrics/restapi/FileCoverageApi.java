package io.jenkins.plugins.coverage.metrics.restapi;

import edu.hm.hafner.coverage.Coverage;
import edu.hm.hafner.coverage.FileNode;
import edu.hm.hafner.coverage.Node;
import edu.hm.hafner.coverage.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import io.jenkins.plugins.coverage.metrics.model.ElementFormatter;

/**
 * Remote API to list the whole-file coverage of the files with coverage relevant changes. Only modified files are
 * reported to keep the response bounded: a project with tens of thousands of files would otherwise produce an
 * unmanageable payload when every file is returned. The whole-file coverage values are preserved, i.e. this returns the
 * absolute coverage of a modified file rather than only the coverage of its modified lines.
 */
@ExportedBean
public class FileCoverageApi {
    private static final ElementFormatter FORMATTER = new ElementFormatter();

    private final List<FileCoverage> files;

    FileCoverageApi(final Node node) {
        files = createListOfFilesWithCoverage(node);
    }

    @Exported(inline = true, name = "files")
    public List<FileCoverage> getFiles() {
        return files;
    }

    /**
     * Finds the modified files and their whole-file coverage values in the passed {@link Node} object. Only
     * {@link Coverage} values recorded directly on each {@link FileNode} (e.g. line, branch, instruction, mutation)
     * are reported; derived container metrics and non-coverage metrics (complexity, loc, tests) are skipped.
     *
     * @param node
     *         containing the file tree.
     *
     * @return a list of {@link FileCoverage} objects, one per file with coverage relevant changes.
     */
    private List<FileCoverage> createListOfFilesWithCoverage(final Node node) {
        var result = new ArrayList<FileCoverage>();

        for (FileNode fileNode : node.filterByModifiedFiles().getAllFileNodes()) {
            var metrics = new TreeMap<String, String>();
            for (Value value : fileNode.getValues()) {
                if (value instanceof Coverage coverage && coverage.isSet()) {
                    metrics.put(coverage.getMetric().toTagName(), FORMATTER.format(coverage, Locale.ENGLISH));
                }
            }
            result.add(new FileCoverage(fileNode.getRelativePath(), metrics));
        }

        return result;
    }
}
