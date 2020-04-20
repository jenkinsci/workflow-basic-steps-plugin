## Changelog

### 2.20

Release date: 2020-04-20

-   Improvement: Add new `quiet` option to the `waitUntil` step. Use `quiet: true` to stop the `waitUntil` step from logging a message every time the condition is checked. `quiet` defaults to false. ([JENKINS-59776](ihttps://issues.jenkins-ci.org/browse/JENKINS-59776)).
-   Fix: Prevent the `fileExists` step from throwing an error if the `file` parameter is `null`. If the file parameter is `null`, the current directory will be checked, which is the same as the behavior for an empty string. ([PR 108](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/108))
-   Fix: Prevent the `timeout` step from hanging indefinitely after a Jenkins restart in rare cases where the step already attempted to cancel the body before the restart and needs to force-cancel it after the restart because the body was unresponsive. ([JENKINS-42940](https://issues.jenkins-ci.org/browse/JENKINS-42940))
-   Internal: Update minimum supported Jenkins core version to 2.164.3. ([PR 111](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/111))
-   Internal: Use `waitForMessage` instead of `assertLogContains` in tests to avoid nondeterministic failures. ([PR 110](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/110))
-   Internal: Update pom.xml to use jenkinsci/bom to specify dependency versions. ([PR 95](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/95))


### 2.19

Release date: 2020-01-03

-   [JENKINS-60354](https://issues.jenkins-ci.org/browse/JENKINS-60354): Make the retry step work correctly with the `build` step when using `propagate: true` (similarly for the `catchError` and `warnError` steps when using `catchInterruptions: false`). You must update Pipeline: Build Step Plugin to 2.11 or newer along with this update for the full fix. ([PR 102](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/102))
-   Improvement: Add an optional `initialRecurrencePeriod` parameter to the `waitUntil` step. ([PR 49](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/49))
-   Improvement: Update documentation for the `stash` step to clarify that stashes are only available in the current build, not from other jobs, or from other builds of the same job. ([PR 89](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/89))
-   [JENKINS-59575](https://issues.jenkins-ci.org/browse/JENKINS-59575): Update documentation for the `withContext` step to clarify that Groovy objects may not be passed to the step, and to indicate that users are encouraged to create a `Step` in a plugin instead of using the `withContext` or `getContext` steps. ([PR 93](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/93))
-   Internal: Update parent POM, refactor uses of deprecated APIs, improve tests, and migrate wiki content to GitHub. ([PR 91](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/91), [PR 92](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/92), [PR 93](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/93), [PR 94](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/94), [PR 96](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/96), [PR 103](https://github.com/jenkinsci/pipeline-build-step-plugin/pull/40))

### 2.18

Release date: 2019-06-04

> You must update Pipeline Groovy Plugin to version 2.70 along with this
update.

-   [JENKINS-41854](https://issues.jenkins-ci.org/browse/JENKINS-41854):
    Refresh references to files on agents when an agent reconnects.
    Fixes some cases where a `dir` step would fail with
    a `RequestAbortedException` due to a `ChannelClosedException` if the
    agent had disconnected earlier during the build even if the agent
    was connected to Jenkins at the time of failure.
-   [JENKINS-57537](https://issues.jenkins-ci.org/browse/JENKINS-57537):
    The `buildResult`  and `stageResult`  parameters added to
    the `catchError` step in version 2.16 of this plugin did not work
    correctly in a Declarative Pipeline.

### 2.17

Release date: 2019-06-03

-   Released incorrectly, do not use.

### 2.16

Release date: 2019-05-14

> This plugin now requires Jenkins 2.138.4 or newer.

-   [JENKINS-45579](https://issues.jenkins-ci.org/browse/JENKINS-45579)/[JENKINS-39203](https://issues.jenkins-ci.org/browse/JENKINS-39203):
    Use a new API provided by Pipeline API Plugin version 2.34 to create
    new steps to set the build result to `UNSTABLE` in a way that allows
    visualizations such as Blue Ocean to identify the stage and step
    that set the build result to `UNSTABLE`.
    -   **Note:** In order for stage-related results to be visualized
        differently in Blue Ocean, you must update to Pipeline: Graph
        Analysis 1.10 or newer.
    -   The **`catchError`** step has been updated with new optional
        parameters:
        -   **`message: String`** -  A message that will be printed to
            the build log if an exception is caught. The message will be
            associated with the stage result if the stage result is set
            and may be shown in visualizations such as Blue Ocean in the
            future.
        -   **`buildResult: String`** - What the overall build result
            should be set to if an exception is caught. Use `null` or
            `SUCCESS` to not set the build result. Defaults
            to `FAILURE`.
        -   **`stageResult: String`** - What the stage result should be
            set to if an exception is caught. Use `null` or `SUCCESS` to
            not set the stage result. Defaults to `SUCCESS`.
        -   **`catchInterruptions: boolean`** - If true, certain types
            of exceptions that are used to interrupt the flow of
            execution for Pipelines will be caught and handled by the
            step. If false, those types of exceptions will be caught and
            immediately rethrown. Examples of these types of exceptions
            include those thrown when a build is manually aborted
            through the UI and those thrown by the `timeout` step.
            Defaults to `true`.
    -   The following two steps have been added:  
        -   **`unstable(message: String)`**: Prints a message to the log
            and sets the overall build result and the stage result to
            `UNSTABLE`. The message will be associated with the stage
            result and may be shown in visualizations such as Blue Ocean
            in the future.
        -   **`warnError(message: String, catchInterruptions: boolean)`**: Executes
            its body, and if an exception is thrown, sets the overall
            build result and the stage result to `UNSTABLE`, prints the
            specified message and the exception to the build log, and
            associates the stage result with the message so that it can
            be displayed by visualizations.
            -   Equivalent
                to `catchError(message: message, buildResult: 'UNSTABLE', stageResult: 'UNSTABLE')`
            -   `catchInterruptions` has the same meaning as in
                the `catchError`  step.

### 2.15

Release date: 2019-03-18

-   Fix formatting issues in informational text for various steps ([PR
    82](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/82))
-   Internal: Update dependencies and fix resulting test failures so
    that the plugin's tests pass successfully when run using the PCT
    ([PR
    79](https://github.com/jenkinsci/workflow-basic-steps-plugin/pull/79))

### 2.14

Release date: 2019-01-14

-   [JENKINS-43276](https://issues.jenkins-ci.org/browse/JENKINS-43276):
    Fix potential issue where a `SimpleBuildWrapper` used in a Pipeline
    script through the general build wrapper step (`wrap`) could block
    Pipeline execution indefinitely.

### 2.13

Release date: 2018-11-30

-   [JENKINS-54607](https://issues.jenkins-ci.org/browse/JENKINS-54607):
    Fix issue where timeouts reused the timeout value instead of the
    grace period of 60 seconds when force killing unresponsive Pipelines
    after the timeout expires.
-   Improvement: Chinese localizations have been migrated to
    the [Localization: Chinese (Simplified)
    Plugin](https://github.com/jenkinsci/localization-zh-cn-plugin).

### 2.8.3

Release date: 2018-11-30

-   [JENKINS-54607](https://issues.jenkins-ci.org/browse/JENKINS-54607):
    Fix issue where timeouts reused the timeout value instead of the
    grace period of 60 seconds when force killing unresponsive Pipelines
    after the timeout expires.

### 2.12

Release date: 2018-10-26

-   [JENKINS-54078](https://issues.jenkins-ci.org/browse/JENKINS-54078):
    Fix issue causing builds to fail when using the `timeout` step
    with `activity: true`. 

### 2.11

Release date: 2018-09-10

>   **Requires Jenkins Core 2.121.1 or newer.**

-   Fix a file leak introduced in version 2.10 when
    using `writeFile` with Base64 encoding.

### 2.8.2

Release date: 2018-09-10

-   Fix a file leak introduced in version 2.8.1 when using `writeFile`
    with Base64 encoding.

### 2.10

Release date: 2018-08-21

>   **Requires Jenkins Core 2.121.1 or newer.**

-   Adds support to `readFile` and `writeFile` for working with binary
    files by passing `Base64` as the encoding. Can be used with
    Pipeline: Shared Groovy Libraries 2.10 or higher to copy binary
    files from libraries into the workspace.
    ([JENKINS-52313](https://issues.jenkins-ci.org/browse/JENKINS-52313))

### 2.8.1

Release date: 2018-08-21

>   **Requires Jenkins Core 2.60.3 or newer.**

-   Adds support to `readFile` and `writeFile` for working with binary
    files by passing `Base64` as the encoding. Can be used with
    Pipeline: Shared Groovy Libraries 2.10 or higher to copy binary
    files from libraries into the workspace.
    ([JENKINS-52313](https://issues.jenkins-ci.org/browse/JENKINS-52313))

### 2.9

Release date: 2018-06-16

>   **Requires Jenkins Core 2.121.1**

-   Support for storing stashes and artifacts off-master if using an
    appropriate storage implementation via VirtualFile.toExternalUrl
    ([JENKINS-49635](https://issues.jenkins-ci.org/browse/JENKINS-49635))

### 2.8

Release date: 2018-06-15

-   Docs: Fixes to the Stash step help (thanks, community contributor
    Josh Soref!)
-   Docs: Note exception type thrown by Timeout step (thanks, community
    contributor Dawid Gosławski!)

### 2.7

Release date: 2018-04-18

-   [JENKINS-46180](https://issues.jenkins-ci.org/browse/JENKINS-46180) -
    Deprecated `archive` step will log when no files to archive are
    found.
-   [JENKINS-48138](https://issues.jenkins-ci.org/browse/JENKINS-48138) -
    Log a warning when `fileExists` is called with an empty string.
-   [JENKINS-44379](https://issues.jenkins-ci.org/browse/JENKINS-44379)
    - `retry` will no longer retry on a `FlowInterruptedException`, such
    as aborted `input`, `milestone` steps or an aborted run.
-   [JENKINS-26521](https://issues.jenkins-ci.org/browse/JENKINS-26521) -
    Add `activity` flag to `timeout` step.

### 2.6

Release date: 2017-06-30

-   [JENKINS-45101](https://issues.jenkins-ci.org/browse/JENKINS-45101) Improved
    display of step summaries in Blue Ocean for various steps.

### 2.5

Release date: 2017-05-30

-   [JENKINS-27094](https://issues.jenkins-ci.org/browse/JENKINS-27094) Honor
    the `encoding` parameter of `writeFile`.
-   [JENKINS-37327](https://issues.jenkins-ci.org/browse/JENKINS-37327) Allow
    empty `stash`es.

### 2.4

Release date: 2017-02-10

-   [JENKINS-41276](https://issues.jenkins-ci.org/browse/JENKINS-41276)
    `retry` now exits immediately upon receiving a user-initiated build
    abort.
-   Implemented virtual thread dump status for `waitUntil`.
-   Simplified some implementations as per
    [JENKINS-39134](https://issues.jenkins-ci.org/browse/JENKINS-39134).

### 2.3

Release date: 2016-11-01

-   [JENKINS-39072](https://issues.jenkins-ci.org/browse/JENKINS-39072)
    (related to
    [JENKINS-34637](https://issues.jenkins-ci.org/browse/JENKINS-34637)):
    make the `timeout` step print more information to the log, display
    status in **Thread Dump**, and forcibly kill its body after a grace
    period has elapsed.
-   [JENKINS-28385](https://issues.jenkins-ci.org/browse/JENKINS-28385)
    Added `getContext` and `withContext` steps for use in advanced
    libraries.
-   Warning in documentation regarding
    [JENKINS-38640](https://issues.jenkins-ci.org/browse/JENKINS-38640)
    and
    [JENKINS-36914](https://issues.jenkins-ci.org/browse/JENKINS-36914).

### 2.2

Release date: 2016-09-23

-   [JENKINS-37397](https://issues.jenkins-ci.org/browse/JENKINS-37397)
    Allow use of symbols to select the `type` of a `tool`.
-   Creating the workspace directory from some steps if the step
    required it and it did not already exist.
-   Broken documentation link.

### 2.1

Release date: 2016-07-28

-   [JENKINS-29922](https://issues.jenkins-ci.org/browse/JENKINS-29922)
    Marking `step` and `wrap` as “metasteps”, allowing simplified syntax
    with [Pipeline Groovy
    Plugin](https://plugins.jenkins.io/workflow-cps)
    2.10. Correspondingly deprecating the `artifact` step for Jenkins
    2.2+ users.
-   [JENKINS-34554](https://issues.jenkins-ci.org/browse/JENKINS-34554)
    Maximum recurrence period for `waitUntil`, currently hard-coded.
-   [JENKINS-34281](https://issues.jenkins-ci.org/browse/JENKINS-34281)
    Indicate in the build log if `sleep` still applies after a Jenkins
    restart.
-   [JENKINS-31842](https://issues.jenkins-ci.org/browse/JENKINS-31842)
    Indicate in the virtual thread dump if `sleep` is ongoing.
-   [JENKINS-29170](https://issues.jenkins-ci.org/browse/JENKINS-29170)
    Snippet generation improvements for `mail`.
-   Some inline help fixes.

### 2.0

Release date: 2016-04-05

-   First release under per-plugin versioning scheme. See [1.x
    changelog](https://github.com/jenkinsci/workflow-plugin/blob/82e7defa37c05c5f004f1ba01c93df61ea7868a5/CHANGES.md)
    for earlier releases.
-   Now includes `stash` and `unstash` steps formerly in [Pipeline
    Supporting APIs
    Plugin](https://plugins.jenkins.io/workflow-support).
