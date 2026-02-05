# VM Suite: Building GraalVM from Source

The VM suite provides the tooling required to build GraalVM from source.
It defines a base GraalVM package that contains the GraalVM SDK, the Graal JIT compiler, SubstrateVM (without the `native-image` tool), and the Truffle framework.

Here you will find instructions on how to build a custom GraalVM Community Edition (CE) distribution from source.

## Prepare the Environment

- Python version as defined in the [Graal CI](https://github.com/oracle/graal/blob/master/ci/common.jsonnet#L147) (currently Python 3.8). Install the required version and set the `MX_PYTHON` environment variable.
- Developer packages for your operating system are required. The following versions are tested by the Oracle-internal Graal CI but newer versions might also work.
  - **Linux**: [`devtoolset` 12](https://docs.redhat.com/en/documentation/red_hat_developer_toolset/12/html-single/user_guide/index) which includes
    - `gcc` 12.2 (the minimum accepted version is 10.0; version 14.2 is also known to compile successfully)
    - `make` 4.3
    - `binutils` 2.36
  - **macOS**: Sonoma and XCode 15
  - **Windows**: Visual Studio 2022 17.13
- OpenJDK or Oracle JDK on your system to be used as the "boot" image. Say you want to build a GraalVM version 25, you need to set `JAVA_HOME` to OpenJDK or Oracle JDK 25 or 24. You can install it with SDKMAN!.

## Build GraalVM from Source

GraalVM Community Edition (CE) is an open-source project based on OpenJDK.
The base package includes:
- SubstrateVM (without the `native-image` tool)
- Graal JIT compiler
- Truffle framework

You can build a custom GraalVM distribution from source.
You can divide the process into three main parts.

### Setting `mx`

`mx` is a special tool for developing Graal projects. It is [available on GitHub](https://github.com/graalvm/mx).
For more details, see [What is `mx`?](#what-is-mx) below.

1. Create a workspace directory, and enter it:
    ```bash
    mkdir workspace; cd workspace
    ```
2. Clone the `mx` repository:
    ```bash
    git clone https://github.com/graalvm/mx
    ```
3. Add `mx` to your `PATH` environment variable:
    ```bash
    export PATH=/path/to/mx:$PATH
    ```

### Creating a JVMCI-Enabled JDK

To be able to build a GraalVM CE distribution, you need to have a **JVMCI-enabled JDK**, built on top of [LabsJDK](https://github.com/graalvm/labs-openjdk).
You can download the latest [pre-built release](https://github.com/graalvm/labs-openjdk/releases), but we recommend building it yourself.

1. Clone the [LabsJDK repository](https://github.com/graalvm/labs-openjdk):
    ```bash
    git clone https://github.com/graalvm/labs-openjdk.git
    ```

2. Enter the directory and check out the required tag. The tag is derived from the definition of `jdks.labsjdk-ce-latest.version` in [common.json](https://github.com/oracle/graal/blob/master/common.json).
    ```bash
    cd labs-openjdk
    git checkout `cat /path/to/graal/common.json | grep '"labsjdk-ce-latest"' | sed 's:.*\(jvmci-[^"]*\)".*:\1:g'`
    ```
    For example, at the time of writing this guide, the tag is `jvmci-25.1-b14`:
    ```
    git status
    HEAD detached at jvmci-25.1-b14
    ```
3. Build a JVMCI-enabled JDK. First configure the build and then build the image with `make`:
    ```bash
    bash configure --disable-dtrace
    ```
    ```bash
    make graal-builder-image
    ```
    As a result, you get the `graal-builder-jdk` image in the  _build/your-platform/images/_ directory.
    The contents of `graal-builder-jdk` will have the layout of `$JAVA_HOME`.

4. Point the `JAVA_HOME` environment variable to the newly built image:
    ```bash
    export JAVA_HOME="$HOME/workspace/labs-openjdk/build/your-platform/images/graal-builder-jdk"
    ```
    (For more details, see [LabsJDK build instructions](https://github.com/graalvm/labs-openjdk/blob/master/doc/building.md#tldr-instructions-for-the-impatient)).

    Now you are all set to build your custom GraalVM distribution.

### Building GraalVM

1. Return back to the _workspace_ directory and clone the [Graal repository](https://github.com/oracle/graal):
    ```bash
    git clone https://github.com/oracle/graal.git
    ```
 2. Enter the _graal/vm_ directory:
    ```bash
    cd graal/vm
    ```
3. Build the base GraalVM:
    ```bash
    mx --env ce --sources=sdk:GRAAL_SDK,truffle:TRUFFLE_API,compiler:GRAAL,substratevm:SVM --debuginfo-dists --base-jdk-info=labsjdk:25 build --targets=GRAALVM
    ```
    The build will be available at:
    ```bash
    mx --env ce graalvm-home
    ```
    The uncompressed archive of the build will be available at:
    ```bash
    mx --env ce paths $(mx --env ce graalvm-dist-name)
    ```
    Since additional processing may occur during archiving, you should use the uncompressed archive.

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

_How do you check how many suites a Graal project repository contains?_
Search for files defining a suite named _suite.py_ in the root directory, using `mx` or `find`:
```bash
mx suites
```
```bash
find . -type d -maxdepth 2 -name "mx\.*"
```
You should see several suites.

_What is a primary suite?_
A **primary suite** is the one in the current directory, or the one passed as an `mx` argument.
Only the commands defined by the primary suite (and all its dependencies) are available.
For example, running:
```bash
mx -p ../graal/compiler
```
from the [graal-js](https://github.com/oracle/graaljs/tree/master/graal-js) directory uses the compiler suite as the primary suite.

### Can I point `JAVA_HOME` to the `graal-builder-jdk` at build time instead of hardcoding it in the system configuration?

Yes, `mx` supports the `--java-home` option.
Pass `--java-home=<path to build/<platform>/images/graal-builder-jdk/>` when invoking `mx`.

### Can more components be added to the build?

More components can be added to the build by dynamically importing additional suites.
This can be achieved either by:
1. Running `mx build` with the `--dynamicimports <suite...>` option.
2. Setting the `DEFAULT_DYNAMIC_IMPORTS` or `DYNAMIC_IMPORTS` environment variables before running `mx build`.
3. Providing the file with environment variables: `mx --env <env file> build`.

Note that the build dependencies of each component are specified in the README file of the corresponding repository.
Before building a GraalVM image, you can validate imports with:
```bash
mx --env ce sforceimports
mx --env ce build
```

Find examples and learn more in the [Dynamic Imports documentation](https://github.com/graalvm/mx/blob/master/docs/dynamic-imports.md).

### Can I see the expected result before building?

Before starting the build, we recommend to verify what will end up in that custom distribution of yours.
```bash
mx --env ce graalvm-show
```
This command lists the components, launchers, and libraries that will be built.

### Minimal configuration script example

Below is an example of a minimal configuration script for Linux:

```bash
if [ -f /etc/bashrc ]; then
    . /etc/bashrc
fi
export PATH=/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/bin

# Graal builder JDK (JVMCI-enabled)
export JAVA_HOME="$HOME/workspace/labs-openjdk/build/linux-x86_64-server-release/images/graal-builder-jdk"
export PATH="$JAVA_HOME/bin:$PATH"

# mx configuration
export PATH="$HOME/workspace/mx:$PATH"
export MX_PYTHON=python3.8

# gcc toolset 12
source /opt/rh/gcc-toolset-12/enable
```

### Related Documentation

- [Static, Dynamic, and Versioned Imports in mx](https://github.com/graalvm/mx/blob/master/docs/dynamic-imports.md)
- [Building the JDK](https://github.com/graalvm/labs-openjdk/blob/master/doc/building.md)