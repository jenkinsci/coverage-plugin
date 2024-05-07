# Jenkins Coverage Plugin

[![Join the chat at Gitter/Matrix](https://badges.gitter.im/jenkinsci/code-coverage-api-plugin.svg)](https://gitter.im/jenkinsci/code-coverage-api-plugin?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/coverage.svg?color=red)](https://plugins.jenkins.io/coverage)
[![Jenkins](https://ci.jenkins.io/job/Plugins/job/coverage-plugin/job/main/badge/icon?subject=Jenkins%20CI)](https://ci.jenkins.io/job/Plugins/job/coverage-plugin/job/main/)
[![GitHub Actions](https://github.com/jenkinsci/coverage-plugin/workflows/GitHub%20CI/badge.svg)](https://github.com/jenkinsci/coverage-plugin/actions)
[![Codecov](https://codecov.io/gh/jenkinsci/coverage-plugin/branch/main/graph/badge.svg)](https://codecov.io/gh/jenkinsci/coverage-plugin/branch/main)
[![CodeQL](https://github.com/jenkinsci/coverage-plugin/workflows/CodeQL/badge.svg)](https://github.com/jenkinsci/coverage-plugin/actions/workflows/codeql.yml)

The Jenkins Coverage Plug-in collects reports of code coverage or mutation coverage tools. It has support for the following report formats:

- [JaCoCo](https://www.jacoco.org/jacoco): Code Coverage
- [Cobertura](https://cobertura.github.io/cobertura/): Code Coverage
- [OpenCover](https://github.com/OpenCover/opencover): Code Coverage
- [VectorCAST](https://www.vector.com/int/en/products/products-a-z/software/vectorcast): Code Coverage including MC/DC, Function, Function Call coverages
- [PIT](https://pitest.org/): Mutation Coverage
- [JUnit](https://ant.apache.org/manual/Tasks/junitreport.html): Test Results
- [NUnit](https://nunit.org/): Test Results
- [XUnit](https://xunit.net/): Test Results

If your coverage tool is not yet supported by the coverage plugin, feel free to provide a pull request for the [Coverage Model](https://github.com/jenkinsci/coverage-model/pulls).

The plugin publishes a report of the code and mutation coverage in your build, so you can navigate to a summary report from the main build page. Additionally, the plugin gathers several metrics (lines of code, cyclomatic complexity, number of tests per class) and visualizes these results along with the coverage information. 
From there you can also dive into the details:
- tree charts that show the distribution of the metrics by type (line, branch, complexity, tests, etc.)
- tabular listing of all files with their coverage, complexity and number of tests 
- source code of the files with the coverage highlighted
- trend charts of the coverage over time

The initial version of this plugin has been developed by Shenyu Zheng in [GSoC 2018](https://jenkins.io/projects/gsoc/2018/code-coverage-api-plugin/). After several incompatible improvements of the pipeline steps and API classes, we decided to move the whole source code to a clean and new plugin. The old plugin containing the deprecated steps and code is still available at [GitHub](https://github.com/jenkinsci/code-coverage-api-plugin) and [Jenkins](https://plugins.jenkins.io/code-coverage-api/).

This code of this plugin is also available as a standalone GitHub or GitLab action that runs without Jenkins now:
- [Quality Monitor GitHub Action](https://github.com/uhafner/quality-monitor): action that monitors the quality of projects based on a configurable set of metrics and gives feedback on pull requests (or single commits) in GitHub.
- [GitHub Autograding action](https://github.com/uhafner/autograding-github-action): action that automatically grades student software projects based on a configurable set of metrics and gives feedback on pull requests (or single commits) in GitHub.
- [GitLab Autograding action](https://github.com/uhafner/autograding-gitlab-action): action that automatically grades student software projects based on a configurable set of metrics and gives feedback on merge requests (or single commits) in GitLab.

![Quality Monitor GitHub Action](images/quality-monitor.png)


## Features

The code coverage plug-in provides the following features when added as a post-build action (or step) to a job:

* Coverage analysis of projects and pull requests: The plugin now computes and shows the absolute coverage of the project, the coverage of the modified files and the coverage of the modified lines, so you can see how the changes actually affect the code coverage. Additionally, the delta values of these coverages with respect to the reference build are computed and the coverage changes created by changed test cases (indirect coverage changes).

 ![Coverage overview and trend](./images/summary.png)

* Coverage overview and trend:

  ![Coverage overview and trend](./images/reportOverview_screen.PNG)
  
* Colored project coverage tree map for line, branch, and mutation coverages and for cyclomatic complexity and number of tests

  ![Colored project coverage tree map](./images/reportTree_screen.PNG)
  
* Source code navigation with a configuration option to store the source code files for all builds, for current build only, or for changed files only:

  ![Source code navigation](./images/reportFile_screen.PNG)
  
* Specific source code view for analyzing the coverage of changed code lines:

  ![Specific source code view for Change Coverage](./images/reportCC_screen.PNG)
   
* Specific source code view for analyzing the coverage changes that are a result of test changes (indirect coverage changes):

  ![Specific source code view for Indirect Coverage Changes](./images/reportICC_screen.PNG)

* Customizable coverage overview for the Jenkins dashboard view and for build results:

  ![alt text](./images/dashboard_screen.PNG "Analysis overview for Jenkins dashboard")

* Quality Gates: You can specify an arbitrary number of quality gates that allow to set the build to unstable or failed if the thresholds are not met. For each quality gate the metric (branch coverage, complexity, etc.) and the baseline (whole project, changed files, etc.) can be defined.

  ![Quality Gates](./images/quality-gates.png)

* Quality gate result: The detailed result of the quality gate evaluation is available as tooltip in the build summary:

  ![Quality Gate Result](images/quality-gates-result.png)

* Cyclomatic Complexity and LOC metrics: Several coverage parsers support the measurement of cyclomatic complexity and lines of code. These metrics are now computed and recorded as well:

  ![Cyclomatic Complexity and LOC metrics](./images/all-metrics.png)

* The recorder has been extended with a native step that is capable of setting the step status (unstable, failed, ok):

  ![Native step](./images/step.png)

* GitHub checks report to show the detailed line and branch coverage results for pull request:

  ![Code Coverage Checks Overview](./images/jacoco-coverage-checks.png)
  ![Code Coverage Checks Annotations](./images/jacoco-coverage-checks-annotations.png)

* GitHub checks report to show the detailed line and mutation coverage results for pull request:

  ![Mutation Coverage Checks Overview](./images/pit-coverage-checks.png)
  ![Mutation Coverage Checks Annotations](./images/pit-coverage-checks-annotations.png)

* Token macro support: The recorder has been extended with a token macro that allows to use the coverage results in other plugins (e.g. email-ext) or pipeline scripts.

## Usage

:exclamation: The plugin does not run the code coverage, it just visualizes the results reported by such tools. You still need to enable and configure the code coverage tool in your build file or Jenkinsfile. :exclamation:

### Supported parsers

The Coverage Plug-in supports the following report formats:

- [JaCoCo](https://www.jacoco.org/jacoco): Code Coverage
- [Cobertura](https://cobertura.github.io/cobertura/): Code Coverage
- [OpenCover](https://github.com/OpenCover/opencover): Code Coverage
- [VectorCAST](https://www.vector.com/int/en/products/products-a-z/software/vectorcast) Code Coverage including MC/DC, Function, Function Call coverages
- [PIT](https://pitest.org/): Mutation Coverage
- [JUnit](https://ant.apache.org/manual/Tasks/junitreport.html): Test Results
- [NUnit](https://nunit.org/): Test Results
- [XUnit](https://xunit.net/): Test Results

Some of these report files are generated by other tools and may contain invalid or inconsistent information. By default, the plugin tries to fail fast if such a broken file is detected. You can disable this behavior by setting the property `ignoreParsingErrors` to `true`. In this case, the plugin will try to parse as much information as possible from the report file.

### Supported project types

The Coverage Plug-in supports the following Jenkins project types:

- Freestyle Project
- Maven Project
- Scripted Pipeline (sequential and parallel steps)
- Declarative Pipeline (sequential and parallel steps)
- Multi-branch Pipeline

#### Freestyle project 

Enable the "Record code coverage results" publisher in the Post-build Actions section of your job. Select at least one coverage tool and specify the path to the report file. If you do not specify a path, the plugin will search for the report file in the workspace using the default pattern of the tool. 

#### Pipeline example

The recording step also supports pipeline configuration (scripted or declarative). The simplest way to generate the corresponding pipeline code is by using Jenkins' Snippet Generator: there you can configure all available properties of the step by using a rich user interface with inline help and validation. A sample step definition is given in the following code snippet. This step records coverage results of JaCoCo with the default file name pattern. The coverage information is rendered with the source code for every build, and quality gates have been set up for line and branch coverages. I.e., if the line or branch coverage does not reach the threshold of 60%, then the build will be marked as unstable.

```groovy
recordCoverage(tools: [[parser: 'JACOCO']],
        id: 'jacoco', name: 'JaCoCo Coverage',
        sourceCodeRetention: 'EVERY_BUILD',
        qualityGates: [
                [threshold: 60.0, metric: 'LINE', baseline: 'PROJECT', unstable: true],
                [threshold: 60.0, metric: 'BRANCH', baseline: 'PROJECT', unstable: true]])
```

## Reference build (baseline)

One unique feature of the Coverage plugin is the delta computation of coverage metrics with respect to a baseline build (reference build). The plugin reads the results of the reference build and compares these results with the current results: for each coverage metric, a delta value is computed. The delta values are shown as absolute coverages of the project, the coverages of the modified files, or the coverages of the modified lines. This helps to see how the code changes actually affect the code coverage.

In order to compute this classification, the plugin requires a reference build (baseline). When selecting a baseline, we need to distinguish two different use cases, which are documented in the next sections.

### Selecting a baseline from the current job

When a team wants to investigate how the coverage of the project changes over the time, we need to simply look back in the history of the same Jenkins job and select another build that we can use to compare the results with. Such a Jenkins job typically builds the main branch of the source control system. This can be achieved by using `discoverReferenceBuild` before the step to record the code coverage:

```groovy
discoverReferenceBuild()
recordCoverage(tools: [[parser: 'JACOCO']])
```

### Selecting a baseline in the target job

:warning: This feature requires the installation of an additional plugin:
[Git Forensics Plugin](https://github.com/jenkinsci/git-forensics-plugin).

For more complex branch source projects (i.e., projects that build several branches and pull requests in a connected job hierarchy) it makes more sense to select a reference build from a job that builds the actual target branch (i.e., the branch the current changes will be merged into). Here one typically is interested what changed in the branch or pull request over the main branch (or any other target branch). That means we want to see how the coverage changes when new code will be submitted by a branch or pull request.

If you are using a Git branch source project, the Jenkins job that builds the target branch will be selected automatically by running the reference recorder step. Simply call the step `discoverGitReferenceBuild` before the step to record the code coverage:

```groovy
discoverGitReferenceBuild()
recordCoverage(tools: [[parser: 'JACOCO']])
```

Selecting the correct reference build is not that easy as it looks, since the main branch of a project will evolve more frequently than a specific feature or bugfix branch. That means if we want to compare the results of a pull request with the results of the main branch we need to select a build from the main branch that contains only commits that are also part of the branch of the associated pull request.

Therefore, the Git Forensics plugin automatically tracks all commits of a Jenkins build and uses this information to identify a build in the target branch that matches best with the commits in the current branch. Please have a look at the [documentation of the Git Forensics plugin](https://github.com/jenkinsci/git-forensics-plugin) to see how this is achieved in detail.

This algorithm can be used for plain Git SCM freestyle projects or pipelines as well. In this case, we cannot get the target branch information automatically from the Git branch source API. Therefore, you need to manually specify the Jenkins job that builds the target branch in the parameter `referenceJob`. See the following sample pipeline snippet for an example on how to discover a baseline from such a reference job:

```groovy
discoverGitReferenceBuild referenceJob: 'my-reference-job'
recordCoverage(tools: [[parser: 'JACOCO']])
```

## Rendering of the source code

The plugin will automatically find your source code files to create a report that shows the source code in combination with the achieved code coverage results. You can change the strategy that should be used to store the colored source code files with the property `sourceCodeRetention`. If your server has not enough free space available to store the sources for all builds it might make sense to store only the coverage results of the last build. In this case, the plugin will automatically discard old results before the new sources will be stored. Or, if you do not need the source files at all, you can deactivate the storing of source code files. The following options are supported:

- `NEVER`: Never store source code files.
- `LAST_BUILD` (default): Store source code files of the last build, delete older artifacts.
- `EVERY_BUILD`: Store source code files for all builds, never delete those files automatically.
- `MODIFIED`: Store only changed source code files for all builds, never delete those files automatically.

For Java projects, the source code rendering typically works out-of-the-box since the coverage tools export the results into a report that contains the exact locations of the source code files (absolute path). If this automatic detection does not work in your case, then you can specify a path prefix to the sources by using the option `sourceDirectories`. This property can be filled with one or more relative paths within the workspace that should be searched for the source code. You can also specify absolute paths, but then you need to make sure that those paths are approved by an administrator in the configuration section of the Prism plugin in Jenkins´ global configuration. The following example shows how to specify such a path prefix: 

```groovy
recordCoverage(tools: [[parser: 'JACOCO']], 
            sourceCodeRetention: 'MODIFIED', 
            sourceDirectories: [[path: 'plugin/src/main/java']])
```

## Token macro support

The coverage plugin provides the token `COVERAGE` that could be used in additional post build processing steps, e.g. in the mailer. In order to use this token you need to install the [Token Macro plugin](https://plugins.jenkins.io/token-macro).
The token has the following optional parameters:
- `id`: selects a particular coverage result, if not defined the defauult  result that are published under the URL "coverage" are shown.
- `metric`: selects the coverage metric to evaluate, see [Metric help](https://github.com/jenkinsci/coverage-plugin/blob/main/plugin/src/main/resources/io/jenkins/plugins/coverage/metrics/steps/CoverageMetricColumn/help-metric.html) for all possible values. 
- `baseline`: selects the baseline to use, see [Baseline help](https://github.com/jenkinsci/coverage-plugin/blob/main/plugin/src/main/resources/io/jenkins/plugins/coverage/metrics/steps/CoverageMetricColumn/help-baseline.html) for all possible values. .
 
Examples:

- `${COVERAGE}`: shows the line coverage of the whole project
- `${COVERAGE, metric="BRANCH"}`: shows the branch coverage of the whole project
- `${COVERAGE, metric="MUTATION", baseline="MODIFIED_LINES"}`: shows the mutation coverage of the modified lines 

## Remote API

We provide a remote API to retrieve a coverage overview, using the following URL: `https://[jenkins-url]/job/[job-name]/[build-number]/coverage/api/json?pretty=true`. 

Example output:
```json
{
  "_class" : "io.jenkins.plugins.coverage.metrics.restapi.CoverageApi",
  "modifiedFilesDelta" : {
    "branch" : "+1.72%",
    "class" : "-3.54%",
    "complexity" : "-236",
    "complexity-density" : "+0.47%",
    "file" : "+0.00%",
    "instruction" : "+0.16%",
    "line" : "-0.48%",
    "loc" : "-482",
    "method" : "+1.23%",
    "module" : "+0.00%",
    "package" : "+0.00%"
  },
  "modifiedFilesStatistics" : {
    "branch" : "83.91%",
    "class" : "93.33%",
    "complexity" : "392",
    "complexity-density" : "+50.19%",
    "file" : "100.00%",
    "instruction" : "88.19%",
    "line" : "87.96%",
    "loc" : "781",
    "method" : "86.18%",
    "module" : "100.00%",
    "package" : "100.00%"
  },
  "modifiedLinesDelta" : {
    "branch" : "+8.95%",
    "file" : "+0.00%",
    "line" : "+3.85%",
    "loc" : "-610",
    "module" : "+0.00%",
    "package" : "+0.00%"
  },
  "modifiedLinesStatistics" : {
    "branch" : "92.86%",
    "file" : "100.00%",
    "line" : "91.81%",
    "loc" : "171",
    "module" : "100.00%",
    "package" : "100.00%"
  },
  "projectDelta" : {
    "branch" : "+4.43%",
    "class" : "+2.94%",
    "complexity" : "-8",
    "complexity-density" : "+1.28%",
    "file" : "+4.00%",
    "instruction" : "+2.59%",
    "line" : "+3.37%",
    "loc" : "-50",
    "method" : "+1.28%",
    "module" : "+0.00%",
    "package" : "+0.00%"
  },
  "projectStatistics" : {
    "branch" : "82.19%",
    "class" : "96.88%",
    "complexity" : "628",
    "complexity-density" : "+49.72%",
    "file" : "100.00%",
    "instruction" : "88.03%",
    "line" : "88.44%",
    "loc" : "1263",
    "method" : "84.94%",
    "module" : "100.00%",
    "package" : "100.00%"
  },
  "qualityGates" : {
    "overallResult" : "SUCCESS",
    "resultItems" : [
      {
        "qualityGate" : "Overall project - Line Coverage",
        "result" : "SUCCESS",
        "threshold" : 60.0,
        "value" : "88.44%"
      },
      {
        "qualityGate" : "Overall project - Branch Coverage",
        "result" : "SUCCESS",
        "threshold" : 60.0,
        "value" : "82.19%"
      }
    ]
  },
  "referenceBuild" : "<a href=\"http://localhost:8080/job/coverage-model-history/10/\" class=\"model-link inside\">coverage-model-history #10</a>"
}
```

More concrete, the line coverage for modified code lines is provided per modified file, using the following URL: `https://[jenkins-url]/job/[job-name]/[build-number]/coverage/modified/api/json?pretty=true`.

Example output:
```json
{
  "_class": "io.jenkins.plugins.coverage.metrics.restapi.ModifiedLinesCoverageApi",
  "files": [
    {
      "fullyQualifiedFileName": "io/jenkins/plugins/coverage/metrics/restapi/ModifiedLinesCoverageApi.java",
      "modifiedLinesBlocks": [
        {
          "startLine": 30,
          "endLine": 35,
          "type": "MISSED"
        }
      ]
    },
    {
      "fullyQualifiedFileName": "io/jenkins/plugins/coverage/metrics/restapi/ModifiedLinesBlocks.java",
      "modifiedLinesBlocks": [
        {
          "startLine": 80,
          "endLine": 81,
          "type": "COVERED"
        }
      ]
    }
  ]
}
```
