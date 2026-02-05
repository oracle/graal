# VM Suite: Building GraalVM from Source

The VM suite provides the tooling required to build GraalVM from source.
It defines a base GraalVM package that contains the GraalVM SDK, the Graal JIT compiler, [SubstrateVM](../substratevm/README.md), and the Truffle framework.

Here you will find instructions on how to build a custom GraalVM Community Edition (CE) distribution from source.

## Prepare the Environment

- Python version as defined in the Graal CI (see value of `MX_PYTHON` in [common.jsonnet](../ci/common.jsonnet), currently 3.8). Install the required version and set the `MX_PYTHON` environment variable.
- Developer packages for your operating system are required. The following versions are tested by the Oracle-internal Graal CI but newer versions might also work.
  - **Linux**: [`devtoolset` 12](https://docs.redhat.com/en/documentation/red_hat_developer_toolset/12/html-single/user_guide/index) which includes
    - `gcc` 12.2 (the minimum accepted version is 10.0; version 14.2 is also known to compile successfully)
    - `make` 4.3
    - `binutils` 2.36
  - **macOS**: Sonoma and XCode 15
  - **Windows**: Visual Studio 2022 17.13

## Build GraalVM from Source

GraalVM Community Edition (CE) is an open-source project based on OpenJDK.
The base package includes:
- SubstrateVM
- Graal JIT compiler
- Truffle framework

You can build a custom GraalVM distribution from source.
You can divide the process into three main parts.

### Setting `mx`

`mx` is a special tool for developing Graal projects, available on GitHub.
For more details, see [What is `mx`?](#what-is-mx) below.

1. Create a workspace directory, and enter it:
    ```bash
    mkdir workspace; cd workspace
    ```
2. Clone the [Graal repository](https://github.com/oracle/graal):
    ```bash
    git clone https://github.com/oracle/graal.git
    ```
3. Clone the [`mx` repository](https://github.com/graalvm/mx):
    ```bash
    git clone https://github.com/graalvm/mx
    ```
4. Check the `mx` version defined in the [Graal CI](../common.json). Currently it is on `"mx_version": "7.68.10"`. Then check the one you cloned:
    ```bash
    mx/mx --version
    ```
    It should correspond.
    If it does not, check out the correct version:
    ```bash
    cd mx && git checkout 7.68.10
    mx --version && cd ..
    ```
5. Add `mx` to your `PATH` environment variable:
    ```bash
    export PATH=/path/to/mx:$PATH
    ```

### Creating a JVMCI-Enabled JDK

To build a custom GraalVM, you need a **JVMCI-enabled JDK** based on [LabsJDK](https://github.com/graalvm/labs-openjdk).
You can either download the latest [prebuilt release](https://github.com/graalvm/labs-openjdk/releases), or use `mx fetch-jdk` to fetch a prebuilt base JDK suitable for GraalVM, or build yourself to ensure full control and compatibility.

1. Install OpenJDK on your system to be used as the boot JDK. Note that the boot JDK version must be not lower than LabsJDK version -1. It is only required for building LabsJDK. This guide illustrates how to build a GraalVM CE 25. For that version, installing [OpenJDK 25](https://jdk.java.net/25/) is sufficient.
    > Not required if you opt for downloading a LabsJDK prebuilt binary.

2. Clone the [LabsJDK repository](https://github.com/graalvm/labs-openjdk):
    ```bash
    git clone https://github.com/graalvm/labs-openjdk.git
    ```

3. Enter the directory and check out the required tag. The tag is derived from the definition of `jdks.labsjdk-ce-latest.version` in [common.json](../common.json) in the Graal repository.
    ```bash
    cd labs-openjdk
    git checkout `cat /path/to/graal/common.json | grep '"labsjdk-ce-latest"' | sed 's:.*\(jvmci-[^"]*\)".*:\1:g'`
    ```
    For example, at the time of writing this guide, the tag is `jvmci-25.1-b14`:
    ```
    git status
    HEAD detached at jvmci-25.1-b14
    ```

4. Build a JVMCI-enabled JDK. First configure the build and then build the image with `make`:
    ```bash
    bash configure
    ```
    ```bash
    make graal-builder-image
    ```
    As a result, you get the `graal-builder-jdk` image in the  _build/your-platform/images/_ directory.
    The contents of `graal-builder-jdk` follow the standard `$JAVA_HOME` layout. Note, however, that on macOS it does not include the `/Contents/Home` directory structure required for JDKs on that platform.

5. Point the `JAVA_HOME` environment variable to the newly built image:
    ```bash
    export JAVA_HOME="/path/to/labs-openjdk/build/your-platform/images/graal-builder-jdk"
    ```
    (For more details, see [LabsJDK build instructions](https://github.com/graalvm/labs-openjdk/blob/master/doc/building.md#tldr-instructions-for-the-impatient)).

    Now you are all set to build your custom GraalVM distribution.

### Building GraalVM

1. Enter the _graal/vm_ directory:
    ```bash
    cd ../graal/vm
    ```
2. Build the base GraalVM:
    ```bash
    mx --env ce build
    ```
    Once it completes, the build will be available at:
    ```bash
    mx --env ce graalvm-home
    ```
    The uncompressed archive of the build will be available at:
    ```bash
    mx --env ce paths $(mx --env ce graalvm-dist-name)
    ```
    Since additional processing may occur during archiving, you should use the uncompressed archive.

    > Note: By default, build artifacts are placed inside the source repository.
    To keep the repository clean, you can set the `MX_ALT_OUTPUT_ROOT` environment variable to redirect the output to an alternative location. For example:
    ```bash
    export MX_ALT_OUTPUT_ROOT=/path/to/alternative/build-directory
    ```

## Frequently Asked Questions

### What is `mx`?

`mx` is a CLI tool for building and testing Graal projects.
It is developed and maintained by the Graal team, publicly available on [Github](https://github.com/graalvm/mx).
`mx` works with suites. A **suite** is an `mx` construct that defines:
  - what are the dependencies of a project
  - where are the source files
  - how they are built
  - what are the results of the build process
  - how to execute tests and programs

Some Graal projects contain only one suite, while others contain multiple suites.

### What are "suite" dependencies, and how do they work?

In the GraalVM ecosystem, **suite dependencies** refer to related components that are required for a project to build successfully.
For example, if you are working in the [GraalJS directory](https://github.com/oracle/graaljs/tree/master/graal-js), running the command:
```bash
mx suites
```
will output something like this:
```
graal-js
regex
truffle
sdk
```
This output shows that `graal-js` depends on the `regex`, `truffle`, and `sdk` suites, which are part of the main Graal repository.
Each project repository contains a _suite.py_ file that defines its build dependencies.

#### What is a primary suite?

A **primary suite** is the one in the current directory, or the one passed as an `mx` argument.
Only the commands defined by the primary suite (and all its dependencies) are available.
For example, running:
```bash
mx -p ../graal/compiler
```
from the [graal-js](https://github.com/oracle/graaljs/tree/master/graal-js) directory uses the compiler suite as the primary suite.

### Can I create IDE configurations for GraalVM projects using `mx`?

Yes, you can use `mx` to generate IDE configurations for various platforms.
The following commands are available:

* For IntelliJ: `mx intellijinit`
* For Eclipse: `mx eclipseinit`
* For NetBeans: `mx netbeansinit`

These commands automatically configure the IDE with the project's dependencies (referred to as "suite" dependencies in `mx` terminology).
Note that for the IDE to correctly resolve the dependencies, the Graal repository must be checked out in a sibling directory to your working repository.

### Can more components be added to the build?

More components can be added to the build by dynamically importing additional suites.
This can be achieved either by:
1. Running `mx build` with the `--dynamicimports <suite...>` option.
2. Setting the `DEFAULT_DYNAMIC_IMPORTS` or `DYNAMIC_IMPORTS` environment variables before running `mx build`.
3. Providing the file with environment variables: `mx --env <env file> build`.

Note that the build dependencies of each component are specified in _suite.py_ of the corresponding repository.
Before building a GraalVM image, you can validate imports with:
```bash
mx --env ce sforceimports
```

Find examples and learn more in the [Dynamic Imports documentation](https://github.com/graalvm/mx/blob/master/docs/dynamic-imports.md).

### Can I point `JAVA_HOME` to the `graal-builder-jdk` at build time instead of hardcoding it in the system configuration?

Yes, `mx` supports the `--java-home` option.
Pass `--java-home=<path to build/<platform>/images/graal-builder-jdk/>` when invoking `mx`.

### Can I see the expected result before building?

Before starting the build, we recommend to verify what will end up in that custom distribution of yours.
```bash
mx --env ce graalvm-show
```
This command lists the components, launchers, and libraries that will be built.

### Related Documentation

- [Static, Dynamic, and Versioned Imports in mx](https://github.com/graalvm/mx/blob/master/docs/dynamic-imports.md)
- [Building the LabsJDK](https://github.com/graalvm/labs-openjdk/blob/master/doc/building.md)