# Migration Guide: Metric Aggregation Support

## Overview

This guide explains how to migrate from the deprecated `COMPLEXITY_MAXIMUM` metric to the new aggregation-based approach introduced to fix [JENKINS-75323](https://issues.jenkins.io/browse/JENKINS-75323).

## Background

Previously, the plugin supported specific metric variants like `COMPLEXITY_MAXIMUM` that were removed from the underlying coverage-model library (version 0.66.0). This caused pipeline jobs to fail with:

```
IllegalArgumentException: No enum constant edu.hm.hafner.coverage.Metric.COMPLEXITY_MAXIMUM
```

## New Approach: Metric Aggregation

Instead of having separate metrics for different aggregation modes, the plugin now supports an `aggregation` parameter on quality gates. This allows you to specify how metrics should be computed across the codebase.

### Available Aggregation Modes

- **TOTAL** (default): Sums up all values (e.g., total lines of code, total complexity)
- **MAXIMUM**: Takes the maximum value from all methods/classes (e.g., highest complexity)
- **AVERAGE**: Computes the arithmetic mean of all values (e.g., average complexity per method)

## Migration Examples

### Pipeline Script (Declarative)

**Old Configuration** (deprecated):

```groovy
recordCoverage(
    qualityGates: [
        [metric: 'COMPLEXITY_MAXIMUM', threshold: 10.0]
    ]
)
```

**New Configuration**:

```groovy
recordCoverage(
    qualityGates: [
        [metric: 'CYCLOMATIC_COMPLEXITY', aggregation: 'MAXIMUM', threshold: 10.0]
    ]
)
```

### Pipeline Script (Scripted)

**Old Configuration** (deprecated):

```groovy
publishCoverage(
    qualityGates: [
        [metric: 'COMPLEXITY_MAXIMUM', threshold: 10.0]
    ]
)
```

**New Configuration**:

```groovy
publishCoverage(
    qualityGates: [
        [metric: 'CYCLOMATIC_COMPLEXITY', aggregation: 'MAXIMUM', threshold: 10.0]
    ]
)
```

### Job DSL

**Old Configuration** (deprecated):

```groovy
publishers {
    recordCoverage {
        qualityGates {
            qualityGate {
                metric('COMPLEXITY_MAXIMUM')
                threshold(10.0)
            }
        }
    }
}
```

**New Configuration**:

```groovy
publishers {
    recordCoverage {
        qualityGates {
            qualityGate {
                metric('CYCLOMATIC_COMPLEXITY')
                aggregation('MAXIMUM')
                threshold(10.0)
            }
        }
    }
}
```

## Metrics Supporting Aggregation

The aggregation parameter can be used with the following software metrics:

- **CYCLOMATIC_COMPLEXITY**: Cyclomatic complexity (McCabe)
- **CYCLOMATIC_COMPLEXITY_DENSITY**: Complexity per line of code
- **COGNITIVE_COMPLEXITY**: Cognitive complexity
- **NCSS**: Non-Commenting Source Statements
- **NPATH_COMPLEXITY**: NPath complexity
- **LOC**: Lines of Code

Note: Coverage metrics (LINE, BRANCH, MUTATION, etc.) always use TOTAL aggregation and do not support MAXIMUM or AVERAGE modes.

## Examples by Use Case

### 1. Enforce Maximum Cyclomatic Complexity

**Objective**: Ensure no method has cyclomatic complexity above 10

```groovy
recordCoverage(
    qualityGates: [
        [metric: 'CYCLOMATIC_COMPLEXITY', aggregation: 'MAXIMUM', threshold: 10.0]
    ]
)
```

### 2. Maintain Average Code Complexity

**Objective**: Keep average cyclomatic complexity below 5 across all methods

```groovy
recordCoverage(
    qualityGates: [
        [metric: 'CYCLOMATIC_COMPLEXITY', aggregation: 'AVERAGE', threshold: 5.0]
    ]
)
```

### 3. Limit Total Complexity

**Objective**: Keep total cyclomatic complexity of the project below 1000

```groovy
recordCoverage(
    qualityGates: [
        [metric: 'CYCLOMATIC_COMPLEXITY', aggregation: 'TOTAL', threshold: 1000.0]
    ]
)
```

### 4. Multiple Quality Gates

**Objective**: Combine different aggregation modes for comprehensive quality control

```groovy
recordCoverage(
    qualityGates: [
        [metric: 'CYCLOMATIC_COMPLEXITY', aggregation: 'MAXIMUM', threshold: 15.0],
        [metric: 'CYCLOMATIC_COMPLEXITY', aggregation: 'AVERAGE', threshold: 5.0],
        [metric: 'LINE', threshold: 80.0]  // Coverage metrics always use TOTAL
    ]
)
```

## UI Configuration

When configuring quality gates through the Jenkins UI:

1. Select the **Metric** (e.g., "Cyclomatic Complexity")
2. Select the **Aggregation** mode:
   - **Total**: Sum of all values (default)
   - **Maximum**: Highest value found
   - **Average**: Mean of all values
3. Set the **Threshold** value
4. Choose the **Baseline** (PROJECT, MODIFIED_LINES, etc.)
5. Set the **Criticality** (FAILURE, UNSTABLE, NOTE)

## Backward Compatibility

### Automatic Migration

Existing build configurations that reference `COMPLEXITY_MAXIMUM` will be automatically migrated when deserialized:

- `COMPLEXITY_MAXIMUM` → `CYCLOMATIC_COMPLEXITY` with `aggregation = TOTAL`

**Note**: This automatic migration maintains backward compatibility but does **not** preserve the original MAXIMUM aggregation semantics. You must manually update your configuration to use `aggregation: 'MAXIMUM'` to restore the original behavior.

### Why Manual Update is Required

The automatic migration uses TOTAL (sum) instead of MAXIMUM because:

1. TOTAL is the safe default that works for all metrics
2. The plugin cannot determine the original intent from the metric name alone
3. Users should explicitly review and update their quality gates

### Update Checklist

For each quality gate using `COMPLEXITY_MAXIMUM`:

- [ ] Change `metric` from `COMPLEXITY_MAXIMUM` to `CYCLOMATIC_COMPLEXITY`
- [ ] Add `aggregation: 'MAXIMUM'` parameter
- [ ] Review and adjust threshold if needed
- [ ] Test the pipeline to verify the quality gate works as expected

## Troubleshooting

### Quality Gate Not Evaluating Correctly

If your quality gate is not evaluating as expected:

1. **Check the aggregation mode**: Ensure you've specified the correct aggregation (TOTAL, MAXIMUM, or AVERAGE)
2. **Verify metric support**: Only software metrics support MAXIMUM/AVERAGE aggregation
3. **Review threshold values**: MAXIMUM thresholds are typically much lower than TOTAL thresholds
4. **Check build logs**: The plugin logs the aggregated value used for evaluation

### Build Still Failing with "No enum constant"

If you're still getting the `IllegalArgumentException`:

1. Ensure you've updated **all** quality gates in your configuration
2. Check for multiple `recordCoverage` or `publishCoverage` steps in your pipeline
3. Verify Job DSL configurations have been regenerated
4. Clear the Jenkins workspace and rebuild

## Additional Resources

- [GitHub Issue #639](https://github.com/jenkinsci/coverage-plugin/issues/639)
- [JENKINS-75323](https://issues.jenkins.io/browse/JENKINS-75323)
- [Plugin Documentation](https://plugins.jenkins.io/coverage/)

## Questions or Issues?

If you encounter problems during migration or have questions about the new aggregation feature:

1. Check the [plugin documentation](https://plugins.jenkins.io/coverage/)
2. Search existing [GitHub issues](https://github.com/jenkinsci/coverage-plugin/issues)
3. Create a new issue with your pipeline configuration and error logs
