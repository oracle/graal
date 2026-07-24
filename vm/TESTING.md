# Testing GraalVM: Understanding CI and Running Tests Locally

GraalVM is tested across a defined set of platforms and configurations using Oracle internal infrastructure and GitHub Actions.
**GitHub Actions test the Community Edition (CE) parts of GraalVM** on the following platforms: `linux-amd64`, `linux-aarch64`, `darwin-aarch64` (macOS).

Tests are executed using `mx`, the build and project management tool [available on GitHub](https://github.com/graalvm/mx).

This document explains where those tests are defined, how contributors can trigger CI workflows on GitHub, and reproduce testing locally.
Test failures help catch issues early in the development cycle.

## CI Workflows

GraalVM CE is tested by running various GitHub Actions workflows.
All workflows are defined in [.github/workflows](https://github.com/oracle/graal/tree/master/.github/workflows).
You can view and trigger them from the **Actions** tab in the repository.

Several workflows exist, but two are particularly relevant for contributors.

### Gate Workflow

**GraalVM Gate** is the primary workflow defined in [**main.yml**](https://github.com/oracle/graal/blob/master/.github/workflows/main.yml).
This workflow runs on pushes and pull requests and includes the steps that build GraalVM and execute tests.

At a high level, the workflow performs the following steps:
- Checking out the repository
- Setting up the required JDK
- Building the project
- Running the full test suite
- Uploading logs

The `mx` commands executed by the workflow determine what is actually built and tested.
In most cases, the workflow runs a command equivalent to:
```bash
mx gate --strict-mode --tags <gate-tags>
```
with a specific primary suite selected, for example:
```bash
mx --primary-suite-path compiler gate --strict-mode --tags style,fullbuild,test
```

This means CI is running the gate pipeline for a particular GraalVM component (such as the compiler), which typically includes building the component, running unit tests, and performing additional validation checks.

The parameters passed to `mx` are derived from environment variables defined in the [workflow matrix](https://github.com/oracle/graal/blob/master/.github/workflows/main.yml#L99). For example:

```yml
# /compiler
- env:
    JDK_VERSION: "latest"
    TOOLS_JDK_VERSION: "21"
    GATE_TAGS: "style,fullbuild,test"
    PRIMARY: "compiler"
```
These variables configure `mx gate` to run using the compiler suite as the primary suite, on the latest JDK, and with specific gate tags.

The exact configuration of the workflow (platforms, JDK versions, and matrix parameters) may change over time.

### Build Workflow

The **Build GraalVM** workflow is defined in [**build-graalvm.yml**](https://github.com/oracle/graal/blob/master/.github/workflows/build-graalvm.yml) and builds GraalVM CE from source.
It can be triggered on demand: under the **Actions** tab, select the **Build GraalVM** pipeline, expand the **Run workflow** menu, and confirm the action.

This workflow provides three fields for passing custom build arguments to `mx` (comma-separated, no spaces):

- Value for `mx --dy` option

    With `--dy` you can dynamically import more static dependencies (suites) to the base GraalVM CE package.
    Typical examples are `/substratevm`, `/compiler`, `/truffle`.
    Dynamic imports are declared in _ci/common.jsonnet_.
    Build dependencies of each component are specified in _mx.\<component\>/suite.py_ of the corresponding repository.

- Value for `mx --native-images` option

    This option specifies which native images to build as part of the run. Search the repository for where native images are defined.
    Typical examples are `native-image`, `native-image-configure`, `lib:native-image-agent`, `lib:jvmcicompiler`.

- Value for `mx --components` option

    This option defines which GraalVM components are included in a build.
    You can list all components with `mx graalvm-components`.
    You can also inspect component definitions in the _sdk/mx.sdk/suite.py_ and _vm/mx.vm/suite.py_ files.
    Typical examples are `Native Image`, `Native Image Configure Tool`, `LibGraal`.

If you do not provide any other values, this workflow builds the base distribution.
When the build succeeds, the workflow attaches the binary as an artifact, named _graalvm.zip_.

## Troubleshooting a Test Failure

If a test fails, the most reliable way to understand or reproduce it is:

1. Open the workflow run in GitHub Actions.
2. Locate the step that runs the gate command.
3. Check the logged `mx` command to see the exact parameters used.

When you open a particular pipeline run, you can trace any step in the logs back to the exact definition in the workflow file.

## Local Test Environment

You can run the same core test commands locally that are used in GraalVM CI.
Start by preparing your local environment.

1. Ensure your development environment meets the prerequisites: [prepare the environment](https://github.com/oracle/graal/blob/master/vm/README.md#prepare-the-environment).
2. Install `mx` as described [here](README.md#setting-mx).
3. Clone your fork of the Graal repository and enter the component directory you want to test (for example, `compiler`, `substratevm`, or `truffle`).
4. Make sure you are using a JDK version supported by CI (for example, `jdk21`, `jdk25`, or `latest`).
5. Run tests using `mx`. For example:
    ```bash
    mx unittest
    ```

This command runs the JUnit-based tests for the current suite (component).

If you want to test a full GraalVM build similar to CI artifacts, you can also run the [Build GraalVM workflow](#build-workflow) in GitHub Actions and download the produced build artifact.

## Reproducing a CI Test Locally

When a test fails in CI, the most reliable way to reproduce it is to run the same `mx gate` command locally.

1. Open the failed workflow run in GitHub Actions.
2. Locate the step that runs the gate command.
3. Copy the exact `mx` command from the logs.

A typical example looks like this:
```bash
mx --primary-suite-path compiler gate --strict-mode --tags style,fullbuild,test
```
Running this command locally executes the same gate tasks that CI runs for that suite.

## Local Testing Configuration

When running locally, you can limit testing to specific components using tags or suite settings.
The following environment variables values translate directly into `mx` command arguments:

- `GATE_TAGS` defines which gate tasks will run. Common tags include: `style`, `build`, `fullbuild`, and `test`. More tags may exist depending on the component (suite). Tags are defined in the component `mx` suite file: _mx.\<component\>/suite.py_.
- `PRIMARY` defines which suite is used as the primary suite for the gate run. Common suites include: `compiler`, `substratevm`, `truffle`, and `vm`.

You can inspect all tasks and their associated tags by running:
```bash
mx -v gate --dry-run
```

If you are reproducing a failure, check the workflow logs to see which platform, JDK version, environment variables, and gate tags were used in that run.
Matching those settings locally usually reproduces CI behavior more closely.

## Related Documentation

- Learn more about suites in the [`mx` project documentation](https://github.com/graalvm/mx/blob/master/README.md).
- See also how to [build GraalVM Community Edition from source](vm/README.md) manually.

We hope this document will help users test changes in a setup that mirrors GraalVM CI, thus improving the quality of external contributions.